package com.chorereminder.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Quick snooze choices offered by the delay picker; a picked date carries its own value. */
sealed interface DelayOption {
    data object OneHour : DelayOption
    data object ThreeHours : DelayOption
    data object Tomorrow : DelayOption
    data class PickedDate(val date: LocalDate) : DelayOption
}

/** Delivery time used for day-granularity delays, matching the normal reminder slot. */
val DELAY_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/** Resolves a delay choice to the instant the chore should next alert at. */
fun DelayOption.resolve(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant = when (this) {
    DelayOption.OneHour -> now.plusSeconds(3600)
    DelayOption.ThreeHours -> now.plusSeconds(3 * 3600)
    DelayOption.Tomorrow -> now.atZone(zone).toLocalDate().plusDays(1).atTime(DELAY_REMINDER_TIME).atZone(zone).toInstant()
    is DelayOption.PickedDate -> date.atTime(DELAY_REMINDER_TIME).atZone(zone).toInstant()
}
