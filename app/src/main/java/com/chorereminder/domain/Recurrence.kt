package com.chorereminder.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How often a task repeats. Pure Kotlin + java.time -- no Android dependencies, so
 * every rule here is covered by plain JVM unit tests. This is the one place where a
 * silent bug costs the user a missed chore, so the math is kept deliberately explicit.
 */
sealed interface Recurrence {

    /**
     * Repeats [interval] [unit]s after the last completion (or after the task's
     * creation date if it has never been completed). Completing late pushes the next
     * due date out; completing early pulls it in.
     */
    data class Relative(val interval: Int, val unit: IntervalUnit) : Recurrence

    /**
     * Repeats on a calendar grid anchored at [anchor], every [interval] [unit]s,
     * regardless of when the task is actually completed. Completing early or late
     * never moves the anchor.
     */
    data class FixedInterval(
        val anchor: LocalDate,
        val interval: Int,
        val unit: IntervalUnit,
    ) : Recurrence

    /** Repeats on a specific weekday, e.g. "every Monday". */
    data class FixedWeekday(val dayOfWeek: DayOfWeek) : Recurrence

    /** A single-occurrence task with no repeat. Due on [dueDate], done for good once completed. */
    data class OneOff(val dueDate: LocalDate) : Recurrence
}

enum class IntervalUnit { DAYS, WEEKS, MONTHS }

/**
 * Adds [count] units to [this]. Month arithmetic clamps to the end of the target
 * month (Jan 31 + 1 month = Feb 28), which java.time does for us.
 */
internal fun LocalDate.plus(count: Long, unit: IntervalUnit): LocalDate = when (unit) {
    IntervalUnit.DAYS -> plusDays(count)
    IntervalUnit.WEEKS -> plusWeeks(count)
    IntervalUnit.MONTHS -> plusMonths(count)
}

/**
 * The next date on which [recurrence] falls due.
 *
 * @param createdDate the date the task was created -- the fallback reference point
 *   for a task that has never been completed.
 * @param lastCompletion the actual local date of the latest inserted completion.
 * @param completedOccurrence the scheduled due date consumed under this same rule;
 *   null for legacy history. Only fixed rules use it.
 */
fun nextDueDate(
    recurrence: Recurrence,
    createdDate: LocalDate,
    lastCompletion: LocalDate?,
    completedOccurrence: LocalDate? = null,
): LocalDate {
    // A fixed rule consumes the displayed occurrence, even when completed early.
    // Late completion still skips missed grid dates as in previous releases.
    val fixedThrough = listOfNotNull(lastCompletion, completedOccurrence).maxOrNull()
    return when (recurrence) {
        is Recurrence.Relative -> {
            // Relative rules always measure from the last completion, falling back to
            // the creation date. Interval is coerced to >= 1 so a corrupt 0 can never
            // produce a due date that never advances.
            val base = lastCompletion ?: createdDate
            base.plus(recurrence.interval.coerceAtLeast(1).toLong(), recurrence.unit)
        }

        is Recurrence.FixedInterval -> nextFixedIntervalOccurrence(recurrence, fixedThrough)

        is Recurrence.FixedWeekday -> {
            if (fixedThrough == null) {
                // Never completed: the next occurrence on or after creation, so a task
                // created on its own weekday is due that same day.
                nextOrSameWeekday(createdDate, recurrence.dayOfWeek)
            } else {
                // Completed: strictly after, so completing on Monday schedules the
                // following Monday rather than re-arming today.
                nextOrSameWeekday(fixedThrough.plusDays(1), recurrence.dayOfWeek)
            }
        }

        // A one-off task's due date never moves -- whether it's hidden after
        // completion is decided by the caller, not by the date math.
        is Recurrence.OneOff -> recurrence.dueDate
    }
}

/** Stable identity of the effective rule; old consumed dates never constrain an edited rule. */
fun Recurrence.scheduleKey(): String = when (this) {
    is Recurrence.Relative -> "relative:$interval:${unit.name}"
    is Recurrence.FixedInterval -> "fixed:$anchor:$interval:${unit.name}"
    is Recurrence.FixedWeekday -> "weekday:${dayOfWeek.name}"
    is Recurrence.OneOff -> "once:$dueDate"
}

private fun nextOrSameWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
    val delta = (target.value - from.dayOfWeek.value + 7) % 7
    return from.plusDays(delta.toLong())
}

/**
 * The first occurrence of the fixed grid that falls strictly after [lastCompletion],
 * or the anchor itself if the task has never been completed.
 *
 * Occurrences are always computed as `anchor.plus(k * interval)` -- never by stepping
 * forward from the previous occurrence. That distinction is what makes a monthly rule
 * anchored on Jan 31 produce Feb 28 then *Mar 31*; iterative addition would clamp
 * once and then be stuck on the 28th forever.
 */
private fun nextFixedIntervalOccurrence(
    rule: Recurrence.FixedInterval,
    lastCompletion: LocalDate?,
): LocalDate {
    val interval = rule.interval.coerceAtLeast(1).toLong()
    if (lastCompletion == null) return rule.anchor

    // Jump straight to an approximate k, then walk the (very short) remaining
    // distance. The estimate is never more than a step or two off even with month
    // clamping, and the walk is bounded in both directions.
    var k = when (rule.unit) {
        IntervalUnit.DAYS ->
            (lastCompletion.toEpochDay() - rule.anchor.toEpochDay()) / interval
        IntervalUnit.WEEKS ->
            (lastCompletion.toEpochDay() - rule.anchor.toEpochDay()) / (interval * 7)
        IntervalUnit.MONTHS ->
            java.time.temporal.ChronoUnit.MONTHS.between(rule.anchor, lastCompletion) / interval
    }
    if (k < 0) k = 0

    // Walk back to the last occurrence that is not after the completion...
    while (k > 0 && rule.anchor.plus(k * interval, rule.unit) > lastCompletion) {
        k--
    }
    // ...then forward to the first occurrence strictly after it.
    while (rule.anchor.plus(k * interval, rule.unit) <= lastCompletion) {
        k++
    }
    return rule.anchor.plus(k * interval, rule.unit)
}
