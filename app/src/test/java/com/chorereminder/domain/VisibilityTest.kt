package com.chorereminder.domain

import com.chorereminder.data.VisibilitySpec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VisibilityTest {
    private val today = LocalDate.parse("2026-11-13")
    @Test fun immediatePreservesExistingBehavior() {
        assertTrue(VisibilitySpec().isVisible(today, today.plusYears(2), null))
    }
    @Test fun afterCompletionHasInclusiveBoundary_andNeverHidesDueChores() {
        val rule = VisibilitySpec("AFTER_COMPLETION", 7)
        assertFalse(rule.isVisible(today, today.plusDays(14), today.minusDays(6)))
        assertTrue(rule.isVisible(today, today.plusDays(14), today.minusDays(7)))
        assertTrue(rule.isVisible(today, today, today))
        assertTrue(rule.isVisible(today, today.minusDays(4), today))
    }
    @Test fun neverCompletedChoreIsVisibleWithAfterCompletion() {
        assertTrue(VisibilitySpec("AFTER_COMPLETION", 999).isVisible(today, today.plusDays(5), null))
    }
    @Test fun beforeDueUsesActualNextOccurrenceRegardlessOfRecurrenceType() {
        val rule = VisibilitySpec("BEFORE_DUE", 1, "WEEKS")
        assertFalse(rule.isVisible(today, today.plusDays(8), null))
        assertTrue(rule.isVisible(today, today.plusDays(7), null))
        assertTrue(rule.isVisible(today, today.minusDays(20), null))
    }
    @Test fun calendarMonthsClamp_andLargeOffsetsCannotPermanentlyHideDueTasks() {
        val rule = VisibilitySpec("BEFORE_DUE", 1, "MONTHS")
        assertTrue(rule.isVisible(LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31"), null))
        assertTrue(VisibilitySpec("AFTER_COMPLETION", 999, "MONTHS").isVisible(today, today, today))
    }
    @Test fun invalidBackupRulesAreRejected() {
        for (rule in listOf(VisibilitySpec("OTHER"), VisibilitySpec(amount = 0), VisibilitySpec(unit = "HOURS"))) {
            try { rule.validate(); fail("invalid rule accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
