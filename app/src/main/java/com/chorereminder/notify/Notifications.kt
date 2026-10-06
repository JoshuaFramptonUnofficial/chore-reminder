package com.chorereminder.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chorereminder.MainActivity
import com.chorereminder.R
import com.chorereminder.data.IconType
import com.chorereminder.data.TaskView

object Notifications {

    const val CHANNEL_ID = "chore_reminders"

    const val EXTRA_TASK_ID = "com.chorereminder.extra.TASK_ID"
    const val ACTION_COMPLETE = "com.chorereminder.action.COMPLETE"

    /**
     * PendingIntent identity ignores extras, so every task needs its own request
     * code or they all collapse onto the last-scheduled task. Content tap and the
     * action button also need distinct codes from each other.
     */
    private fun contentRequestCode(taskId: Long) = taskId.toInt() * 2
    private fun actionRequestCode(taskId: Long) = taskId.toInt() * 2 + 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // DEFAULT, not HIGH: FR counter-metric SM-C1 says nag, don't spam.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** The I/O matrix requires checking this before posting (FR: notifications denied). */
    fun canPost(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, view: TaskView) {
        if (!canPost(context)) return
        ensureChannel(context)

        val taskId = view.task.id

        // Tapping the body opens the confirmation screen (FR-10).
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            contentRequestCode(taskId),
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // "Mark Complete" is a broadcast, not an activity: notification trampolines
        // are blocked on API 31+, and the receiver does the DB write itself.
        val completeIntent = Intent(context, CompleteActionReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            actionRequestCode(taskId),
            completeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val emoji = if (view.task.iconType == IconType.EMOJI) "${view.task.iconValue} " else ""
        val title = "$emoji${view.task.name}"
        val body = when {
            view.dueState == com.chorereminder.domain.DueState.OVERDUE ->
                "Overdue since ${view.nextDue.month.name.lowercase().replaceFirstChar(Char::uppercase)} ${view.nextDue.dayOfMonth}"
            else -> "Due today"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(view.categoryName)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Mark Complete", completePending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(view.task.notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post -- nothing to do.
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }
}
