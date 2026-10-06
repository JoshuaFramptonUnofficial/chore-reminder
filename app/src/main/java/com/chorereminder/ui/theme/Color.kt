package com.chorereminder.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback palette for when dynamic color is unavailable (FR-17). A calm green/teal
 * "household" palette rather than Compose's purple default.
 */
private val Green40 = Color(0xFF2F6E5B)
private val Green90 = Color(0xFFB3F1DC)
private val Green30 = Color(0xFF14543F)
private val Green80 = Color(0xFF97D5C0)

private val Sand40 = Color(0xFF745B0B)
private val Sand90 = Color(0xFFFFE08A)

private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)

val FallbackLightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green30,
    secondary = Sand40,
    secondaryContainer = Sand90,
    error = Red40,
    onError = Color.White,
)

val FallbackDarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green30,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Sand90,
    error = Red80,
    onError = Color(0xFF690005),
)
