package com.chorereminder.ui.complete

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.ui.TaskIconBadge
import com.chorereminder.ui.formatDate
import com.chorereminder.ui.formatDueLabel
import com.chorereminder.ui.theme.glassPanel
import com.chorereminder.ui.theme.glassSource
import com.chorereminder.ui.theme.glassTint
import dev.chrisbanes.haze.rememberHazeState
import java.time.LocalDate

/**
 * FR-10: the minimal confirmation view a notification tap lands on. One prominent
 * confirm button, plus a way to leave without changing anything.
 *
 * This is the screen the PRD explicitly calls out for glass treatment (FR-19), so
 * the card is a real Haze backdrop blur over a tinted background.
 */
@Composable
fun CompleteScreen(
    viewModel: CompleteViewModel,
    onFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val today = LocalDate.now()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Background is the blur source for the card floating over it.
            .glassSource(hazeState),
        contentAlignment = Alignment.Center,
    ) {
        BackdropWash()

        when (val s = state) {
            is CompleteUiState.Loading -> CircularProgressIndicator()

            is CompleteUiState.Missing -> {
                ConfirmCard(hazeState) {
                    Text(
                        "That chore no longer exists.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }

            is CompleteUiState.Ready -> {
                val view = s.task
                ConfirmCard(hazeState) {
                    TaskIconBadge(
                        iconType = view.task.iconType,
                        iconValue = view.task.iconValue,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        diameter = 64.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = view.task.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatDueLabel(view.nextDue, view.dueState, today),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    view.lastCompletedOn?.let {
                        Text(
                            text = "Last done: ${formatDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Did you complete this task?",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.confirm(onFinished) },
                        enabled = !s.saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("Mark Complete", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Not yet")
                    }
                }
            }
        }
    }
}

/** Subtle colour wash so the glass card has something to blur against. */
@Composable
private fun BackdropWash() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(140.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(120.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun ConfirmCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .clip(shape)
            .glassPanel(hazeState, glassTint, blurRadius = 28.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}
