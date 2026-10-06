package com.chorereminder.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant

/** Outcome of an export/import, surfaced verbatim to the user in Settings. */
sealed interface BackupResult {
    data class Success(val message: String) : BackupResult
    data class Failure(val message: String) : BackupResult
}

/**
 * JSON export/import over SAF (FR-15/16). All file and DB work happens on
 * [Dispatchers.IO] -- never the main thread.
 */
class BackupManager(
    private val context: Context,
    private val repository: ChoreRepository,
) {

    private val json = Json {
        prettyPrint = true
        // FR-16: a file from a future/odd build should be rejected loudly by the
        // version check below, not silently half-read.
        ignoreUnknownKeys = true
    }

    fun suggestedFileName(): String {
        val stamp = Instant.now().toString().replace(":", "-").substringBefore(".")
        return "chores-backup-$stamp.json"
    }

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val (categories, tasks, completions) = repository.snapshot()
            val backup = BackupFile(
                exportedAt = Instant.now().toString(),
                categories = categories.map { it.toBackup() },
                tasks = tasks.map { it.toBackup() },
                completions = completions.map { it.toBackup() },
            )
            val text = json.encodeToString(backup)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(text.toByteArray())
            } ?: return@withContext BackupResult.Failure("Could not open the file for writing.")

            BackupResult.Success("Exported ${tasks.size} tasks.")
        } catch (e: Exception) {
            BackupResult.Failure("Export failed: ${e.message ?: e::class.simpleName}")
        }
    }

    /**
     * Full replace, per the PRD assumption in section 4.6. Everything is parsed into
     * entities *before* a single row is touched, and the write itself is one
     * transaction -- so a malformed file leaves existing data completely untouched.
     */
    suspend fun import(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val parsed = try {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().decodeToString() }
                ?: return@withContext BackupResult.Failure("Could not open the file.")

            val backup = json.decodeFromString<BackupFile>(text)
            if (backup.version > BackupFile.CURRENT_VERSION) {
                return@withContext BackupResult.Failure(
                    "That backup was made by a newer version of the app.",
                )
            }

            // Parse everything up front: any bad enum name or date string throws
            // here, before the DB has been touched at all.
            val categories = backup.categories.map { it.toEntity() }
            val tasks = backup.tasks.map { it.toEntity() }
            val completions = backup.completions.map { it.toEntity() }

            // Guarantee the default category survives and no task points at a
            // category the file forgot to include.
            val categoryIds = categories.mapTo(mutableSetOf()) { it.id }
            val withDefault = if (UNCATEGORIZED_ID in categoryIds) {
                categories
            } else {
                categories + CategoryEntity(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, 1000)
            }
            val safeIds = withDefault.mapTo(mutableSetOf()) { it.id }
            val repaired = tasks.map { task ->
                if (task.categoryId in safeIds) task
                else task.copy(categoryId = UNCATEGORIZED_ID)
            }
            val taskIds = repaired.mapTo(mutableSetOf()) { it.id }
            // Orphan completions would violate the FK and abort the transaction.
            val validCompletions = completions.filter { it.taskId in taskIds }

            Triple(withDefault, repaired, validCompletions)
        } catch (e: Exception) {
            return@withContext BackupResult.Failure(
                "That file isn't a valid backup. Your data was not changed.",
            )
        }

        try {
            repository.replaceAll(parsed.first, parsed.second, parsed.third)
            BackupResult.Success("Imported ${parsed.second.size} tasks.")
        } catch (e: Exception) {
            BackupResult.Failure("Import failed: ${e.message ?: "your data was not changed."}")
        }
    }
}
