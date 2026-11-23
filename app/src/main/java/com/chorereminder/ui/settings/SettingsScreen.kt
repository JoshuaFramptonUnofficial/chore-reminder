package com.chorereminder.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.chorereminder.data.UiPreferences
import com.chorereminder.ui.theme.LocalLiquidGlassBlur
import com.chorereminder.ui.theme.liquidPillSurface
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import com.chorereminder.ui.GlassAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.data.CategoryWithCount
import com.chorereminder.data.UNCATEGORIZED_ID
import com.chorereminder.notify.Notifications

/** FR-15/FR-16 plus the notifications-denied explainer from the I/O matrix. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val glassBlur by viewModel.glassBlur.collectAsStateWithLifecycle()
    val followCompleted by viewModel.followCompleted.collectAsStateWithLifecycle()
    val visibility by viewModel.visibility.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    // SAF: the user picks the destination, so no storage permission is needed.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> pendingImport = uri }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all chores?") },
            text = { Text("Import replaces your chores, categories and completion history. Export a backup first if you want to keep them.") },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; viewModel.import(uri) }) { Text("Replace and import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection("Appearance") {
                GlassBlurSetting(glassBlur, viewModel::setGlassBlur)
            }
            SettingsSection("Chore behaviour") {
                Text("Default chore visibility", style = MaterialTheme.typography.titleMedium)
                com.chorereminder.ui.VisibilityEditor(visibility, viewModel::setVisibility)
                Text("Applies to chores using Default. Custom chore rules are unchanged.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().toggleable(value = followCompleted, role = Role.Switch,
                    onValueChange = viewModel::setFollowCompleted), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Follow completed chores", style = MaterialTheme.typography.titleMedium)
                        Text("Scroll to a chore after completion or undo. Hidden chores are never followed.", style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(checked = followCompleted, onCheckedChange = null)
                }
            }
            SettingsSection("Categories") {
                CategoriesSection(categories, viewModel::addCategory, viewModel::renameCategory, viewModel::deleteCategory, enabled = !busy)
            }
            SettingsSection("Notifications") {
                NotificationStatusCard(context)
            }
            SettingsSection("Backup & restore") {
                Text("Export saves your chores, categories, completion history and visibility rules to a JSON file.", style = MaterialTheme.typography.bodyMedium)
                Text("Import replaces the data currently in the app. Export first if you want to keep it.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { exportLauncher.launch(viewModel.suggestedFileName()) }, enabled = !busy) { Text("Export") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, enabled = !busy) { Text("Import") }
                }
                if (busy) {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(operation ?: "Working...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            SettingsSection("About") {
                val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                Text("Chore Reminder", style = MaterialTheme.typography.titleMedium)
                Text("Version ${version ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                Text("Works entirely on this device. No account, internet permission or sync.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Preview uses the same capture/effect as real controls; only the pending slider value is local. */
@Composable
private fun GlassBlurSetting(blur: Int, onSave: (Int) -> Unit) {
    var sliderValue by remember(blur) { mutableFloatStateOf(blur.toFloat()) }
    val selected = sliderValue.roundToInt()
    val previewBackdrop = rememberLayerBackdrop()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Liquid Glass blur", style = MaterialTheme.typography.titleMedium)
                Text("$selected / ${UiPreferences.MAX_GLASS_BLUR}" +
                    if (selected == UiPreferences.DEFAULT_GLASS_BLUR) " (recommended)" else "",
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(enabled = selected != UiPreferences.DEFAULT_GLASS_BLUR, onClick = {
                sliderValue = UiPreferences.DEFAULT_GLASS_BLUR.toFloat()
                onSave(UiPreferences.DEFAULT_GLASS_BLUR)
            }) { Text("Reset") }
        }
        Text("Adjust glass buttons and confirmation dialogs.", style = MaterialTheme.typography.bodySmall)
        val scheme = MaterialTheme.colorScheme
        CompositionLocalProvider(LocalLiquidGlassBlur provides sliderValue.dp) {
            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp)).clearAndSetSemantics { },
                contentAlignment = Alignment.Center) {
                // Both colour fields and the fine edges cross underneath the glass.
                Canvas(Modifier.fillMaxSize().layerBackdrop(previewBackdrop)) {
                    drawRect(scheme.primaryContainer, size = Size(size.width / 2, size.height))
                    drawRect(scheme.tertiaryContainer, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
                    val spacing = 16.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(scheme.onSurface.copy(alpha = 0.45f), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 2.dp.toPx())
                        x += spacing
                    }
                }
                Box(Modifier.fillMaxWidth(0.8f).height(64.dp)
                    .liquidPillSurface(previewBackdrop, scheme.primary))
            }
        }
        androidx.compose.material3.Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSave(sliderValue.roundToInt()) },
            valueRange = UiPreferences.MIN_GLASS_BLUR.toFloat()..UiPreferences.MAX_GLASS_BLUR.toFloat(),
            steps = UiPreferences.MAX_GLASS_BLUR - UiPreferences.MIN_GLASS_BLUR - 1,
            modifier = Modifier.semantics { contentDescription = "Liquid Glass blur" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Less blur", style = MaterialTheme.typography.bodySmall)
            Text("More blur", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * I/O matrix, "Notifications denied": the app stays usable and Settings explains
 * that reminders are off, with a shortcut to the system toggle.
 */
@Composable
private fun NotificationStatusCard(context: android.content.Context) {
    var enabled by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleResumeEffect(context) {
        enabled = Notifications.canPost(context)
        onPauseOrDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (enabled) "Reminders are on" else "Reminders are off", style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        Text(if (enabled) "Due and overdue chores remind you daily. Android controls delivery timing."
            else "Notifications are disabled for this app or its reminder channel. You can still manage and complete chores.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Open notification settings") }
    }
}

/** Rename and remove categories from one place -- chores are never deleted by this. */
@Composable
private fun CategoriesSection(
    categories: List<CategoryWithCount>,
    onAdd: (String, () -> Unit) -> Unit,
    onRename: (Long, String, (Boolean) -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    enabled: Boolean,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { row ->
            CategoryRow(row, { renamingId = row.category.id }, { deletingId = row.category.id }, enabled)
            HorizontalDivider()
        }
        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New category") },
            singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = {
            val submitted = newName
            onAdd(submitted) { if (newName == submitted) newName = "" }
        }, enabled = enabled && newName.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Text("Add category") }
    }
    categories.firstOrNull { it.category.id == renamingId }?.let { row ->
        RenameCategoryDialog(row, { renamingId = null }, { name -> onRename(row.category.id, name) { ok -> if (ok) renamingId = null } })
    }
    categories.firstOrNull { it.category.id == deletingId }?.let { row ->
        DeleteCategoryDialog(row, { deletingId = null }, { onDelete(row.category.id); deletingId = null })
    }
}

@Composable
private fun CategoryRow(
    row: CategoryWithCount,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    val isUncategorized = row.category.id == UNCATEGORIZED_ID
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.category.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (row.choreCount == 1) "1 chore" else "${row.choreCount} chores",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isUncategorized) {
            IconButton(onClick = onRename, enabled = enabled) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename ${row.category.name}")
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete ${row.category.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RenameCategoryDialog(
    row: CategoryWithCount,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(row.category.id) { mutableStateOf(row.category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteCategoryDialog(
    row: CategoryWithCount,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${row.category.name}\"?") },
        text = {
            Text(
                if (row.choreCount > 0) {
                    "${row.choreCount} ${if (row.choreCount == 1) "chore" else "chores"} will " +
                        "move to Uncategorized. The chores themselves are kept."
                } else {
                    "This category has no chores in it."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
