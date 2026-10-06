package com.chorereminder.ui.edit

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
import androidx.compose.material3.AlertDialog
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
import java.util.Locale

/** FR-1/FR-2/FR-4: create or edit a task. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTaskScreen(
    viewModel: EditTaskViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val pillBackdrop = rememberLayerBackdrop()

    Scaffold(
        topBar = {
            // Glass app bar to match Home/Complete: the form scrolls underneath.
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit chore" else "New chore") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
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
            if (state.canSave) {
                LiquidPillButton(
                    onClick = { viewModel.save(onDone) },
                    backdrop = pillBackdrop,
                ) {
                    Text(
                        if (state.isEditing) "Save changes" else "Add chore",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .glassSource(hazeState)
                .layerBackdrop(pillBackdrop)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                    onValueChange = viewModel::setName,
                    label = { Text("Name") },
                    placeholder = { Text("Change bed sheets") },
                    singleLine = true,
                    isError = state.name.isBlank(),
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
                        "A single task -- marking it done removes it for good."
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
                                    Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.intervalText,
                            onValueChange = viewModel::setIntervalText,
                            label = { Text("Every") },
                            singleLine = true,
                            isError = state.interval == null,
                            modifier = Modifier.width(110.dp),
                        )
                        Spacer(Modifier.width(8.dp))
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

            if (!state.canSave) {
                Spacer(Modifier.height(16.dp))
                // No valid recurrence/name yet -- a disabled flat button explains
                // why there's nothing to tap, since the FAB only appears once the
                // form is actually saveable.
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isEditing) "Save changes" else "Add chore") }
            }
            Spacer(Modifier.height(96.dp))
        }
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
                    viewModel.delete(onDone)
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
@Composable
private fun DueDatePicker(
    label: String,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label ${formatDate(date)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
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
