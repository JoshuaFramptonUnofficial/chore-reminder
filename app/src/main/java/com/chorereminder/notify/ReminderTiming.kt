package com.chorereminder.notify

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Calendar-day policy, shared by scheduling and delivery and testable without Android. */
internal object ReminderTiming {
    private val reminderTime = LocalTime.of(9, 0)

    /**
     * [snoozedUntil] is a one-shot override: while `now` is still before it, every
     * other gate is suppressed. Once it has passed, both functions fall straight
     * through to the normal day/time rules below -- there is no separate "snooze
     * just lapsed" branch, so nothing here can loop once the override fires. The
     * normal once-a-day gate resuming is what stops a second notification; arming
     * a delay is expected to also clear today's delivery receipt (see
     * `AppContainer.delayTask`) so that gate doesn't block the overridden slot.
     */
    fun shouldNotify(
        nextDue: LocalDate,
        now: ZonedDateTime,
        lastNotifiedOn: LocalDate?,
        snoozedUntil: Instant? = null,
    ): Boolean {
        if (snoozedUntil != null && now.toInstant().isBefore(snoozedUntil)) return false
        return !nextDue.isAfter(now.toLocalDate()) &&
            !now.toLocalTime().isBefore(reminderTime) &&
            lastNotifiedOn != now.toLocalDate()
    }

    fun delayUntilNextReminder(
        nextDue: LocalDate,
        now: ZonedDateTime,
        lastNotifiedOn: LocalDate? = null,
        catchUp: Boolean = true,
        snoozedUntil: Instant? = null,
    ): Duration {
        if (snoozedUntil != null && now.toInstant().isBefore(snoozedUntil)) {
            return Duration.between(now.toInstant(), snoozedUntil)
        }
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
