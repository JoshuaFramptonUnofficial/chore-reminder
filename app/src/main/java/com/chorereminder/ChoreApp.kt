package com.chorereminder

import android.app.Application
import android.content.Context
import com.chorereminder.data.BackupManager
import com.chorereminder.data.ChoreDatabase
import com.chorereminder.data.ChoreRepository
import com.chorereminder.data.TaskEntity
import com.chorereminder.notify.Notifications
import com.chorereminder.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChoreApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        // Catch up any task whose reminder was lost (fresh install, data import,
        // WorkManager pruned). Cheap and idempotent -- scheduling uses REPLACE.
        container.applicationScope.launch { container.rescheduleAll() }
    }
}

/**
 * Hand-rolled dependency container. The app is small enough that a DI framework
 * would be more ceremony than the wiring it replaces.
 *
 * Every mutation that can change a due date goes through here, so the DB and the
 * scheduled reminders can never drift apart.
 */
class AppContainer(private val context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database by lazy { ChoreDatabase.get(context) }

    val repository: ChoreRepository by lazy { ChoreRepository(database.choreDao()) }

    val backupManager: BackupManager by lazy { BackupManager(context, repository) }

    /** Creates a task and arms its first reminder. */
    suspend fun createTask(task: TaskEntity): Long {
        val id = repository.insertTask(task)
        repository.getTask(id)?.let { ReminderScheduler.schedule(context, it) }
        return id
    }

    /** FR-2: edits recompute the due date, so the reminder must be re-armed. */
    suspend fun updateTask(task: TaskEntity) {
        repository.updateTask(task)
        repository.getTask(task.id)?.let { ReminderScheduler.schedule(context, it) }
    }

    /** FR-3: deleting a task cancels its pending reminder. */
    suspend fun deleteTask(taskId: Long) {
        ReminderScheduler.cancel(context, taskId)
        repository.deleteTask(taskId)
    }

    /**
     * FR-11/FR-14: identical effect whether triggered from the list, the
     * confirmation screen, or the notification action.
     */
    suspend fun completeTask(taskId: Long) {
        repository.completeTask(taskId) ?: return
        Notifications.cancel(context, taskId)
        repository.getTask(taskId)?.let { ReminderScheduler.schedule(context, it) }
    }

    /** Re-arms reminders for every task -- used at startup and after an import. */
    suspend fun rescheduleAll() {
        repository.getAllTasks().forEach { ReminderScheduler.schedule(context, it) }
    }
}
