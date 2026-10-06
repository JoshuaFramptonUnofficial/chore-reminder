package com.chorereminder.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.chorereminder.domain.IntervalUnit
import com.chorereminder.domain.Recurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

const val UNCATEGORIZED_ID: Long = 1L
const val UNCATEGORIZED_NAME = "Uncategorized"

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Manual ordering of category groups on the home screen. */
    val sortOrder: Int = 0,
)

/** How a task's icon is rendered: a curated Material icon, or a literal emoji. */
enum class IconType { MATERIAL, EMOJI }

/** Discriminator for the [Recurrence] sealed type, flattened for Room storage. */
enum class RecurrenceKind { RELATIVE, FIXED_INTERVAL, FIXED_WEEKDAY, ONE_OFF }

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // Deleting a category re-homes its tasks rather than destroying them.
            onDelete = ForeignKey.SET_DEFAULT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconType: IconType = IconType.MATERIAL,
    /** Material icon key (see IconCatalog) or the emoji string itself. */
    val iconValue: String = "CheckCircle",
    @ColumnInfo(defaultValue = "1") val categoryId: Long = UNCATEGORIZED_ID,
    val createdDate: LocalDate,
    @Embedded(prefix = "rec_") val recurrence: RecurrenceSpec,
) {
    /** Stable per-task notification id / PendingIntent request code (see plan). */
    val notificationId: Int get() = id.toInt()
}

/**
 * Flattened form of [Recurrence]. Room cannot persist a sealed hierarchy directly,
 * so the discriminator plus every field lives here and is mapped back on read.
 */
data class RecurrenceSpec(
    val kind: RecurrenceKind,
    val interval: Int = 1,
    val unit: IntervalUnit = IntervalUnit.DAYS,
    val anchor: LocalDate? = null,
    val dayOfWeek: DayOfWeek? = null,
) {
    fun toDomain(createdDate: LocalDate): Recurrence = when (kind) {
        RecurrenceKind.RELATIVE -> Recurrence.Relative(interval, unit)
        RecurrenceKind.FIXED_INTERVAL ->
            Recurrence.FixedInterval(anchor ?: createdDate, interval, unit)
        RecurrenceKind.FIXED_WEEKDAY ->
            Recurrence.FixedWeekday(dayOfWeek ?: createdDate.dayOfWeek)
        // Reuses the `anchor` column as the one-off due date -- no new column needed.
        RecurrenceKind.ONE_OFF -> Recurrence.OneOff(anchor ?: createdDate)
    }

    companion object {
        fun fromDomain(recurrence: Recurrence): RecurrenceSpec = when (recurrence) {
            is Recurrence.Relative -> RecurrenceSpec(
                kind = RecurrenceKind.RELATIVE,
                interval = recurrence.interval,
                unit = recurrence.unit,
            )
            is Recurrence.FixedInterval -> RecurrenceSpec(
                kind = RecurrenceKind.FIXED_INTERVAL,
                interval = recurrence.interval,
                unit = recurrence.unit,
                anchor = recurrence.anchor,
            )
            is Recurrence.FixedWeekday -> RecurrenceSpec(
                kind = RecurrenceKind.FIXED_WEEKDAY,
                dayOfWeek = recurrence.dayOfWeek,
            )
            is Recurrence.OneOff -> RecurrenceSpec(
                kind = RecurrenceKind.ONE_OFF,
                anchor = recurrence.dueDate,
            )
        }
    }
}

@Entity(
    tableName = "completions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            // FR-3: deleting a task removes its history permanently.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val completedAt: Instant,
    /** Local date of completion -- what the recurrence math actually keys off. */
    val completedOn: LocalDate,
)

/** Projection for [ChoreDao.observeCategoryTaskCounts]. */
data class CategoryTaskCount(val categoryId: Long, val count: Int)

/** A task with everything the UI needs to render and schedule it. */
data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity?,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val completions: List<CompletionEntity>,
) {
    val lastCompletion: CompletionEntity? get() = completions.maxByOrNull { it.completedAt }
}
