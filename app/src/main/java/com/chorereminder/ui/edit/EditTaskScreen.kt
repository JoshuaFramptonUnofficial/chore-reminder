package com.chorereminder.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.chorereminder.ui.GlassAlertDialog as AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.chorereminder.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.data.RecurrenceKind
import com.chorereminder.domain.IntervalUnit
import com.chorereminder.ui.TaskIconBadge
import com.chorereminder.ui.formatDate
import com.chorereminder.ui.theme.LiquidPillButton
import com.chorereminder.ui.theme.glassPanel
import com.chorereminder.ui.theme.glassSource
import com.chorereminder.ui.theme.glassTint
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.rememberHazeState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalLocale

/** FR-1/FR-2/FR-4: create or edit a task. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTaskScreen(
    viewModel: EditTaskViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }
    val defaultVisibility by viewModel.defaultVisibility.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (!state.busy) {
            if (viewModel.hasUnsavedChanges) confirmDiscard = true else onDone()
        }
    }
    BackHandler(onBack = requestBack)
    val hazeState = rememberHazeState()
    val pillBackdrop = rememberLayerBackdrop()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // Glass app bar to match Home/Complete: the form scrolls underneath.
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit chore" else "New chore") },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditing && !state.missing) {
                        IconButton(onClick = { confirmDelete = true }, enabled = state.loaded && !state.busy) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete chore",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                modifier = Modifier.glassPanel(hazeState, glassTint),
            )
        },
        floatingActionButton = {
            if (!state.missing) {
                LiquidPillButton(
                    onClick = viewModel::save,
                    backdrop = pillBackdrop,
                    enabled = state.canSave,
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_save), contentDescription = null)
                    Text(
                        if (state.saving) "Saving..." else if (state.deleting) "Deleting..."
                        else if (state.isEditing) "Save" else "Add chore",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Match Home: capture the whole viewport, including under Save.
                // Insets belong to scrolling content, not the sampled layer's bounds.
                // Keep capture outside verticalScroll so reflections track scrolling.
                .glassSource(hazeState)
                .layerBackdrop(pillBackdrop)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.loaded) {
                CircularProgressIndicator(Modifier.padding(24.dp))
                return@Column
            }
            if (state.missing) {
                Text("That chore no longer exists.", modifier = Modifier.padding(vertical = 24.dp))
                TextButton(onClick = onDone) { Text("Back to chores") }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskIconBadge(
                    iconType = state.iconType,
                    iconValue = state.iconValue,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    diameter = 52.dp,
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { nameTouched = true; viewModel.setName(it) },
                    label = { Text("Name") },
                    placeholder = { Text("Change bed sheets") },
                    singleLine = true,
                    isError = nameTouched && state.name.isBlank(),
                    supportingText = { if (state.name.isBlank()) Text("Enter a chore name.") },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel("Icon")
            IconPicker(
                selectedType = state.iconType,
                selectedValue = state.iconValue,
                onSelect = viewModel::setIcon,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { viewModel.setCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.newCategoryName,
                    onValueChange = viewModel::setNewCategoryName,
                    label = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { viewModel.addCategory() },
                    enabled = state.newCategoryName.isNotBlank(),
                ) { Text("Add") }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("Repeats")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceKindChip(
                    state.kind, RecurrenceKind.RELATIVE,
                    "After completing", viewModel::setKind,
                )
                RecurrenceKindChip(
                    state.kind, RecurrenceKind.FIXED_INTERVAL,
                    "Fixed schedule", viewModel::setKind,
                )
                RecurrenceKindChip(
                    state.kind, RecurrenceKind.FIXED_WEEKDAY,
                    "Weekday", viewModel::setKind,
                )
                RecurrenceKindChip(
                    state.kind, RecurrenceKind.ONE_OFF,
                    "Doesn't repeat", viewModel::setKind,
                )
            }

            Text(
                text = when (state.kind) {
                    RecurrenceKind.RELATIVE ->
                        "Next due is counted from the day you mark it done."
                    RecurrenceKind.FIXED_INTERVAL ->
                        "Stays on its calendar schedule no matter when you finish it."
                    RecurrenceKind.FIXED_WEEKDAY ->
                        "Falls due on the same weekday every week."
                    RecurrenceKind.ONE_OFF ->
                        "A single task -- mark it done once, with undo available."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state.kind) {
                RecurrenceKind.FIXED_WEEKDAY -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = state.dayOfWeek == day,
                                onClick = { viewModel.setDayOfWeek(day) },
                                label = {
                                    Text(day.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale))
                                },
                            )
                        }
                    }
                }

                RecurrenceKind.ONE_OFF -> {
                    DueDatePicker(
                        label = "Due",
                        date = state.anchor,
                        onPick = viewModel::setAnchor,
                    )
                }

                else -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.intervalText,
                            onValueChange = viewModel::setIntervalText,
                            label = { Text("Every") },
                            singleLine = true,
                            isError = state.interval == null,
                            modifier = Modifier.width(110.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IntervalUnit.entries.forEach { unit ->
                                FilterChip(
                                    selected = state.unit == unit,
                                    onClick = { viewModel.setUnit(unit) },
                                    label = { Text(unit.name.lowercase()) },
                                )
                            }
                        }
                    }

                    if (state.kind == RecurrenceKind.FIXED_INTERVAL) {
                        DueDatePicker(
                            label = "Starting",
                            date = state.anchor,
                            onPick = viewModel::setAnchor,
                        )
                    }
                }
            }

            SectionLabel("Upcoming chore visibility")
            Row {
                FilterChip(selected = state.visibility == null, onClick = { viewModel.setVisibility(null) }, label = { Text("Default") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = state.visibility != null, onClick = { if (state.visibility == null) viewModel.setVisibility(defaultVisibility) }, label = { Text("Custom") })
            }
            state.visibility?.let { com.chorereminder.ui.VisibilityEditor(it, viewModel::setVisibility) }
                ?: Text("Uses the default visibility rule in Settings.", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(96.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Your changes to this chore have not been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    // FR-3: deletion requires a confirmation step.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this chore?") },
            text = {
                Text("\"${state.name}\" and its completion history will be permanently removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecurrenceKindChip(
    current: RecurrenceKind,
    value: RecurrenceKind,
    label: String,
    onSelect: (RecurrenceKind) -> Unit,
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

/**
 * A date field paired with a Material3 date picker dialog. Replaces the old
 * "Today"-only assist chip so a fixed schedule's anchor -- or a one-off task's due
 * date -- can be set to any day, not just today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DueDatePicker(
    label: String,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label ${formatDate(date)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        AssistChip(
            onClick = { showPicker = true },
            label = { Text("Change") },
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
