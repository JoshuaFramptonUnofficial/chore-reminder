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

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Denial is fine: the I/O matrix says the app stays usable and Settings
            // shows the "reminders off" explainer.
            if (granted) {
                lifecycleScope.launch { (application as ChoreApp).container.rescheduleAll() }
            }
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
                        notificationTaskIds = notificationTaskIds.asSharedFlow(),
                    )
                }
            }
        }
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
            // Consume it, so a configuration change doesn't re-navigate.
            intent?.removeExtra(Notifications.EXTRA_TASK_ID)
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (Notifications.canPost(this)) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
