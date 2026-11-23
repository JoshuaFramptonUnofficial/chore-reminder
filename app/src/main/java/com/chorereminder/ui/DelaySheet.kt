package com.chorereminder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chorereminder.domain.DelayOption
import com.chorereminder.ui.theme.glassCardTint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Shared duration picker for delaying a chore -- used from Home (swipe/long-press),
 * the notification-tap confirmation screen, and the Delay action on the notification
 * itself. One sheet, one place that knows the available durations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelaySheet(
    onSelect: (DelayOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = glassCardTint) {
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp)) {
            Text(
                "Delay this chore",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DelayOptionRow("1 hour") { onSelect(DelayOption.OneHour) }
            DelayOptionRow("3 hours") { onSelect(DelayOption.ThreeHours) }
            DelayOptionRow("Tomorrow") { onSelect(DelayOption.Tomorrow) }
            DelayOptionRow("Pick a date") { showDatePicker = true }
        }
    }

    if (showDatePicker) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onSelect(DelayOption.PickedDate(date))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun DelayOptionRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}
