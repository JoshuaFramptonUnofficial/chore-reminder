package com.chorereminder.data

import com.chorereminder.domain.DueState
import com.chorereminder.domain.dueStateFor
import com.chorereminder.domain.nextDueDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
)

/** A category header plus its tasks, sorted soonest-due first (FR-13). */
data class CategoryGroup(
    val categoryId: Long,
    val categoryName: String,
    val tasks: List<TaskView>,
)

class ChoreRepository(private val dao: ChoreDao) {

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
    fun observeGroupedTasks(today: () -> LocalDate = LocalDate::now): Flow<List<CategoryGroup>> =
        dao.observeTasksWithDetails().map { rows ->
            val now = today()
            rows.map { it.toView(now) }
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
        }

    fun observeTask(taskId: Long, today: () -> LocalDate = LocalDate::now): Flow<TaskView?> =
        dao.observeTaskWithDetails(taskId).map { it?.toView(today()) }

    suspend fun getTask(taskId: Long, today: LocalDate = LocalDate.now()): TaskView? =
        dao.getTaskWithDetails(taskId)?.toView(today)

    suspend fun getAllTasks(today: LocalDate = LocalDate.now()): List<TaskView> =
        dao.getAllTasksWithDetails().map { it.toView(today) }

    // --- Mutations -------------------------------------------------------------

    suspend fun insertTask(task: TaskEntity): Long = dao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task)

    suspend fun deleteTask(taskId: Long) = dao.deleteTask(taskId)

    /**
     * FR-11: append a timestamped completion. Returns the task's recomputed next-due
     * date so the caller can reschedule, or null if the task vanished underneath us
     * (deleted concurrently, or just now -- a one-off task has no next occurrence,
     * so completing it finishes the task for good).
     */
    suspend fun completeTask(taskId: Long, at: Instant = Instant.now()): LocalDate? {
        val details = dao.getTaskWithDetails(taskId) ?: return null
        val completedOn = at.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        dao.insertCompletion(
            CompletionEntity(taskId = taskId, completedAt = at, completedOn = completedOn),
        )
        val task = details.task
        if (task.recurrence.kind == RecurrenceKind.ONE_OFF) {
            dao.deleteTask(taskId)
            return null
        }
        return nextDueDate(
            recurrence = task.recurrence.toDomain(task.createdDate),
            createdDate = task.createdDate,
            lastCompletion = completedOn,
        )
    }

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

    suspend fun snapshot(): Triple<List<CategoryEntity>, List<TaskEntity>, List<CompletionEntity>> =
        Triple(
            dao.getAllCategories(),
            dao.getAllTasksWithDetails().map { it.task },
            dao.getAllCompletions(),
        )

    suspend fun replaceAll(
        categories: List<CategoryEntity>,
        tasks: List<TaskEntity>,
        completions: List<CompletionEntity>,
    ) = dao.replaceAll(categories, tasks, completions)
}

/** Shared projection so the list, detail and scheduler all agree on the due date. */
fun TaskWithDetails.toView(today: LocalDate): TaskView {
    val lastCompletedOn = completions.maxByOrNull { it.completedAt }?.completedOn
    val nextDue = nextDueDate(
        recurrence = task.recurrence.toDomain(task.createdDate),
        createdDate = task.createdDate,
        lastCompletion = lastCompletedOn,
    )
    return TaskView(
        task = task,
        categoryName = category?.name ?: UNCATEGORIZED_NAME,
        nextDue = nextDue,
        dueState = dueStateFor(nextDue, today),
        lastCompletedOn = lastCompletedOn,
        completionCount = completions.size,
    )
}
