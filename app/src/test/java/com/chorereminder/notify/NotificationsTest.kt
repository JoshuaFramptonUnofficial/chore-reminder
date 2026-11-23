package com.chorereminder.notify

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.chorereminder.data.RecurrenceKind
import com.chorereminder.data.RecurrenceSpec
import com.chorereminder.data.TaskEntity
import com.chorereminder.data.TaskView
import com.chorereminder.domain.DueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationsTest {
    @Test
    fun android13Plus_requiresRuntimePermission() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(Notifications.canPost(app))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(Notifications.canPost(app))
    }

    @Test
    @Config(sdk = [31])
    fun android12_doesNotRequireAndroid13RuntimePermission() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(Notifications.canPost(app))
    }

    @Test
    fun disabledReminderChannel_preventsRecordingAnUndeliveredReminder() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Notifications.CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_NONE),
        )
        assertFalse(Notifications.canPost(app))
    }

    @Test
    fun postedNotification_offersDistinctCompleteDelayAndSkipActions() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val view = TaskView(
            task = TaskEntity(
                id = 7, name = "Water plants", createdDate = LocalDate.parse("2026-11-01"),
                recurrence = RecurrenceSpec(RecurrenceKind.RELATIVE),
            ),
            categoryName = "Uncategorized",
            nextDue = LocalDate.parse("2026-11-13"),
            dueState = DueState.DUE_TODAY,
            lastCompletedOn = null,
            completionCount = 0,
        )
        assertTrue(Notifications.post(app, view))
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java))
            .getNotification(view.task.notificationId)
        assertNotNull(posted)
        val actions = posted.actions
        assertEquals(3, actions.size)
        assertEquals("Mark Complete", actions[0].title)
        assertEquals("Delay…", actions[1].title)
        assertEquals("Skip…", actions[2].title)
        val intents = actions.map { it.actionIntent }
        assertEquals(intents.size, intents.distinct().size)
    }
}
