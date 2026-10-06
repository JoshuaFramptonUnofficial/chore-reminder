package com.chorereminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Handles the notification's "Mark Complete" action button.
 *
 * `onReceive` runs on the main thread and is capped at ~10 s, so the DB write is
 * handed to WorkManager rather than done inline. The notification is cancelled here
 * directly -- `setAutoCancel` does not apply to action buttons.
 */
class CompleteActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifications.ACTION_COMPLETE) return
        val taskId = intent.getLongExtra(Notifications.EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return

        Notifications.cancel(context, taskId)

        val request = OneTimeWorkRequestBuilder<CompleteTaskWorker>()
            .setInputData(workDataOf(CompleteTaskWorker.KEY_TASK_ID to taskId))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }
}
