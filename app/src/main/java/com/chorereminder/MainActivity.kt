package com.chorereminder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.chorereminder.notify.Notifications
import com.chorereminder.ui.ChoreNavHost
import com.chorereminder.ui.theme.ChoreReminderTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Replay 1 so a task id delivered in `onCreate` -- before the NavHost has
     * started collecting -- is not dropped.
     */
    private val notificationTaskIds = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 4)
    /** Emits when the notification's Delay... action -- not just a plain tap -- opened the app. */
    private val notificationDelayTaskIds = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 4)
    /** Emits when the notification's Skip... action -- not just a plain tap -- opened the app. */
    private val notificationSkipTaskIds = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 4)
    private var remindersEnabled = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Denial is fine: the I/O matrix says the app stays usable and Settings
            // shows the "reminders off" explainer.
            refreshReminderPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ChoreApp).container

        handleIntent(intent)
        maybeRequestNotificationPermission()

        setContent {
            ChoreReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ChoreNavHost(
                        container = container,
                        notificationTaskIds = notificationTaskIds,
                        notificationDelayTaskIds = notificationDelayTaskIds,
                        notificationSkipTaskIds = notificationSkipTaskIds,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also catches permission/channel changes made in Android Settings. This
        // runs only for the foreground activity, never a worker's cold start.
        (application as ChoreApp).container.refreshDate()
        refreshReminderPermission()
    }

    private fun refreshReminderPermission() {
        val enabled = Notifications.canPost(this)
        if (enabled && !remindersEnabled) {
            lifecycleScope.launch { (application as ChoreApp).container.rescheduleAll() }
        }
        remindersEnabled = enabled
    }

    /**
     * The activity is `singleTop`, so a notification tap while it is already
     * running arrives here rather than through `onCreate`. Both paths must work.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val taskId = intent?.getLongExtra(Notifications.EXTRA_TASK_ID, -1L) ?: -1L
        if (taskId > 0L) {
            notificationTaskIds.tryEmit(taskId)
            if (intent?.getBooleanExtra(Notifications.EXTRA_OPEN_DELAY, false) == true) {
                notificationDelayTaskIds.tryEmit(taskId)
            }
            if (intent?.getBooleanExtra(Notifications.EXTRA_OPEN_SKIP, false) == true) {
                notificationSkipTaskIds.tryEmit(taskId)
            }
            // Consume it, so a configuration change doesn't re-navigate.
            intent?.removeExtra(Notifications.EXTRA_TASK_ID)
            intent?.removeExtra(Notifications.EXTRA_OPEN_DELAY)
            intent?.removeExtra(Notifications.EXTRA_OPEN_SKIP)
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (Notifications.canPost(this)) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
