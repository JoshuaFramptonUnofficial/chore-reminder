package com.chorereminder.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.data.CategoryGroup
import com.chorereminder.data.TaskView
import com.chorereminder.domain.DueState
import com.chorereminder.ui.TaskIconBadge
import com.chorereminder.ui.formatDueLabel
import com.chorereminder.ui.formatRecurrence
import com.chorereminder.ui.theme.LiquidPillButton
import com.chorereminder.ui.theme.glassCard
import com.chorereminder.ui.theme.glassPanel
import com.chorereminder.ui.theme.glassSource
import com.chorereminder.ui.theme.glassTint
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.rememberHazeState
import java.time.LocalDate

/**
 * FR-13: the app's default view. Tasks grouped by category, soonest-due first
 * within each group, overdue items visually distinguished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddTask: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val pillBackdrop = rememberLayerBackdrop()
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            // Glass app bar: the list scrolls underneath and blurs through it.
            TopAppBar(
                title = { Text("Chores", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
            LiquidPillButton(onClick = onAddTask, backdrop = pillBackdrop) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("New chore", fontWeight = FontWeight.Medium)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Source layer: this is the content the app bar blurs.
                .glassSource(hazeState)
                // Also the content the liquid-glass pill refracts (FR-19 follow-up).
                .layerBackdrop(pillBackdrop),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                item(key = "header-${group.categoryId}") {
                    CategoryHeader(group, Modifier.animateItem())
                }
                items(group.tasks, key = { it.task.id }) { task ->
                    TaskCard(
                        task = task,
                        today = today,
                        onClick = { onOpenTask(task.task.id) },
                        onComplete = { viewModel.complete(task.task.id) },
                        // Completing a task re-sorts the list; animate the move
                        // instead of letting rows jump.
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "gap-${group.categoryId}") {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(group: CategoryGroup, modifier: Modifier = Modifier) {
    val overdue = group.tasks.count { it.dueState == DueState.OVERDUE }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.categoryName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (overdue > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$overdue overdue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * A single chore. Tapping opens detail; the trailing button marks complete
 * in place (FR-14). Overdue cards get an error-toned border and label (FR-13).
 */
@Composable
private fun TaskCard(
    task: TaskView,
    today: LocalDate,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOverdue = task.dueState == DueState.OVERDUE
    val isDueToday = task.dueState == DueState.DUE_TODAY

    val accent by animateColorAsState(
        targetValue = when {
            isOverdue -> MaterialTheme.colorScheme.error
            isDueToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "accent",
    )
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .glassCard(shape)
            .then(
                if (isOverdue) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f), shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskIconBadge(
                iconType = task.task.iconType,
                iconValue = task.task.iconValue,
                containerColor = if (isOverdue) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (isOverdue) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = task.task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatDueLabel(task.nextDue, task.dueState, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    fontWeight = if (task.dueState.isActionable) FontWeight.Medium else FontWeight.Normal,
                )
                Text(
                    text = formatRecurrence(task.task.recurrence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onComplete,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Mark ${task.task.name} complete",
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("No chores yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap + to add your first recurring chore.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
