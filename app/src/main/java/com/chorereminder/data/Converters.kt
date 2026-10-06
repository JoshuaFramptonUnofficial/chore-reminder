package com.chorereminder.data

import androidx.room.TypeConverter
import com.chorereminder.domain.IntervalUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters. Dates are stored as epoch-day longs and instants as epoch
 * millis so the DB stays timezone-independent and sortable.
 */
class Converters {

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun dayOfWeekToInt(value: DayOfWeek?): Int? = value?.value

    @TypeConverter
    fun intToDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)

    @TypeConverter
    fun intervalUnitToName(value: IntervalUnit?): String? = value?.name

    @TypeConverter
    fun nameToIntervalUnit(value: String?): IntervalUnit? = value?.let(IntervalUnit::valueOf)

    @TypeConverter
    fun iconTypeToName(value: IconType?): String? = value?.name

    @TypeConverter
    fun nameToIconType(value: String?): IconType? = value?.let(IconType::valueOf)

    @TypeConverter
    fun recurrenceKindToName(value: RecurrenceKind?): String? = value?.name

    @TypeConverter
    fun nameToRecurrenceKind(value: String?): RecurrenceKind? =
        value?.let(RecurrenceKind::valueOf)
}
