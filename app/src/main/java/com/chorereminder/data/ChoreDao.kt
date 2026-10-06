package com.chorereminder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChoreDao {

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

    @Query("SELECT categoryId, COUNT(*) as count FROM tasks GROUP BY categoryId")
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
    ) {
        deleteAllCompletions()
        deleteAllTasks()
        deleteAllCategories()
        insertCategories(categories)
        insertTasks(tasks)
        insertCompletions(completions)
    }
}
