package com.chorereminder.domain

import com.chorereminder.data.RecurrenceKind
import com.chorereminder.data.RecurrenceSpec
import com.chorereminder.data.TaskEntity
import com.chorereminder.data.TaskView
import com.chorereminder.data.VisibilitySpec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetContentTest {
    private val today = LocalDate.parse("2026-11-13")

    private fun view(
        id: Long,
        name: String,
        nextDue: LocalDate,
        dueState: DueState,
        visibility: VisibilitySpec? = null,
    ) = TaskView(
        task = TaskEntity(
            id = id, name = name, createdDate = nextDue.minusDays(30),
            visibility = visibility,
            recurrence = RecurrenceSpec(RecurrenceKind.RELATIVE, interval = 7),
        ),
        categoryName = "Uncategorized",
        nextDue = nextDue,
        dueState = dueState,
        lastCompletedOn = null,
        completionCount = 0,
    )

    @Test fun dueAndOverdue_excludesUpcoming() {
        val tasks = listOf(
            view(1, "Overdue", today.minusDays(1), DueState.OVERDUE),
            view(2, "Due today", today, DueState.DUE_TODAY),
            view(3, "Later", today.plusDays(3), DueState.UPCOMING),
        )
        val result = selectWidgetTasks(tasks, today, WidgetFilter.DUE_AND_OVERDUE, VisibilitySpec())
        assertEquals(listOf("Overdue", "Due today"), result.map { it.task.name })
    }

    @Test fun includeUpcoming_addsOnlyWithinSevenDays() {
        val tasks = listOf(
            view(1, "Due today", today, DueState.DUE_TODAY),
            view(2, "Soon", today.plusDays(5), DueState.UPCOMING),
            view(3, "Far", today.plusDays(30), DueState.UPCOMING),
        )
        val result = selectWidgetTasks(tasks, today, WidgetFilter.INCLUDE_UPCOMING, VisibilitySpec())
        assertEquals(listOf("Due today", "Soon"), result.map { it.task.name })
    }

    @Test fun sortsBySoonestDueThenName() {
        val tasks = listOf(
            view(1, "Zebra", today, DueState.DUE_TODAY),
            view(2, "Apple", today, DueState.DUE_TODAY),
            view(3, "Overdue one", today.minusDays(2), DueState.OVERDUE),
        )
        val result = selectWidgetTasks(tasks, today, WidgetFilter.DUE_AND_OVERDUE, VisibilitySpec())
        assertEquals(listOf("Overdue one", "Apple", "Zebra"), result.map { it.task.name })
    }

    @Test fun respectsPerTaskVisibility_likeHomeScreen() {
        val tasks = listOf(
            // BEFORE_DUE by 1 day: not visible yet three days out.
            view(1, "Hidden", today.plusDays(3), DueState.UPCOMING, visibility = VisibilitySpec(mode = "BEFORE_DUE", amount = 1, unit = "DAYS")),
            view(2, "Visible", today, DueState.DUE_TODAY),
        )
        val result = selectWidgetTasks(tasks, today, WidgetFilter.INCLUDE_UPCOMING, VisibilitySpec())
        assertEquals(listOf("Visible"), result.map { it.task.name })
    }

    @Test fun limitTrimsResults() {
        val tasks = (1..5).map { view(it.toLong(), "Task $it", today, DueState.DUE_TODAY) }
        val result = selectWidgetTasks(tasks, today, WidgetFilter.DUE_AND_OVERDUE, VisibilitySpec(), limit = 2)
        assertEquals(2, result.size)
    }
}
