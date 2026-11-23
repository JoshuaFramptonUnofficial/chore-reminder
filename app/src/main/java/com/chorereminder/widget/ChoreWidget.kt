package com.chorereminder.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as UnitColorProvider
import com.chorereminder.ChoreApp
import com.chorereminder.MainActivity
import com.chorereminder.R
import com.chorereminder.data.IconType
import com.chorereminder.data.TaskView
import com.chorereminder.domain.DueState
import com.chorereminder.domain.WidgetFilter
import com.chorereminder.domain.selectWidgetTasks
import com.chorereminder.notify.Notifications
import com.chorereminder.ui.edit.iconResForKey
import com.chorereminder.ui.formatDelayedUntil
import com.chorereminder.ui.formatDueLabel
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate

/** Per-widget-instance settings, stored via Glance's own preferences state. */
object ChoreWidgetPrefs {
    val FILTER = androidx.datastore.preferences.core.stringPreferencesKey("filter")
    val SHOW_ICONS = androidx.datastore.preferences.core.booleanPreferencesKey("show_icons")
    val COMPACT_ROWS = androidx.datastore.preferences.core.booleanPreferencesKey("compact_rows")
    val OPACITY_PERCENT = androidx.datastore.preferences.core.intPreferencesKey("opacity_percent")

    const val DEFAULT_SHOW_ICONS = true
    const val DEFAULT_COMPACT_ROWS = false
    const val DEFAULT_OPACITY_PERCENT = 90
    val DEFAULT_FILTER = WidgetFilter.DUE_AND_OVERDUE

    fun filterOf(prefs: androidx.datastore.preferences.core.Preferences): WidgetFilter =
        prefs[FILTER]?.let { runCatching { WidgetFilter.valueOf(it) }.getOrNull() } ?: DEFAULT_FILTER
}

private object ChoreWidgetActionKeys {
    val TASK_ID = ActionParameters.Key<Long>("taskId")
    val REVISION = ActionParameters.Key<Long>("revision")
}

// Calibrated against real launcher grids, not round numbers: a typical 3-column
// placement on a phone measures ~235dp wide, which must clear the MEDIUM
// threshold or every normal placement falls back to the bare SMALL layout.
private val SMALL = androidx.compose.ui.unit.DpSize(100.dp, 40.dp)
private val MEDIUM = androidx.compose.ui.unit.DpSize(180.dp, 110.dp)
private val LARGE = androidx.compose.ui.unit.DpSize(250.dp, 250.dp)

/**
 * A list of due/overdue (optionally + upcoming) chores, resizable small/medium/
 * large. Settings (filter, icons, compact rows, background opacity) are per
 * widget instance, set from [ChoreWidgetConfigActivity].
 */
class ChoreWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ChoreApp).container
        val today = LocalDate.now()
        val defaultVisibility = container.repository.observeVisibility().first()
        val tasks = container.repository.getAllTasks(today)
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val filter = ChoreWidgetPrefs.filterOf(prefs)
            val showIcons = prefs[ChoreWidgetPrefs.SHOW_ICONS] ?: ChoreWidgetPrefs.DEFAULT_SHOW_ICONS
            val compact = prefs[ChoreWidgetPrefs.COMPACT_ROWS] ?: ChoreWidgetPrefs.DEFAULT_COMPACT_ROWS
            val opacity = (prefs[ChoreWidgetPrefs.OPACITY_PERCENT] ?: ChoreWidgetPrefs.DEFAULT_OPACITY_PERCENT)
                .coerceIn(MIN_OPACITY_PERCENT, 100)
            val rows = selectWidgetTasks(tasks, today, filter, defaultVisibility)
            val panelColor = scheme.surfaceContainerHighest.copy(alpha = opacity / 100f)

            GlanceTheme {
                WidgetContent(rows, today, showIcons, compact, panelColor, scheme)
            }
        }
    }

    companion object {
        const val MIN_OPACITY_PERCENT = 40
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(
    tasks: List<TaskView>,
    today: LocalDate,
    showIcons: Boolean,
    compact: Boolean,
    panelColor: Color,
    scheme: ColorScheme,
) {
    val size = LocalSize.current
    val isSmall = size.height < MEDIUM.height
    val isLarge = size.height >= LARGE.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(UnitColorProvider(panelColor))
            .cornerRadius(24.dp),
    ) {
        WidgetHeader(tasks.size, LocalContext.current, scheme)
        Divider(scheme)
        Box(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            if (tasks.isEmpty()) {
                EmptyWidget(scheme)
            } else if (isSmall) {
                SmallWidget(tasks.first(), today, scheme)
            } else {
                val limit = if (isLarge) 8 else 4
                ListWidget(tasks.take(limit), today, showIcons, compact, scheme)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetHeader(dueCount: Int, context: Context, scheme: ColorScheme) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity(openHomeIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_notification),
            contentDescription = null,
            colorFilter = androidx.glance.ColorFilter.tint(UnitColorProvider(scheme.onSurface)),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            "Chores",
            style = TextStyle(fontWeight = FontWeight.Bold, color = UnitColorProvider(scheme.onSurface)),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (dueCount > 0) {
            Text(
                dueCount.toString(),
                style = TextStyle(fontWeight = FontWeight.Medium, color = UnitColorProvider(scheme.onSurfaceVariant)),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun Divider(scheme: ColorScheme) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(UnitColorProvider(scheme.onSurface.copy(alpha = 0.1f))),
    ) {}
}

@androidx.compose.runtime.Composable
private fun EmptyWidget(scheme: ColorScheme) {
    Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Text(
            "All caught up",
            style = TextStyle(fontWeight = FontWeight.Medium, color = UnitColorProvider(scheme.onSurfaceVariant)),
        )
    }
}

@androidx.compose.runtime.Composable
private fun SmallWidget(top: TaskView, today: LocalDate, scheme: ColorScheme) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskIconBadge(top, scheme, diameter = 28.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                top.task.name, maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Medium, color = UnitColorProvider(scheme.onSurface)),
            )
            Text(statusLabel(top, today), maxLines = 1, style = statusStyle(top, scheme))
        }
    }
}

@androidx.compose.runtime.Composable
private fun ListWidget(tasks: List<TaskView>, today: LocalDate, showIcons: Boolean, compact: Boolean, scheme: ColorScheme) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
        var lastCategory: String? = null
        tasks.forEachIndexed { index, task ->
            if (task.categoryName != lastCategory) {
                lastCategory = task.categoryName
                if (index > 0) Spacer(GlanceModifier.height(4.dp))
                Text(
                    task.categoryName,
                    style = TextStyle(fontWeight = FontWeight.Bold, color = UnitColorProvider(scheme.primary)),
                    modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp, start = 8.dp),
                )
            }
            TaskRow(task, today, showIcons, compact, scheme)
            if (index < tasks.lastIndex && tasks[index + 1].categoryName == task.categoryName) {
                Divider(scheme)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun TaskRow(task: TaskView, today: LocalDate, showIcons: Boolean, compact: Boolean, scheme: ColorScheme) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 8.dp, horizontal = 8.dp)
            .clickable(actionStartActivity(openChoreIntent(context, task.task.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcons) {
            TaskIconBadge(task, scheme, diameter = if (compact) 28.dp else 34.dp)
            Spacer(GlanceModifier.width(10.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                task.task.name, maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Medium, color = UnitColorProvider(scheme.onSurface)),
            )
            if (!compact) Text(statusLabel(task, today), maxLines = 1, style = statusStyle(task, scheme))
        }
        Spacer(GlanceModifier.width(8.dp))
        CompleteButton(task, scheme)
    }
}

/** Circular tinted badge matching the in-app `TaskIconBadge` color-per-state treatment. */
@androidx.compose.runtime.Composable
private fun TaskIconBadge(task: TaskView, scheme: ColorScheme, diameter: androidx.compose.ui.unit.Dp) {
    val delayed = task.delayedUntil?.isAfter(Instant.now()) == true
    val (container, onContainer) = when {
        delayed -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        task.dueState == DueState.OVERDUE -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Box(
        modifier = GlanceModifier
            .size(diameter)
            .background(UnitColorProvider(container))
            .cornerRadius(diameter / 2),
        contentAlignment = Alignment.Center,
    ) {
        when (task.task.iconType) {
            IconType.EMOJI -> Text(
                task.task.iconValue,
                style = TextStyle(fontWeight = FontWeight.Medium, color = UnitColorProvider(onContainer)),
            )
            IconType.MATERIAL -> Image(
                provider = ImageProvider(iconResForKey(task.task.iconValue)),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(UnitColorProvider(onContainer)),
                modifier = GlanceModifier.size(diameter * 0.55f),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun CompleteButton(task: TaskView, scheme: ColorScheme) {
    Box(
        modifier = GlanceModifier
            .size(30.dp)
            .background(UnitColorProvider(scheme.secondaryContainer))
            .cornerRadius(15.dp)
            .clickable(
                actionRunCallback<CompleteChoreAction>(
                    androidx.glance.action.actionParametersOf(
                        ChoreWidgetActionKeys.TASK_ID to task.task.id,
                        ChoreWidgetActionKeys.REVISION to task.occurrenceRevision,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_chore_check_circle),
            contentDescription = "Mark ${task.task.name} complete",
            colorFilter = androidx.glance.ColorFilter.tint(UnitColorProvider(scheme.onSecondaryContainer)),
            modifier = GlanceModifier.size(18.dp),
        )
    }
}

private fun statusLabel(task: TaskView, today: LocalDate): String {
    val delayed = task.delayedUntil?.takeIf { it.isAfter(Instant.now()) }
    return delayed?.let { formatDelayedUntil(it, today) } ?: formatDueLabel(task.nextDue, task.dueState, today)
}

/** Tints the status line the same way `TaskCard` does: tertiary for delayed, error for overdue. */
private fun statusStyle(task: TaskView, scheme: ColorScheme): TextStyle {
    val delayed = task.delayedUntil?.isAfter(Instant.now()) == true
    val color = when {
        delayed -> scheme.tertiary
        task.dueState == DueState.OVERDUE -> scheme.error
        else -> scheme.onSurfaceVariant
    }
    return TextStyle(color = UnitColorProvider(color))
}

private fun openChoreIntent(context: Context, taskId: Long) = Intent(context, MainActivity::class.java).apply {
    action = Intent.ACTION_VIEW
    putExtra(Notifications.EXTRA_TASK_ID, taskId)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

private fun openHomeIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
}

/** The widget's own tick button -- completes without opening the app. */
class CompleteChoreAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[ChoreWidgetActionKeys.TASK_ID] ?: return
        val revision = parameters[ChoreWidgetActionKeys.REVISION] ?: return
        (context.applicationContext as ChoreApp).container.completeTask(taskId, revision)
    }
}
