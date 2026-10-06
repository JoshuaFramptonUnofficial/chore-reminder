package com.chorereminder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * FR-17/FR-18: Material You dynamic color on Android 12+, with a fixed fallback,
 * following the system light/dark setting.
 *
 * minSdk is 31, so dynamic color is effectively always available -- the version
 * guard is kept only because `dynamic*ColorScheme` is annotated for API 31.
 */
@Composable
fun ChoreReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> FallbackDarkColors
        else -> FallbackLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
