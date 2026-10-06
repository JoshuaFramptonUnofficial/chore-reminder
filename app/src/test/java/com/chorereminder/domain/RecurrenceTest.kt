package com.chorereminder.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Every row of the plan's I/O & Edge-Case Matrix, plus the boundary cases around
 * them. The matrix is the spec for this file.
 */
class RecurrenceTest {

    private fun date(s: String) = LocalDate.parse(s)

    // --- Matrix row: Relative, never done -------------------------------------
    // interval 14d, created Oct 1, no Completion -> nextDue = Oct 15

    @Test
    fun relative_neverCompleted_countsFromCreationDate() {
        val next = nextDueDate(
            recurrence = Recurrence.Relative(14, IntervalUnit.DAYS),
            createdDate = date("2026-10-01"),
            lastCompletion = null,
        )
        assertEquals(date("2026-10-15"), next)
    }

    // --- Matrix row: Relative, completed late ---------------------------------
    // interval 14d, due Oct 15, completed Oct 20 -> nextDue = Nov 3

    @Test
    fun relative_completedLate_countsFromCompletionNotDueDate() {
        val next = nextDueDate(
            recurrence = Recurrence.Relative(14, IntervalUnit.DAYS),
            createdDate = date("2026-10-01"),
            lastCompletion = date("2026-10-20"),
        )
        assertEquals(date("2026-11-03"), next)
    }

    @Test
    fun relative_completedEarly_pullsNextDueIn() {
        val next = nextDueDate(
            recurrence = Recurrence.Relative(14, IntervalUnit.DAYS),
            createdDate = date("2026-10-01"),
            lastCompletion = date("2026-10-10"),
        )
        assertEquals(date("2026-10-24"), next)
    }

    @Test
    fun relative_weeksAndMonthsUnits() {
        assertEquals(
            date("2026-10-15"),
            nextDueDate(
                Recurrence.Relative(2, IntervalUnit.WEEKS),
                date("2026-10-01"),
                null,
            ),
        )
        assertEquals(
            date("2026-12-01"),
            nextDueDate(
                Recurrence.Relative(2, IntervalUnit.MONTHS),
                date("2026-10-01"),
                null,
            ),
        )
    }

    // --- Matrix row: Fixed interval, completed early --------------------------
    // every 14d from Oct 1, completed Oct 10 -> nextDue stays Oct 15 (anchor unmoved)

    @Test
    fun fixedInterval_completedEarly_keepsCalendarAnchor() {
        val next = nextDueDate(
            recurrence = Recurrence.FixedInterval(date("2026-10-01"), 14, IntervalUnit.DAYS),
            createdDate = date("2026-10-01"),
            lastCompletion = date("2026-10-10"),
        )
        assertEquals(date("2026-10-15"), next)
    }

    @Test
    fun fixedInterval_completedLate_skipsToNextGridPointAfterCompletion() {
        // Grid: Oct 1, Oct 15, Oct 29. Completing Oct 20 lands between grid points,
        // so the next due date is Oct 29 -- the grid is never re-phased.
        val next = nextDueDate(
            recurrence = Recurrence.FixedInterval(date("2026-10-01"), 14, IntervalUnit.DAYS),
            createdDate = date("2026-10-01"),
            lastCompletion = date("2026-10-20"),
        )
        assertEquals(date("2026-10-29"), next)
    }

    @Test
    fun fixedInterval_completedExactlyOnGridPoint_advancesOneFullInterval() {
        val next = nextDueDate(
            recurrence = Recurrence.FixedInterval(date("2026-10-01"), 14, IntervalUnit.DAYS),
            createdDate = date("2026-10-01"),
            lastCompletion = date("2026-10-15"),
        )
        assertEquals(date("2026-10-29"), next)
    }

    @Test
    fun fixedInterval_neverCompleted_isTheAnchorItself() {
        val next = nextDueDate(
            recurrence = Recurrence.FixedInterval(date("2026-10-01"), 14, IntervalUnit.DAYS),
            createdDate = date("2026-09-20"),
            lastCompletion = null,
        )
        assertEquals(date("2026-10-01"), next)
    }

    @Test
    fun fixedInterval_completionBeforeAnchor_stillReturnsAnchor() {
        val next = nextDueDate(
            recurrence = Recurrence.FixedInterval(date("2026-10-01"), 14, IntervalUnit.DAYS),
            createdDate = date("2026-09-01"),
            lastCompletion = date("2026-09-05"),
        )
        assertEquals(date("2026-10-01"), next)
    }

    @Test
    fun fixedInterval_longGapSkipsManyIntervalsAtOnce() {
        // A task ignored for most of a year must not walk forward one interval per
        // loop iteration into a stale date -- it jumps to the next real grid point.
        val next = nextDueDate(
            recurrence = Recurrence.FixedInterval(date("2026-01-01"), 7, IntervalUnit.DAYS),
            createdDate = date("2026-01-01"),
            lastCompletion = date("2026-11-18"),
        )
        // Grid is Thursdays (Jan 1 2026 is a Thursday): Jan 1 + 46*7 = Nov 19.
        // Completing on Wed Nov 18 lands just short of it, so Nov 19 is next.
        assertEquals(date("2026-11-19"), next)
    }

    // --- Matrix row: Fixed weekday --------------------------------------------
    // "every Monday", today Mon Oct 5 completed -> nextDue = Mon Oct 12, not today

    @Test
    fun fixedWeekday_completedOnItsOwnWeekday_schedulesNextWeekNotToday() {
        val next = nextDueDate(
            recurrence = Recurrence.FixedWeekday(DayOfWeek.MONDAY),
            createdDate = date("2026-09-01"),
            lastCompletion = date("2026-10-05"), // a Monday
        )
        assertEquals(DayOfWeek.MONDAY, date("2026-10-05").dayOfWeek)
        assertEquals(date("2026-10-12"), next)
    }

    @Test
    fun fixedWeekday_neverCompleted_isNextOrSameWeekdayFromCreation() {
        // Created on a Thursday, due the following Monday.
        assertEquals(
            date("2026-10-05"),
            nextDueDate(
                Recurrence.FixedWeekday(DayOfWeek.MONDAY),
                date("2026-10-01"),
                null,
            ),
        )
        // Created on the target weekday itself -> due that same day.
        assertEquals(
            date("2026-10-05"),
            nextDueDate(
                Recurrence.FixedWeekday(DayOfWeek.MONDAY),
                date("2026-10-05"),
                null,
            ),
        )
    }

    @Test
    fun fixedWeekday_completedOffWeekday_findsFollowingWeekday() {
        // Completed Wednesday Oct 7; next Monday is Oct 12.
        val next = nextDueDate(
            recurrence = Recurrence.FixedWeekday(DayOfWeek.MONDAY),
            createdDate = date("2026-09-01"),
            lastCompletion = date("2026-10-07"),
        )
        assertEquals(date("2026-10-12"), next)
    }

    // --- Matrix row: Month rollover -------------------------------------------
    // monthly from Jan 31 -> Feb 28 (clamp to month end), then Mar 31

    @Test
    fun monthly_fromJan31_clampsToFeb28ThenRecoversToMar31() {
        val rule = Recurrence.FixedInterval(date("2026-01-31"), 1, IntervalUnit.MONTHS)

        val afterJan = nextDueDate(rule, date("2026-01-31"), date("2026-01-31"))
        assertEquals("clamps to the short month", date("2026-02-28"), afterJan)

        val afterFeb = nextDueDate(rule, date("2026-01-31"), afterJan)
        assertEquals("recovers the 31st -- never stuck on the 28th", date("2026-03-31"), afterFeb)

        val afterMar = nextDueDate(rule, date("2026-01-31"), afterFeb)
        assertEquals(date("2026-04-30"), afterMar)
    }

    @Test
    fun monthly_leapYearFeb29() {
        val rule = Recurrence.FixedInterval(date("2028-01-31"), 1, IntervalUnit.MONTHS)
        assertEquals(
            date("2028-02-29"),
            nextDueDate(rule, date("2028-01-31"), date("2028-01-31")),
        )
    }

    @Test
    fun relativeMonthly_alsoClampsWithoutOverflowing() {
        assertEquals(
            date("2026-02-28"),
            nextDueDate(
                Recurrence.Relative(1, IntervalUnit.MONTHS),
                date("2026-01-31"),
                date("2026-01-31"),
            ),
        )
    }

    // --- Degenerate input ------------------------------------------------------

    @Test
    fun zeroOrNegativeInterval_isCoercedSoDueDateAlwaysAdvances() {
        assertEquals(
            date("2026-10-02"),
            nextDueDate(
                Recurrence.Relative(0, IntervalUnit.DAYS),
                date("2026-10-01"),
                date("2026-10-01"),
            ),
        )
        assertEquals(
            date("2026-10-02"),
            nextDueDate(
                Recurrence.FixedInterval(date("2026-10-01"), -5, IntervalUnit.DAYS),
                date("2026-10-01"),
                date("2026-10-01"),
            ),
        )
    }

    // --- Matrix row: Overdue task ---------------------------------------------
    // nextDue Oct 1, today Oct 5 -> DueState.Overdue

    @Test
    fun dueState_classifiesOverdueDueTodayAndUpcoming() {
        val today = date("2026-10-05")
        assertEquals(DueState.OVERDUE, dueStateFor(date("2026-10-01"), today))
        assertEquals(DueState.OVERDUE, dueStateFor(date("2026-10-04"), today))
        assertEquals(DueState.DUE_TODAY, dueStateFor(today, today))
        assertEquals(DueState.UPCOMING, dueStateFor(date("2026-10-06"), today))
    }

    @Test
    fun dueState_onlyOverdueAndDueTodayAreActionable() {
        assertEquals(true, DueState.OVERDUE.isActionable)
        assertEquals(true, DueState.DUE_TODAY.isActionable)
        assertEquals(false, DueState.UPCOMING.isActionable)
    }
}
