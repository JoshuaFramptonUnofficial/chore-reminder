package com.chorereminder

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import androidx.work.await
import com.chorereminder.data.BackupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.Duration
import com.chorereminder.domain.DelayOption
import com.chorereminder.domain.UndoAction
import com.chorereminder.domain.resolve
import androidx.work.ExistingWorkPolicy
import com.chorereminder.data.BackupManager
import com.chorereminder.data.ChoreDatabase
import com.chorereminder.data.ChoreRepository
import com.chorereminder.data.TaskEntity
import com.chorereminder.notify.Notifications
import com.chorereminder.notify.ReminderHistory
import com.chorereminder.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChoreApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        // Catch up any task whose reminder was lost (fresh install, data import,
        // WorkManager pruned). KEEP is essential: this also runs when WorkManager
        // starts our process, and must not cancel/postpone the worker starting it.
        container.applicationScope.launch { container.rescheduleAll(ExistingWorkPolicy.KEEP) }
    }
}

/**
 * Hand-rolled dependency container. The app is small enough that a DI framework
 * would be more ceremony than the wiring it replaces.
 *
 * Every mutation that can change a due date goes through here, so the DB and the
 * scheduled reminders stay coordinated. Room and WorkManager are separate stores.
 */
class AppContainer(private val context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Serialize delivery with chore mutations so completion/deletion cannot leave
    // a worker posting or appending stale work after the notification was cleared.
    internal val reminderMutex = Mutex()
    data class TaskChange(val taskId: Long = -1, val revision: Long = -1, val sequence: Long = 0)
    private val _taskChange = MutableStateFlow(TaskChange())
    val taskChange = _taskChange.asStateFlow()
    private fun changed(taskId: Long, revision: Long) {
        _taskChange.value = TaskChange(taskId, revision, _taskChange.value.sequence + 1)
    }
    val uiPreferences = com.chorereminder.data.UiPreferences(context)
    val undoQueue = com.chorereminder.domain.UndoQueue()
    val reminderHistory = ReminderHistory(context)

    private val _today = MutableStateFlow(LocalDate.now())
    val today = _today.asStateFlow()
    private val dateChanges = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    fun refreshDate() { _today.value = LocalDate.now(); dateChanges.trySend(Unit) }

    init {
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context, intent: android.content.Intent) { refreshDate() }
            },
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_DATE_CHANGED)
                addAction(android.content.Intent.ACTION_TIME_CHANGED)
                addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        applicationScope.launch {
            while (true) {
                _today.value = LocalDate.now()
                val now = ZonedDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                kotlinx.coroutines.withTimeoutOrNull(Duration.between(now, midnight).toMillis().coerceAtLeast(1)) { dateChanges.receive() }
            }
        }
    }

    private val database by lazy { ChoreDatabase.get(context) }

    val repository: ChoreRepository by lazy { ChoreRepository(database.choreDao()) }

    val backupManager: BackupManager by lazy { BackupManager(context, repository) }

    fun setDefaultVisibility(rule: com.chorereminder.data.VisibilitySpec) {
        rule.validate()
        // Preserve selection order and finish saving even when Settings is closed.
        applicationScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            reminderMutex.withLock { repository.setVisibility(rule) }
        }
    }

    /** Creates a task and arms its first reminder. */
    suspend fun createTask(task: TaskEntity): Long = reminderMutex.withLock {
        val id = repository.insertTask(task)
        repository.getTask(id)?.let { ReminderScheduler.schedule(context, it) }
        id
    }

    /** FR-2: edits recompute the due date, so the reminder must be re-armed. */
    suspend fun updateTask(task: TaskEntity) = reminderMutex.withLock {
        val current = repository.getTask(task.id) ?: return@withLock
        undoQueue.removeTask(task.id, android.os.SystemClock.elapsedRealtime())
        repository.updateTask(task.copy(completionRevision = current.task.completionRevision + 1))
        repository.getTask(task.id)?.let { ReminderScheduler.schedule(context, it) }
    }

    /** FR-3: deleting a task cancels its pending reminder. */
    suspend fun deleteTask(taskId: Long) = reminderMutex.withLock {
        undoQueue.removeTask(taskId, android.os.SystemClock.elapsedRealtime())
        ReminderScheduler.cancel(context, taskId)
        repository.deleteTask(taskId)
        reminderHistory.forget(taskId)
    }

    /** Occurrence comparison and completion insert are atomic in Room. */
    suspend fun completeTask(taskId: Long, expectedRevision: Long, generation: String? = null) = reminderMutex.withLock {
        currentCoroutineContext().ensureActive()
        if (generation != null && generation != reminderHistory.generation()) return@withLock
        val completed = repository.completeTask(taskId, expectedRevision) ?: return@withLock
        changed(taskId, expectedRevision + 1)
        Notifications.cancel(context, taskId)
        val next = repository.getTask(taskId)
        if (next == null) ReminderScheduler.cancel(context, taskId)
        else ReminderScheduler.schedule(context, next)
        undoQueue.add(completed.id, expectedRevision + 1, android.os.SystemClock.elapsedRealtime(), taskId)
    }

    /**
     * Snoozes a task's reminder until [option] resolves to, and arms the undo pill
     * to put back whatever snooze (if any) was pending before this one.
     */
    suspend fun delayTask(taskId: Long, expectedRevision: Long, option: DelayOption) = reminderMutex.withLock {
        val before = repository.getTask(taskId) ?: return@withLock
        val previousLastNotifiedOn = reminderHistory.lastNotifiedOn(taskId)
        val until = option.resolve(Instant.now())
        repository.snoozeTask(taskId, expectedRevision, until) ?: return@withLock
        changed(taskId, expectedRevision + 1)
        // A delay re-arms today's slot, so today's delivery receipt must not block
        // it -- undo below puts it back, so undoing a delay can't cause a second
        // notification to slip out on a day one already went out.
        reminderHistory.forget(taskId)
        Notifications.cancel(context, taskId)
        repository.getTask(taskId)?.let { ReminderScheduler.schedule(context, it) }
        undoQueue.add(
            taskId, expectedRevision + 1, android.os.SystemClock.elapsedRealtime(), taskId,
            action = UndoAction.Delayed(before.task.snoozedUntil, before.task.snoozeRevision, previousLastNotifiedOn),
        )
    }

    /**
     * Reverses whatever the receipt with [id] represents. The receipt is re-taken
     * from the queue inside the mutex, not trusted from the caller's Compose state,
     * so a receipt that expired or was superseded between the tap and this call
     * can never be actioned twice.
     */
    suspend fun undo(id: Long) = reminderMutex.withLock {
        val receipt = undoQueue.take(id, android.os.SystemClock.elapsedRealtime()) ?: return@withLock
        val taskId = when (val action = receipt.action) {
            is UndoAction.Completed, is UndoAction.Skipped -> {
                val undone = repository.undoSpecific(id, receipt.revision) ?: return@withLock
                changed(undone.taskId, receipt.revision + 1)
                undone.taskId
            }
            is UndoAction.Delayed -> {
                val restored = repository.restoreSnooze(
                    receipt.taskId, receipt.revision, action.previousSnoozedUntil, action.previousSnoozeRevision,
                ) ?: return@withLock
                changed(restored.id, receipt.revision + 1)
                // Put back whatever delivery receipt the delay cleared, so undoing
                // it can't re-open today's already-used notification slot.
                if (action.previousLastNotifiedOn != null) {
                    reminderHistory.markNotified(restored.id, action.previousLastNotifiedOn)
                }
                restored.id
            }
        }
        Notifications.cancel(context, taskId)
        // Keep today's delivery receipt: undo restores the chore without re-alerting.
        repository.getTask(taskId)?.let { ReminderScheduler.schedule(context, it) }
    }

    /** Parse/replace first; failed imports must preserve both data and reminders. */
    suspend fun importBackup(uri: Uri): BackupResult = reminderMutex.withLock {
        val result = backupManager.import(uri)
        if (result is BackupResult.Success) {
            // WorkManager is used only for this app's reminder/completion jobs.
            WorkManager.getInstance(context).cancelAllWork().await()
            undoQueue.clear()
            Notifications.cancelAll(context)
            reminderHistory.clear()
            repository.getAllTasks().forEach { ReminderScheduler.schedule(context, it) }
        }
        result
    }

    /** Re-arms reminders for every task -- used at startup and after an import. */
    suspend fun rescheduleAll(
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) = reminderMutex.withLock {
        repository.getAllTasks().forEach { ReminderScheduler.schedule(context, it, policy = policy) }
    }
}
