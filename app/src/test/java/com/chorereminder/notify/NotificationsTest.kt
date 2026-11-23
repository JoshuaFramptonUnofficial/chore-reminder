package com.chorereminder.notify

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
}
