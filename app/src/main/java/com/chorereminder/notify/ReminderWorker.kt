package com.chorereminder.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chorereminder.ChoreApp
import com.chorereminder.domain.DueState
import java.time.ZonedDateTime

/**
 * Posts one task's reminder, then re-arms itself.
 *
 * Re-arming from inside the worker is what implements FR-9: an overdue task that is
 * never confirmed gets a fresh notification every day, and the loop only stops when
 * the task is completed (which reschedules to the new due date) or deleted.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()

        val container = (applicationContext as ChoreApp).container
        val view = container.repository.getTask(taskId)
            // Task was deleted while the work was pending -- stop the loop.
            ?: return Result.success()

        if (view.dueState.isActionable) {
            Notifications.post(applicationContext, view)
        }

        // Re-arm: tomorrow for a still-outstanding task, or the real next due date
        // for one that isn't due yet.
        ReminderScheduler.schedule(applicationContext, view, ZonedDateTime.now())
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
    }
}

/** Convenience for readability at call sites. */
internal val DueState.isDueOrOverdue: Boolean get() = isActionable
