package com.chorereminder.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class ReminderTimingTest {
    private val today = LocalDate.parse("2026-11-13")
    private fun at(time: String) = ZonedDateTime.parse("2026-11-13T$time:00Z[Europe/London]")

    @Test
    fun dueToday_waitsUntilNineBeforeTheSlot() {
        assertEquals(Duration.ofHours(1), ReminderTiming.delayUntilNextReminder(today, at("08:00")))
        assertFalse(ReminderTiming.shouldNotify(today, at("08:00"), null))
    }

    @Test
    fun dueToday_atNineIsEligibleImmediately() {
        assertEquals(Duration.ZERO, ReminderTiming.delayUntilNextReminder(today, at("09:00")))
        assertTrue(ReminderTiming.shouldNotify(today, at("09:00"), null))
    }

    @Test
    fun missedSlot_catchesUpTodayInsteadOfPostponingUntilTomorrow() {
        assertEquals(Duration.ZERO, ReminderTiming.delayUntilNextReminder(today, at("15:00")))
        assertTrue(ReminderTiming.shouldNotify(today, at("15:00"), null))
        assertEquals(
            Duration.ZERO,
            ReminderTiming.delayUntilNextReminder(today.minusDays(7), at("15:00")),
        )
        assertTrue(ReminderTiming.shouldNotify(today.minusDays(7), at("15:00"), null))
    }

    @Test
    fun futureChore_doesNotNotifyBeforeItsDueDate() {
        assertFalse(ReminderTiming.shouldNotify(today.plusDays(2), at("15:00"), null))
        assertEquals(
            Duration.ofHours(42),
            ReminderTiming.delayUntilNextReminder(today.plusDays(2), at("15:00")),
        )
    }

    @Test
    fun alreadyNotifiedToday_recoveryAndEditsDoNotSendAgain() {
        assertFalse(ReminderTiming.shouldNotify(today.minusDays(7), at("15:00"), today))
        assertEquals(
            Duration.ofHours(18),
            ReminderTiming.delayUntilNextReminder(today.minusDays(7), at("15:00"), today),
        )
        assertEquals(
            Duration.ofHours(25),
            ReminderTiming.delayUntilNextReminder(today, at("08:00"), today),
        )
    }

    @Test
    fun incompleteChore_isEligibleAgainOnTheNextDay() {
        assertTrue(ReminderTiming.shouldNotify(today.minusDays(7), at("09:00"), today.minusDays(1)))
    }

    @Test
    fun workerSuccessor_doesNotSpinWhenPermissionWasDenied() {
        assertEquals(
            Duration.ofHours(24),
            ReminderTiming.delayUntilNextReminder(today, at("09:00"), catchUp = false),
        )
        assertEquals(
            Duration.ofHours(18),
            ReminderTiming.delayUntilNextReminder(today, at("15:00"), catchUp = false),
        )
    }

    @Test
    fun earlyWorkerAfterClockChange_stillSchedulesTodaysSlot() {
        assertEquals(
            Duration.ofHours(1),
            ReminderTiming.delayUntilNextReminder(today, at("08:00"), catchUp = false),
        )
    }

    @Test
    fun dailyRepeat_usesLocalNineAcrossSpringAndAutumnClockChanges() {
        val spring = ZonedDateTime.parse("2026-03-28T09:00:00Z[Europe/London]")
        assertEquals(
            Duration.ofHours(23),
            ReminderTiming.delayUntilNextReminder(spring.toLocalDate(), spring, catchUp = false),
        )
        val autumn = ZonedDateTime.parse("2026-10-24T09:00:00+01:00[Europe/London]")
        assertEquals(
            Duration.ofHours(25),
            ReminderTiming.delayUntilNextReminder(autumn.toLocalDate(), autumn, catchUp = false),
        )
    }

    @Test
    fun completedRecurringChore_usesItsNewDueDateRatherThanOverdueRepeat() {
        val now = at("15:00")
        val newDue = today.plusDays(14)
        assertFalse(ReminderTiming.shouldNotify(newDue, now, today))
        assertEquals(
            Duration.ofDays(13).plusHours(18),
            ReminderTiming.delayUntilNextReminder(newDue, now, today),
        )
    }
}
