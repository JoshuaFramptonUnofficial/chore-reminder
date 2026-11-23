package com.chorereminder.data

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UiPreferencesTest {
    @Test fun followIsOptIn_andPersistsForNewReaders() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val preferences = UiPreferences(app)
        assertFalse(preferences.followCompleted.first())
        preferences.setFollowCompleted(true)
        assertTrue(UiPreferences(app).followCompleted.first())
        preferences.setFollowCompleted(false)
        assertFalse(UiPreferences(app).followCompleted.first())
    }

    @Test fun glassBlurDefaultsToRecommended_andPersistsIndependentlyOfScrolling() = runBlocking {
        val preferences = UiPreferences(RuntimeEnvironment.getApplication())
        assertEquals(4, preferences.liquidGlassBlur.first())
        try {
            preferences.setFollowCompleted(true)
            preferences.setLiquidGlassBlur(8)
            val reader = UiPreferences(RuntimeEnvironment.getApplication())
            assertEquals(8, reader.liquidGlassBlur.first())
            assertTrue(reader.followCompleted.first())
            preferences.setFollowCompleted(false)
            assertEquals(8, reader.liquidGlassBlur.first())
        } finally {
            preferences.setLiquidGlassBlur(UiPreferences.DEFAULT_GLASS_BLUR)
            preferences.setFollowCompleted(false)
        }
    }

    @Test fun glassBlurClampsValuesToSupportedRange() = runBlocking {
        val preferences = UiPreferences(RuntimeEnvironment.getApplication())
        try {
            preferences.setLiquidGlassBlur(-5)
            assertEquals(0, preferences.liquidGlassBlur.first())
            preferences.setLiquidGlassBlur(999)
            assertEquals(12, preferences.liquidGlassBlur.first())
        } finally { preferences.setLiquidGlassBlur(UiPreferences.DEFAULT_GLASS_BLUR) }
    }
}
