package com.chorereminder.ui

import com.chorereminder.data.RecurrenceSpec
import com.chorereminder.data.RecurrenceKind
import com.chorereminder.domain.DueState
import com.chorereminder.domain.IntervalUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val MEDIUM_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val SHORT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun formatDate(date: LocalDate): String = date.format(MEDIUM_DATE)

/** Human label for a pending delay, e.g. "Delayed until 3:00 PM" / "...tomorrow". */
fun formatDelayedUntil(until: Instant, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = until.atZone(zone)
    return when (zoned.toLocalDate()) {
        today -> "Delayed until ${zoned.toLocalTime().format(SHORT_TIME)}"
        today.plusDays(1) -> "Delayed until tomorrow"
        else -> "Delayed until ${formatDate(zoned.toLocalDate())}"
    }
}

/** Human phrasing for a due date, e.g. "Overdue by 3 days" / "Due today" / "in 5 days". */
fun formatDueLabel(nextDue: LocalDate, state: DueState, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, nextDue)
    return when (state) {
        DueState.OVERDUE -> {
            val overdueBy = -days
            if (overdueBy == 1L) "Overdue by 1 day" else "Overdue by $overdueBy days"
        }
        DueState.DUE_TODAY -> "Due today"
        DueState.UPCOMING -> when (days) {
            1L -> "Due tomorrow"
            in 2L..6L -> "Due in $days days"
            else -> "Due ${nextDue.format(SHORT_DATE)}"
        }
    }
}

/** One-line summary of a recurrence rule for list and detail rows. */
fun formatRecurrence(spec: RecurrenceSpec): String {
    val unitName = when (spec.unit) {
        IntervalUnit.DAYS -> if (spec.interval == 1) "day" else "days"
        IntervalUnit.WEEKS -> if (spec.interval == 1) "week" else "weeks"
        IntervalUnit.MONTHS -> if (spec.interval == 1) "month" else "months"
    }
    return when (spec.kind) {
        RecurrenceKind.RELATIVE ->
            if (spec.interval == 1) "Every $unitName after completing"
            else "Every ${spec.interval} $unitName after completing"
        RecurrenceKind.FIXED_INTERVAL ->
            if (spec.interval == 1) "Every $unitName on a fixed schedule"
            else "Every ${spec.interval} $unitName on a fixed schedule"
        RecurrenceKind.FIXED_WEEKDAY -> {
            val day = spec.dayOfWeek
                ?.getDisplayName(TextStyle.FULL, Locale.getDefault())
                ?: "week"
            "Every $day"
        }
        RecurrenceKind.ONE_OFF -> "One-off"
    }
}

/**
 * Explains what completing a not-yet-due task will actually do, since the effect
 * differs by recurrence kind: a relative schedule moves to count from today, a
 * fixed schedule's grid doesn't budge, and a one-off task simply goes away.
 */
fun formatEarlyCompletionMessage(
    taskName: String,
    kind: RecurrenceKind,
    currentDue: LocalDate,
    newNextDue: LocalDate,
): String = when (kind) {
    RecurrenceKind.ONE_OFF ->
        "\"$taskName\" is due ${formatDate(currentDue)}. You can undo completion from Home."
    else ->
        "\"$taskName\" isn't due until ${formatDate(currentDue)}. Completing it now will set " +
            "the next occurrence to ${formatDate(newNextDue)}."
}

/**
 * Explains what skipping an occurrence will do -- it consumes the current due
 * date exactly like completing does, just without logging it as done.
 */
fun formatSkipMessage(
    taskName: String,
    kind: RecurrenceKind,
    newNextDue: LocalDate,
): String = when (kind) {
    RecurrenceKind.ONE_OFF ->
        "\"$taskName\" will be removed from your list. You can undo this from Home."
    else ->
        "\"$taskName\" won't be marked done. Its next occurrence moves to ${formatDate(newNextDue)}."
}
