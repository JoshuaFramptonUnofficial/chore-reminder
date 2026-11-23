package com.chorereminder.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.chorereminder.data.TaskView
import com.chorereminder.domain.nextDueDate
import com.chorereminder.ui.theme.glassCardTint
import java.time.LocalDate

/**
 * Confirms completing a task before it's due. Shared by Home (completing a card
 * directly) and the notification-tap confirmation screen. The message is computed
 * from the same [nextDueDate] math the repository will use, so the number shown
 * here is exactly what the task will reschedule to.
 */
@Composable
fun EarlyCompletionDialog(
    task: TaskView,
    today: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val newNextDue = nextDueDate(
        recurrence = task.task.recurrence.toDomain(task.task.createdDate),
        createdDate = task.task.createdDate,
        lastCompletion = today,
        completedOccurrence = task.nextDue,
    )
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete early?") },
        text = {
            Text(
                formatEarlyCompletionMessage(
                    taskName = task.task.name,
                    kind = task.task.recurrence.kind,
                    currentDue = task.nextDue,
                    newNextDue = newNextDue,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Complete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not yet") }
        },
        containerColor = glassCardTint,
    )
}

/**
 * Confirms skipping an occurrence, with a direct path to delaying instead --
 * "are you sure" shouldn't be a dead end when the user really just wanted more time.
 */
@Composable
fun SkipConfirmDialog(
    task: TaskView,
    today: LocalDate,
    onConfirmSkip: () -> Unit,
    onDelayInstead: () -> Unit,
    onCancel: () -> Unit,
) {
    val newNextDue = nextDueDate(
        recurrence = task.task.recurrence.toDomain(task.task.createdDate),
        createdDate = task.task.createdDate,
        lastCompletion = today,
        completedOccurrence = task.nextDue,
    )
    GlassAlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Skip this chore?") },
        text = {
            Text(
                formatSkipMessage(
                    taskName = task.task.name,
                    kind = task.task.recurrence.kind,
                    newNextDue = newNextDue,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmSkip) { Text("Skip") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelayInstead) { Text("Delay instead") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
        containerColor = glassCardTint,
    )
}
