package com.chorereminder.notify

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.reminderHistoryStore by preferencesDataStore(name = "reminder_history")

/** Device-local delivery receipts survive process death; not part of chore backups. */
class ReminderHistory(context: Context) {
    private val store = context.applicationContext.reminderHistoryStore

    suspend fun lastNotifiedOn(taskId: Long): LocalDate? =
        store.data.first()[key(taskId)]?.let(LocalDate::ofEpochDay)

    suspend fun markNotified(taskId: Long, date: LocalDate) {
        store.edit { it[key(taskId)] = date.toEpochDay() }
    }

    suspend fun forget(taskId: Long) {
        store.edit { it.remove(key(taskId)) }
    }

    suspend fun clear() {
        store.edit { it.clear(); it[stringPreferencesKey("generation")] = java.util.UUID.randomUUID().toString() }
    }

    suspend fun generation(): String = store.data.first()[stringPreferencesKey("generation")] ?: "initial"

    private fun key(taskId: Long) = longPreferencesKey("last_notified_$taskId")
}
