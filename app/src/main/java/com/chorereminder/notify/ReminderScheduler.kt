package com.chorereminder.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chorereminder.data.TaskView
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
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

    /** Reminders fire mid-morning rather than at midnight. */
    private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    private fun workName(taskId: Long) = "reminder-task-$taskId"

    /**
     * (Re)schedules [view]'s next reminder. Replaces any existing work for the task,
     * so editing a task or completing it can never leave a stale reminder armed.
     */
    fun schedule(context: Context, view: TaskView, now: ZonedDateTime = ZonedDateTime.now()) {
        val delay = delayUntilNextReminder(view.nextDue, now)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TASK_ID to view.task.id))
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(view.task.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
        Notifications.cancel(context, taskId)
    }

    /**
     * Delay until the task should next be notified about.
     *
     * An already-due or overdue task is notified at the next [REMINDER_TIME] --
     * today's if it hasn't passed, otherwise tomorrow's. Combined with the worker
     * re-enqueueing itself, that is the once-per-day re-notify loop of FR-9.
     */
    internal fun delayUntilNextReminder(
        nextDue: LocalDate,
        now: ZonedDateTime,
    ): Duration {
        val today = now.toLocalDate()
        val targetDate = if (nextDue.isAfter(today)) nextDue else today
        var target = targetDate.atTime(REMINDER_TIME).atZone(now.zone)
        if (!target.isAfter(now)) {
            // Today's slot has already passed -- wait for tomorrow's.
            target = target.plusDays(1)
        }
        return Duration.between(now, target).coerceAtLeast(Duration.ZERO)
    }

    const val TAG = "chore-reminder"
}
