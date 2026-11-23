package com.chorereminder.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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

private val SMALL = androidx.compose.ui.unit.DpSize(110.dp, 90.dp)
private val MEDIUM = androidx.compose.ui.unit.DpSize(250.dp, 160.dp)
private val LARGE = androidx.compose.ui.unit.DpSize(250.dp, 320.dp)

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
            val backgroundColor = scheme.surfaceContainerHighest.copy(alpha = opacity / 100f)

            GlanceTheme {
                WidgetContent(rows, today, showIcons, compact, backgroundColor)
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
    backgroundColor: Color,
) {
    val size = LocalSize.current
    val isSmall = size.height < MEDIUM.height
    val isLarge = size.height >= LARGE.height

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(UnitColorProvider(backgroundColor))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(openHomeIntent(LocalContext.current))),
    ) {
        if (tasks.isEmpty()) {
            EmptyWidget()
        } else if (isSmall) {
            SmallWidget(tasks, today)
        } else {
            val limit = if (isLarge) 8 else 4
            ListWidget(tasks.take(limit), today, showIcons, compact)
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptyWidget() {
    Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Text("All caught up", style = TextStyle(fontWeight = FontWeight.Medium))
    }
}

@androidx.compose.runtime.Composable
private fun SmallWidget(tasks: List<TaskView>, today: LocalDate) {
    val top = tasks.first()
    Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        Text(
            if (tasks.size == 1) "1 chore due" else "${tasks.size} chores due",
            style = TextStyle(fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(top.task.name, style = TextStyle(fontWeight = FontWeight.Medium), maxLines = 1)
        Text(statusLabel(top, today))
    }
}

@androidx.compose.runtime.Composable
private fun ListWidget(tasks: List<TaskView>, today: LocalDate, showIcons: Boolean, compact: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp)) {
        var lastCategory: String? = null
        tasks.forEach { task ->
            if (task.categoryName != lastCategory) {
                lastCategory = task.categoryName
                Text(
                    task.categoryName,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp, start = 4.dp),
                )
            }
            TaskRow(task, today, showIcons, compact)
        }
    }
}

@androidx.compose.runtime.Composable
private fun TaskRow(task: TaskView, today: LocalDate, showIcons: Boolean, compact: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 6.dp, horizontal = 4.dp)
            .clickable(actionStartActivity(openChoreIntent(context, task.task.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcons) {
            TaskRowIcon(task)
            Spacer(GlanceModifier.width(8.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(task.task.name, maxLines = 1, style = TextStyle(fontWeight = FontWeight.Medium))
            if (!compact) Text(statusLabel(task, today), maxLines = 1)
        }
        Spacer(GlanceModifier.width(8.dp))
        Image(
            provider = ImageProvider(R.drawable.ic_chore_check_circle),
            contentDescription = "Mark ${task.task.name} complete",
            modifier = GlanceModifier
                .size(28.dp)
                .clickable(
                    actionRunCallback<CompleteChoreAction>(
                        androidx.glance.action.actionParametersOf(
                            ChoreWidgetActionKeys.TASK_ID to task.task.id,
                            ChoreWidgetActionKeys.REVISION to task.occurrenceRevision,
                        ),
                    ),
                ),
        )
    }
}

@androidx.compose.runtime.Composable
private fun TaskRowIcon(task: TaskView) {
    when (task.task.iconType) {
        IconType.EMOJI -> Text(task.task.iconValue, style = TextStyle(fontWeight = FontWeight.Medium))
        IconType.MATERIAL -> Image(
            provider = ImageProvider(iconResForKey(task.task.iconValue)),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
        )
    }
}

private fun statusLabel(task: TaskView, today: LocalDate): String {
    val delayed = task.delayedUntil?.takeIf { it.isAfter(Instant.now()) }
    return delayed?.let { formatDelayedUntil(it, today) } ?: formatDueLabel(task.nextDue, task.dueState, today)
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
