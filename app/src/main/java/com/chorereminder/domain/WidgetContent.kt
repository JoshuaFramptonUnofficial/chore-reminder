package com.chorereminder.domain

import com.chorereminder.data.TaskView
import com.chorereminder.data.VisibilitySpec
import java.time.LocalDate

/** Widget's "show upcoming too" setting -- the other axis is always due/overdue. */
enum class WidgetFilter { DUE_AND_OVERDUE, INCLUDE_UPCOMING }

/** How many days out an upcoming chore still counts as "coming up" in the widget. */
private const val UPCOMING_WINDOW_DAYS = 7L

/**
 * Same ordering and visibility rule as the home screen (soonest due first, then
 * name), trimmed to what the chosen [filter] and [limit] allow. Kept free of
 * Android/Glance types so it's testable as plain Kotlin.
 */
fun selectWidgetTasks(
    tasks: List<TaskView>,
    today: LocalDate,
    filter: WidgetFilter,
    defaultVisibility: VisibilitySpec,
    limit: Int = 25,
): List<TaskView> =
    tasks
        .filter { (it.task.visibility ?: defaultVisibility).isVisible(today, it.nextDue, it.lastCompletedOn) }
        .filter { view ->
            when (filter) {
                WidgetFilter.DUE_AND_OVERDUE -> view.dueState != DueState.UPCOMING
                WidgetFilter.INCLUDE_UPCOMING ->
                    view.dueState != DueState.UPCOMING || view.nextDue <= today.plusDays(UPCOMING_WINDOW_DAYS)
            }
        }
        .sortedWith(compareBy({ it.nextDue }, { it.task.name.lowercase() }))
        .take(limit)
