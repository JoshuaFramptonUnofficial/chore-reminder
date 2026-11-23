package com.chorereminder.data

import com.chorereminder.domain.scheduleKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChoreDao {

    @Query("SELECT * FROM completions ORDER BY id DESC LIMIT 1")
    fun observeLatestCompletion(): Flow<CompletionEntity?>

    @Query("SELECT * FROM completions ORDER BY id DESC LIMIT 1")
    suspend fun latestCompletion(): CompletionEntity?

    /** Compare the displayed occurrence inside the same transaction as the write. */
    @Transaction
    suspend fun complete(
        taskId: Long,
        expectedRevision: Long,
        at: java.time.Instant,
        outcome: CompletionOutcome = CompletionOutcome.DONE,
    ): CompletionEntity? {
        val details = getTaskWithDetails(taskId) ?: return null
        if (details.task.completionRevision != expectedRevision) return null
        if (details.task.recurrence.kind == RecurrenceKind.ONE_OFF && details.completions.isNotEmpty()) return null
        val completion = CompletionEntity(
            taskId = taskId, completedAt = at,
            completedOn = at.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            scheduledDue = details.toView(at.atZone(java.time.ZoneId.systemDefault()).toLocalDate()).nextDue,
            scheduleKey = details.task.recurrence.toDomain(details.task.createdDate).scheduleKey(),
            outcome = outcome,
        )
        val inserted = completion.copy(id = insertCompletion(completion))
        // The revision bump alone invalidates any pending snooze: it no longer
        // matches snoozeRevision, so there's nothing to clear here explicitly.
        updateTask(details.task.copy(completionRevision = details.task.completionRevision + 1))
        return inserted
    }

    /** Sets a snooze, guarded by the same optimistic-concurrency check as [complete]. */
    @Transaction
    suspend fun snooze(taskId: Long, expectedRevision: Long, until: java.time.Instant): TaskEntity? {
        val details = getTaskWithDetails(taskId) ?: return null
        if (details.task.completionRevision != expectedRevision) return null
        val updated = details.task.copy(
            completionRevision = expectedRevision + 1,
            snoozedUntil = until,
            snoozeRevision = expectedRevision + 1,
        )
        updateTask(updated)
        return updated
    }

    /** Reverts a snooze for undo -- restores whatever snooze state preceded it. */
    @Transaction
    suspend fun restoreSnooze(
        taskId: Long,
        revision: Long,
        previousSnoozedUntil: java.time.Instant?,
        previousSnoozeRevision: Long?,
    ): TaskEntity? {
        val details = getTaskWithDetails(taskId) ?: return null
        if (details.task.completionRevision != revision) return null
        val updated = details.task.copy(
            completionRevision = revision + 1,
            snoozedUntil = previousSnoozedUntil,
            snoozeRevision = previousSnoozeRevision,
        )
        updateTask(updated)
        return updated
    }

    @Transaction
    suspend fun undo(completionId: Long): CompletionEntity? {
        val latest = latestCompletion() ?: return null
        if (latest.id != completionId) return null
        val task = getTaskWithDetails(latest.taskId)?.task ?: return null
        updateTask(task.copy(completionRevision = task.completionRevision + 1))
        deleteCompletion(latest.id)
        return latest
    }

    @Transaction
    suspend fun snapshot(): BackupSnapshot =
        BackupSnapshot(getAllCategories(), getAllTasksWithDetails().map { it.task }, getAllCompletions(), getVisibility()?.rule ?: VisibilitySpec())

    @Query("SELECT * FROM visibility_settings WHERE id = 1")
    fun observeVisibility(): Flow<VisibilitySettingsEntity?>
    @Query("SELECT * FROM visibility_settings WHERE id = 1")
    suspend fun getVisibility(): VisibilitySettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVisibility(settings: VisibilitySettingsEntity)

    @Query("SELECT * FROM completions WHERE id = :id")
    suspend fun getCompletion(id: Long): CompletionEntity?

    @Transaction
    suspend fun undoSpecific(id: Long, revision: Long): CompletionEntity? {
        val completion = getCompletion(id) ?: return null
        val details = getTaskWithDetails(completion.taskId) ?: return null
        if (details.completions.maxByOrNull { it.id }?.id != id || details.task.completionRevision != revision) return null
        updateTask(details.task.copy(completionRevision = revision + 1))
        deleteCompletion(id)
        return completion
    }

    // --- Reads -----------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM tasks")
    fun observeTasksWithDetails(): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun observeTaskWithDetails(taskId: Long): Flow<TaskWithDetails?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskWithDetails(taskId: Long): TaskWithDetails?

    @Transaction
    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksWithDetails(): List<TaskWithDetails>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM completions")
    suspend fun getAllCompletions(): List<CompletionEntity>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND id != :excludingId LIMIT 1")
    suspend fun findCategoryByName(name: String, excludingId: Long): CategoryEntity?

    @Query("SELECT categoryId, COUNT(*) as count FROM tasks WHERE NOT (rec_kind = 'ONE_OFF' AND EXISTS (SELECT 1 FROM completions WHERE taskId = tasks.id)) GROUP BY categoryId")
    fun observeCategoryTaskCounts(): Flow<List<CategoryTaskCount>>

    // --- Writes ----------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId AND id != $UNCATEGORIZED_ID")
    suspend fun deleteCategory(categoryId: Long)

    @Insert
    suspend fun insertCompletion(completion: CompletionEntity): Long

    @Query("DELETE FROM completions WHERE id = :completionId")
    suspend fun deleteCompletion(completionId: Long)

    // --- Bulk / restore --------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletions(completions: List<CompletionEntity>)

    @Query("DELETE FROM completions")
    suspend fun deleteAllCompletions()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    /**
     * Full replace, per FR-16 / the import row of the I/O matrix. Runs in a single
     * transaction: if any insert throws, Room rolls the whole thing back and the
     * user's existing data is untouched.
     */
    @Transaction
    suspend fun replaceAll(
        categories: List<CategoryEntity>,
        tasks: List<TaskEntity>,
        completions: List<CompletionEntity>,
        invalidateOccurrences: Boolean = false,
        defaultVisibility: VisibilitySpec = VisibilitySpec(),
    ) {
        val revisionBase = if (invalidateOccurrences)
            Math.addExact(getAllTasksWithDetails().maxOfOrNull { it.task.completionRevision } ?: 0L, 1L)
            else 0L
        deleteAllCompletions()
        deleteAllTasks()
        deleteAllCategories()
        insertCategories(categories)
        insertTasks(tasks.map { it.copy(completionRevision = Math.addExact(it.completionRevision, revisionBase)) })
        insertCompletions(completions)
        setVisibility(VisibilitySettingsEntity(rule = defaultVisibility))
    }
}
