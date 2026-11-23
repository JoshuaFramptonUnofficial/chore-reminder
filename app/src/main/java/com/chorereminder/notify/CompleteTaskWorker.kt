package com.chorereminder.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chorereminder.ChoreApp

/**
 * Writes the completion triggered by the notification action. Split out of the
 * receiver so the DB work happens off the main thread and outside the ~10 s
 * `onReceive` budget.
 */
class CompleteTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()

        val container = (applicationContext as ChoreApp).container
        val expected = inputData.getLong(Notifications.EXTRA_COMPLETION_ID, -1L)
        val generation = inputData.getString(Notifications.EXTRA_GENERATION) ?: return Result.success()
        if (expected < 0) return Result.success()
        container.completeTask(taskId, expected, generation)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
    }
}
