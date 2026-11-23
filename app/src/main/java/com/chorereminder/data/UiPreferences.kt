package com.chorereminder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.uiPreferences by preferencesDataStore("ui_preferences")

/** Device-local presentation choice, independent of chore data and reminders. */
class UiPreferences(context: Context) {
    private val store = context.applicationContext.uiPreferences
    private val scrollKey = booleanPreferencesKey("follow_completed_chore")
    private val blurKey = intPreferencesKey("liquid_glass_blur")
    val liquidGlassBlur = store.data.map { (it[blurKey] ?: DEFAULT_GLASS_BLUR).coerceIn(MIN_GLASS_BLUR, MAX_GLASS_BLUR) }
    suspend fun setLiquidGlassBlur(value: Int) {
        store.edit { it[blurKey] = value.coerceIn(MIN_GLASS_BLUR, MAX_GLASS_BLUR) }
    }

    val followCompleted = store.data.map { it[scrollKey] ?: false }
    suspend fun setFollowCompleted(value: Boolean) { store.edit { it[scrollKey] = value } }

    companion object {
        const val MIN_GLASS_BLUR = 0
        const val MAX_GLASS_BLUR = 12
        const val DEFAULT_GLASS_BLUR = 4
    }
}
