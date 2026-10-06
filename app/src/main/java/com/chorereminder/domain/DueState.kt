package com.chorereminder.domain

import java.time.LocalDate

/**
 * A task's status relative to today. Derived from the next-due date on every read --
 * never stored, so it can't go stale while the app sits in the background overnight.
 */
enum class DueState {
    UPCOMING,
    DUE_TODAY,
    OVERDUE,
    ;

    val isActionable: Boolean get() = this != UPCOMING
}

fun dueStateFor(nextDue: LocalDate, today: LocalDate): DueState = when {
    nextDue.isBefore(today) -> DueState.OVERDUE
    nextDue.isEqual(today) -> DueState.DUE_TODAY
    else -> DueState.UPCOMING
}
