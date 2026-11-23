package com.chorereminder.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.chorereminder.data.TaskView
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules day-granularity reminders with WorkManager.
 *
 * WorkManager (not AlarmManager) is deliberate: without the forbidden exact-alarm
 * permissions an inexact alarm is deferred under Doze identically, so AlarmManager
 * would buy nothing -- and WorkManager survives reboot on its own, so no
 * BootReceiver is needed.
 */
object ReminderScheduler {

    private fun workName(taskId: Long) = "reminder-task-$taskId"

    /**
     * (Re)schedules [view]'s next reminder. Replaces any existing work for the task,
     * so editing a task or completing it can never leave a stale reminder armed.
     */
    suspend fun schedule(
        context: Context,
        view: TaskView,
        now: ZonedDateTime = ZonedDateTime.now(),
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) = enqueue(context, view, now, policy, catchUp = true)

    /** Append rather than cancel the worker which is currently delivering the reminder. */
    suspend fun scheduleNext(context: Context, view: TaskView, now: ZonedDateTime) =
        enqueue(context, view, now, ExistingWorkPolicy.APPEND_OR_REPLACE, catchUp = false)

    private suspend fun enqueue(
        context: Context,
        view: TaskView,
        now: ZonedDateTime,
        policy: ExistingWorkPolicy,
        catchUp: Boolean,
    ) {
        val lastNotifiedOn = ReminderHistory(context).lastNotifiedOn(view.task.id)
        val delay = ReminderTiming.delayUntilNextReminder(view.nextDue, now, lastNotifiedOn, catchUp, view.delayedUntil)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TASK_ID to view.task.id))
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(view.task.id), policy, request)
            .await()
    }

    suspend fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId)).await()
        Notifications.cancel(context, taskId)
    }

    const val TAG = "chore-reminder"
}
