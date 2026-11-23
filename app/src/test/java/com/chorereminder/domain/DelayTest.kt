package com.chorereminder.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DelayTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = Instant.parse("2026-11-13T14:00:00Z")

    @Test fun oneHour_addsExactlyOneHour() {
        assertEquals(now.plusSeconds(3600), DelayOption.OneHour.resolve(now, zone))
    }

    @Test fun threeHours_addsExactlyThreeHours() {
        assertEquals(now.plusSeconds(3 * 3600), DelayOption.ThreeHours.resolve(now, zone))
    }

    @Test fun tomorrow_landsOnTheNextDayAtTheUsualReminderTime() {
        val resolved = DelayOption.Tomorrow.resolve(now, zone)
        assertEquals(LocalDate.parse("2026-11-14").atTime(9, 0).atZone(zone).toInstant(), resolved)
    }

    @Test fun pickedDate_landsOnThatDateAtTheUsualReminderTime() {
        val picked = DelayOption.PickedDate(LocalDate.parse("2026-12-01"))
        assertEquals(LocalDate.parse("2026-12-01").atTime(9, 0).atZone(zone).toInstant(), picked.resolve(now, zone))
    }
}
