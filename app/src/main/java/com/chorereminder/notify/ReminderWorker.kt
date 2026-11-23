package com.chorereminder.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chorereminder.ChoreApp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
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
        return container.reminderMutex.withLock {
            val view = container.repository.getTask(taskId)
                // Task was deleted while the work was pending -- stop the loop.
                ?: return@withLock Result.success()

            val now = ZonedDateTime.now()
            val lastNotifiedOn = container.reminderHistory.lastNotifiedOn(taskId)
            currentCoroutineContext().ensureActive()
            if (ReminderTiming.shouldNotify(view.nextDue, now, lastNotifiedOn) &&
                Notifications.post(applicationContext, view, container.reminderHistory.generation())
            ) {
                container.reminderHistory.markNotified(taskId, now.toLocalDate())
            }

            // Persist the successor before reporting success, without cancelling
            // this running worker. Denied notifications still wait for the next slot.
            ReminderScheduler.scheduleNext(applicationContext, view, ZonedDateTime.now())
            Result.success()
        }
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
    }
}
