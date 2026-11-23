package com.chorereminder.notify

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Calendar-day policy, shared by scheduling and delivery and testable without Android. */
internal object ReminderTiming {
    private val reminderTime = LocalTime.of(9, 0)

    fun shouldNotify(nextDue: LocalDate, now: ZonedDateTime, lastNotifiedOn: LocalDate?): Boolean =
        !nextDue.isAfter(now.toLocalDate()) &&
            !now.toLocalTime().isBefore(reminderTime) &&
            lastNotifiedOn != now.toLocalDate()

    fun delayUntilNextReminder(
        nextDue: LocalDate,
        now: ZonedDateTime,
        lastNotifiedOn: LocalDate? = null,
        catchUp: Boolean = true,
    ): Duration {
        val today = now.toLocalDate()
        val earliest = if (lastNotifiedOn == today) today.plusDays(1) else today
        var target = maxOf(nextDue, earliest).atTime(reminderTime).atZone(now.zone)
        if (!target.isAfter(now)) {
            // New/recovered work catches a missed slot now. A worker's successor
            // waits until the next slot even if posting was denied, avoiding a loop.
            if (catchUp) return Duration.ZERO
            target = target.plusDays(1)
        }
        return Duration.between(now, target)
    }
}
