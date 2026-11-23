package com.chorereminder.data

import com.chorereminder.domain.DueState
import com.chorereminder.domain.dueStateFor
import com.chorereminder.domain.nextDueDate
import com.chorereminder.domain.scheduleKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

/** A category plus how many chores currently live in it. */
data class CategoryWithCount(
    val category: CategoryEntity,
    val choreCount: Int,
)

/**
 * A task plus its derived (never stored) scheduling state. [DueState] is computed
 * from `today` at read time, so it can't go stale while the app sits backgrounded.
 */
data class TaskView(
    val task: TaskEntity,
    val categoryName: String,
    val nextDue: LocalDate,
    val dueState: DueState,
    val lastCompletedOn: LocalDate?,
    val completionCount: Int,
    val occurrenceRevision: Long = 0L,
)

/** A category header plus its tasks, sorted soonest-due first (FR-13). */
data class CategoryGroup(
    val categoryId: Long,
    val categoryName: String,
    val tasks: List<TaskView>,
)

data class HomeData(val groups: List<CategoryGroup> = emptyList(), val revisions: Map<Long, Long> = emptyMap())

class ChoreRepository(private val dao: ChoreDao) {

    fun observeVisibility(): Flow<VisibilitySpec> = dao.observeVisibility().map { it?.rule ?: VisibilitySpec() }
    suspend fun setVisibility(rule: VisibilitySpec) { rule.validate(); dao.setVisibility(VisibilitySettingsEntity(rule = rule)) }
    suspend fun undoSpecific(id: Long, revision: Long) = dao.undoSpecific(id, revision)

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()

    /** Settings screen model: every category paired with its live chore count. */
    fun observeCategoriesWithCounts(): Flow<List<CategoryWithCount>> =
        combine(dao.observeCategories(), dao.observeCategoryTaskCounts()) { categories, counts ->
            val countsById = counts.associate { it.categoryId to it.count }
            categories.map { CategoryWithCount(it, countsById[it.id] ?: 0) }
        }

    /**
     * Home-screen model: tasks grouped by category, each group sorted soonest-due
     * first, groups ordered most-urgent-first so whatever needs attention is on top.
     */
    fun observeGroupedTasks(today: Flow<LocalDate> = flowOf(LocalDate.now()), includeHidden: Flow<Boolean> = flowOf(false)): Flow<List<CategoryGroup>> =
        observeHomeData(today, includeHidden).map { it.groups }

    fun observeHomeData(today: Flow<LocalDate> = flowOf(LocalDate.now()), includeHidden: Flow<Boolean> = flowOf(false)): Flow<HomeData> =
        combine(dao.observeTasksWithDetails(), today, observeVisibility(), includeHidden) { rows, now, default, all ->
            val groups = rows.filter { it.isActive }.map { it.toView(now) }
                .filter { all || (it.task.visibility ?: default).isVisible(now, it.nextDue, it.lastCompletedOn) }
                .groupBy { it.task.categoryId }
                .map { (categoryId, tasks) ->
                    CategoryGroup(
                        categoryId = categoryId,
                        categoryName = tasks.first().categoryName,
                        tasks = tasks.sortedWith(
                            compareBy({ it.nextDue }, { it.task.name.lowercase() }),
                        ),
                    )
                }
                .sortedWith(
                    compareBy(
                        { group -> group.tasks.minOfOrNull { it.nextDue } ?: LocalDate.MAX },
                        { it.categoryName.lowercase() },
                    ),
                )
            HomeData(groups, rows.associate { it.task.id to it.task.completionRevision })
        }

    fun observeTask(taskId: Long, today: Flow<LocalDate> = flowOf(LocalDate.now())): Flow<TaskView?> =
        combine(dao.observeTaskWithDetails(taskId), today) { row, date ->
            row?.takeIf { it.isActive }?.toView(date)
        }

    fun observeLatestCompletion(): Flow<CompletionEntity?> = dao.observeLatestCompletion()
    suspend fun undo(completionId: Long): CompletionEntity? = dao.undo(completionId)

    suspend fun getTask(taskId: Long, today: LocalDate = LocalDate.now()): TaskView? =
        dao.getTaskWithDetails(taskId)?.takeIf { it.isActive }?.toView(today)

    suspend fun getAllTasks(today: LocalDate = LocalDate.now()): List<TaskView> =
        dao.getAllTasksWithDetails().filter { it.isActive }.map { it.toView(today) }

    // --- Mutations -------------------------------------------------------------

    suspend fun insertTask(task: TaskEntity): Long = dao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task)

    suspend fun deleteTask(taskId: Long) = dao.deleteTask(taskId)

    suspend fun completeTask(taskId: Long, expectedRevision: Long, at: Instant = Instant.now()): CompletionEntity? =
        dao.complete(taskId, expectedRevision, at)

    suspend fun createCategory(name: String): Long {
        val trimmed = name.trim()
        dao.findCategoryByName(trimmed)?.let { return it.id }
        val id = dao.insertCategory(CategoryEntity(name = trimmed))
        // IGNORE conflict strategy returns -1 if the row already existed.
        return if (id == -1L) dao.findCategoryByName(trimmed)?.id ?: UNCATEGORIZED_ID else id
    }

    /** Returns false (no-op) when the trimmed name is blank or already used by another category. */
    suspend fun renameCategory(categoryId: Long, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        if (dao.findCategoryByName(trimmed, excludingId = categoryId) != null) return false
        val category = dao.getAllCategories().firstOrNull { it.id == categoryId } ?: return false
        dao.updateCategory(category.copy(name = trimmed))
        return true
    }

    suspend fun deleteCategory(categoryId: Long) = dao.deleteCategory(categoryId)

    // --- Backup ----------------------------------------------------------------

    suspend fun snapshot(): BackupSnapshot = dao.snapshot()

    suspend fun replaceAll(
        categories: List<CategoryEntity>,
        tasks: List<TaskEntity>,
        completions: List<CompletionEntity>,
        invalidateOccurrences: Boolean = false,
        defaultVisibility: VisibilitySpec = VisibilitySpec(),
    ) = dao.replaceAll(categories, tasks, completions, invalidateOccurrences, defaultVisibility)
}

/** Shared projection so the list, detail and scheduler all agree on the due date. */
fun TaskWithDetails.toView(today: LocalDate): TaskView {
    val lastCompletedOn = lastCompletion?.completedOn
    val rule = task.recurrence.toDomain(task.createdDate)
    val nextDue = nextDueDate(
        recurrence = rule,
        createdDate = task.createdDate,
        lastCompletion = lastCompletedOn,
        completedOccurrence = lastCompletion?.takeIf { it.scheduleKey == rule.scheduleKey() }?.scheduledDue,
    )
    return TaskView(
        task = task,
        categoryName = category?.name ?: UNCATEGORIZED_NAME,
        nextDue = nextDue,
        dueState = dueStateFor(nextDue, today),
        lastCompletedOn = lastCompletedOn,
        completionCount = completions.size,
        occurrenceRevision = task.completionRevision,
    )
}

private val TaskWithDetails.isActive: Boolean
    get() = task.recurrence.kind != RecurrenceKind.ONE_OFF || completions.isEmpty()
