package com.chorereminder.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.chorereminder.domain.WidgetFilter
import com.chorereminder.ui.theme.ChoreReminderTheme
import kotlinx.coroutines.launch

/** What the screen pre-fills with -- either this instance's saved settings, or the defaults for a brand new one. */
private data class WidgetSettingsUi(
    val includeUpcoming: Boolean,
    val showIcons: Boolean,
    val compactRows: Boolean,
    val opacityPercent: Int,
    /** True once this glance id already has saved state -- i.e. "Edit widget", not first placement. */
    val isReconfigure: Boolean,
)

/**
 * Appearance settings for one widget instance, opened both when a widget is
 * first placed and later via "Edit widget" (widgetFeatures=reconfigurable). On
 * reconfigure, the screen pre-fills with this instance's current settings
 * rather than resetting it back to defaults.
 */
class ChoreWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            ChoreReminderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var initial by remember { mutableStateOf<WidgetSettingsUi?>(null) }
                    LaunchedEffect(Unit) { initial = loadExistingSettings() }
                    initial?.let { settings ->
                        ChoreWidgetConfigScreen(
                            initial = settings,
                            onSave = { filter, showIcons, compactRows, opacity ->
                                lifecycleScope.launch { saveAndFinish(filter, showIcons, compactRows, opacity) }
                            },
                            onCancel = { finish() },
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadExistingSettings(): WidgetSettingsUi {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(this, ChoreWidget().stateDefinition, glanceId)
        return WidgetSettingsUi(
            includeUpcoming = ChoreWidgetPrefs.filterOf(prefs) == WidgetFilter.INCLUDE_UPCOMING,
            showIcons = prefs[ChoreWidgetPrefs.SHOW_ICONS] ?: ChoreWidgetPrefs.DEFAULT_SHOW_ICONS,
            compactRows = prefs[ChoreWidgetPrefs.COMPACT_ROWS] ?: ChoreWidgetPrefs.DEFAULT_COMPACT_ROWS,
            opacityPercent = prefs[ChoreWidgetPrefs.OPACITY_PERCENT] ?: ChoreWidgetPrefs.DEFAULT_OPACITY_PERCENT,
            // SHOW_ICONS is written on every save, so its presence means this glance id was configured before.
            isReconfigure = prefs[ChoreWidgetPrefs.SHOW_ICONS] != null,
        )
    }

    private suspend fun saveAndFinish(
        filter: WidgetFilter,
        showIcons: Boolean,
        compactRows: Boolean,
        opacityPercent: Int,
    ) {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(this, ChoreWidget().stateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[ChoreWidgetPrefs.FILTER] = filter.name
                this[ChoreWidgetPrefs.SHOW_ICONS] = showIcons
                this[ChoreWidgetPrefs.COMPACT_ROWS] = compactRows
                this[ChoreWidgetPrefs.OPACITY_PERCENT] = opacityPercent
            }
        }
        ChoreWidget().update(this, glanceId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoreWidgetConfigScreen(
    initial: WidgetSettingsUi,
    onSave: (WidgetFilter, Boolean, Boolean, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var includeUpcoming by remember { mutableStateOf(initial.includeUpcoming) }
    var showIcons by remember { mutableStateOf(initial.showIcons) }
    var compactRows by remember { mutableStateOf(initial.compactRows) }
    var opacity by remember { mutableStateOf(initial.opacityPercent.toFloat()) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Widget settings") })
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            SettingRow("Also show upcoming chores", "Include chores due in the next 7 days, not just due/overdue") {
                Switch(checked = includeUpcoming, onCheckedChange = { includeUpcoming = it })
            }
            SettingRow("Show icons", "Display each chore's icon in the list") {
                Switch(checked = showIcons, onCheckedChange = { showIcons = it })
            }
            SettingRow("Compact rows", "Hide the due-date line to fit more chores") {
                Switch(checked = compactRows, onCheckedChange = { compactRows = it })
            }
            Spacer(Modifier.height(8.dp))
            Text("Background opacity", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = ChoreWidget.MIN_OPACITY_PERCENT.toFloat()..100f,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        val filter = if (includeUpcoming) WidgetFilter.INCLUDE_UPCOMING else WidgetFilter.DUE_AND_OVERDUE
                        onSave(filter, showIcons, compactRows, opacity.toInt())
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (initial.isReconfigure) "Save" else "Add widget") }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}
