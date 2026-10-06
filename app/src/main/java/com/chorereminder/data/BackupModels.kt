package com.chorereminder.data

import com.chorereminder.domain.IntervalUnit
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Versioned envelope for export/import (FR-15/16). [version] lets a future schema
 * change reject or migrate an old file instead of silently mis-parsing it.
 *
 * Dates are ISO-8601 strings rather than epoch numbers so the file is genuinely
 * human-readable, as FR-15 requires.
 */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val categories: List<BackupCategory>,
    val tasks: List<BackupTask>,
    val completions: List<BackupCompletion>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupTask(
    val id: Long,
    val name: String,
    val iconType: String,
    val iconValue: String,
    val categoryId: Long,
    /** ISO-8601 local date, e.g. "2026-10-01". */
    val createdDate: String,
    val recurrenceKind: String,
    val interval: Int,
    val unit: String,
    val anchor: String? = null,
    /** ISO-8601 day name, e.g. "MONDAY". */
    val dayOfWeek: String? = null,
)

@Serializable
data class BackupCompletion(
    val id: Long,
    val taskId: Long,
    /** ISO-8601 instant, e.g. "2026-10-05T09:30:00Z". */
    val completedAt: String,
    val completedOn: String,
)

// --- Mapping ------------------------------------------------------------------

fun CategoryEntity.toBackup() = BackupCategory(id = id, name = name, sortOrder = sortOrder)

fun BackupCategory.toEntity() = CategoryEntity(id = id, name = name, sortOrder = sortOrder)

fun TaskEntity.toBackup() = BackupTask(
    id = id,
    name = name,
    iconType = iconType.name,
    iconValue = iconValue,
    categoryId = categoryId,
    createdDate = createdDate.toString(),
    recurrenceKind = recurrence.kind.name,
    interval = recurrence.interval,
    unit = recurrence.unit.name,
    anchor = recurrence.anchor?.toString(),
    dayOfWeek = recurrence.dayOfWeek?.name,
)

/** Throws on malformed input; callers parse inside a try so the import rolls back. */
fun BackupTask.toEntity() = TaskEntity(
    id = id,
    name = name,
    iconType = IconType.valueOf(iconType),
    iconValue = iconValue,
    categoryId = categoryId,
    createdDate = LocalDate.parse(createdDate),
    recurrence = RecurrenceSpec(
        kind = RecurrenceKind.valueOf(recurrenceKind),
        interval = interval,
        unit = IntervalUnit.valueOf(unit),
        anchor = anchor?.let(LocalDate::parse),
        dayOfWeek = dayOfWeek?.let(DayOfWeek::valueOf),
    ),
)

fun CompletionEntity.toBackup() = BackupCompletion(
    id = id,
    taskId = taskId,
    completedAt = completedAt.toString(),
    completedOn = completedOn.toString(),
)

fun BackupCompletion.toEntity() = CompletionEntity(
    id = id,
    taskId = taskId,
    completedAt = Instant.parse(completedAt),
    completedOn = LocalDate.parse(completedOn),
)
