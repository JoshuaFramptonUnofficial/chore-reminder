package com.chorereminder.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import com.chorereminder.ui.GlassAlertDialog as AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.chorereminder.R
import com.chorereminder.ui.CompletionBar
import com.chorereminder.ui.theme.progressiveHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.data.CategoryGroup
import com.chorereminder.data.TaskView
import com.chorereminder.domain.DelayOption
import com.chorereminder.domain.DueState
import com.chorereminder.domain.nextDueDate
import com.chorereminder.ui.DelaySheet
import com.chorereminder.ui.TaskIconBadge
import com.chorereminder.ui.formatDelayedUntil
import com.chorereminder.ui.formatDueLabel
import com.chorereminder.ui.formatEarlyCompletionMessage
import com.chorereminder.ui.formatRecurrence
import com.chorereminder.ui.theme.LiquidIconButton
import com.chorereminder.ui.theme.LiquidPillButton
import com.chorereminder.ui.theme.glassCard
import com.chorereminder.ui.theme.glassCardTint
import com.chorereminder.ui.theme.glassPanel
import com.chorereminder.ui.theme.glassSource
import com.chorereminder.ui.theme.glassTint
import com.chorereminder.ui.theme.rememberPressScale
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
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
    showCompletion: Boolean = true,
) {
    val homeData by viewModel.homeData.collectAsStateWithLifecycle()
    val groups = homeData.groups
    val listState = rememberLazyListState()
    val follow by viewModel.followCompleted.collectAsStateWithLifecycle()
    val change by viewModel.taskChange.collectAsStateWithLifecycle()
    val keys: List<Any> = groups.flatMap { group -> listOf<Any>("header-${group.categoryId}") + group.tasks.map { it.task.id } + listOf("gap-${group.categoryId}") }
    var previousKeys by remember { mutableStateOf(keys) }
    var previousRevisions by remember { mutableStateOf(emptyMap<Long, Long>()) }
    var consumedChange by remember { mutableStateOf(change.sequence) }
    val revisions = groups.flatMap { it.tasks }.associate { it.task.id to it.occurrenceRevision }
    SideEffect {
        val ready = change.sequence != consumedChange && (homeData.revisions[change.taskId] == change.revision)
        if (keys != previousKeys || revisions != previousRevisions || ready) {
            val target = keys.indexOf(change.taskId)
            if (follow && ready && target >= 0 && !listState.isScrollInProgress) {
                listState.requestScrollToItem(target)
            } else {
                val position = stablePosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, keys.size)
                listState.requestScrollToItem(position.index, position.offset)
            }
            previousKeys = keys
            previousRevisions = revisions
            if (ready) consumedChange = change.sequence
        }
    }
    val hazeState = rememberHazeState()
    val pillBackdrop = rememberLayerBackdrop()
    val headerBackdrop = rememberLayerBackdrop()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val includeHidden by viewModel.includeHidden.collectAsStateWithLifecycle()
    var pendingEarlyComplete by remember { mutableStateOf<TaskView?>(null) }
    var pendingDelay by remember { mutableStateOf<TaskView?>(null) }

    Scaffold(
        topBar = {
            // Capture only the already-blurred header background, never its controls.
            Box {
                Box(Modifier.matchParentSize().layerBackdrop(headerBackdrop).progressiveHeader(hazeState))
                TopAppBar(
                    title = { Text("Chores", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        LiquidIconButton(onClick = viewModel::toggleHidden, backdrop = headerBackdrop, diameter = 48.dp,
                            modifier = Modifier.semantics { stateDescription = if (includeHidden) "Hidden chores visible" else "Hidden chores concealed" }) {
                            Icon(painterResource(if (includeHidden) R.drawable.ic_eye else R.drawable.ic_eye_off),
                                contentDescription = if (includeHidden) "Hide hidden chores" else "Show hidden chores")
                        }
                        Spacer(Modifier.width(8.dp))
                        LiquidIconButton(onClick = onOpenSettings, backdrop = headerBackdrop, diameter = 48.dp) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        Spacer(Modifier.width(4.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .imePadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                // This allocation exists even while the confirmation is absent/animating.
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                    if (showCompletion) CompletionBar(viewModel.appContainer, pillBackdrop)
                }
                LiquidPillButton(onClick = onAddTask, backdrop = pillBackdrop) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New", fontWeight = FontWeight.Medium)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().glassSource(hazeState).layerBackdrop(pillBackdrop).padding(padding))
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Source layer: this is the content the app bar blurs.
                .glassSource(hazeState)
                // Also the content the liquid-glass pill refracts (FR-19 follow-up).
                .layerBackdrop(pillBackdrop),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
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
                        onComplete = {
                            // Completing something not yet due is surprising enough
                            // to double-check -- especially since it reschedules the
                            // whole recurrence from today.
                            if (task.dueState == DueState.UPCOMING) {
                                pendingEarlyComplete = task
                            } else {
                                viewModel.complete(task)
                            }
                        },
                        onDelay = { pendingDelay = task },
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

    pendingEarlyComplete?.let { task ->
        EarlyCompletionDialog(
            task = task,
            today = today,
            onConfirm = {
                viewModel.complete(task)
                pendingEarlyComplete = null
            },
            onDismiss = { pendingEarlyComplete = null },
        )
    }

    pendingDelay?.let { task ->
        DelaySheet(
            onSelect = { option ->
                viewModel.delay(task, option)
                pendingDelay = null
            },
            onDismiss = { pendingDelay = null },
        )
    }
}

/**
 * Confirms completing a task before it's due. The message is computed from the
 * same [nextDueDate] math the repository will use, so the number shown here is
 * exactly what the task will reschedule to.
 */
@Composable
private fun EarlyCompletionDialog(
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
    AlertDialog(
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

@Composable
private fun CategoryHeader(group: CategoryGroup, modifier: Modifier = Modifier) {
    // A delayed chore was silenced on purpose, so it shouldn't still read as
    // something the user is neglecting.
    val overdue = group.tasks.count { it.dueState == DueState.OVERDUE && !it.isCurrentlyDelayed() }
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

/** Whether [TaskView.delayedUntil] is set and hasn't lapsed yet. */
private fun TaskView.isCurrentlyDelayed(): Boolean = delayedUntil?.isAfter(Instant.now()) == true

/**
 * A single chore. Tapping opens detail; the trailing button marks complete in
 * place (FR-14). Overdue cards get an error-toned border and label (FR-13); a
 * delayed card is tertiary-toned instead, whatever its underlying due state.
 * Swiping left or long-pressing opens the delay picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: TaskView,
    today: LocalDate,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOverdue = task.dueState == DueState.OVERDUE
    val isDueToday = task.dueState == DueState.DUE_TODAY
    val isDelayed = task.isCurrentlyDelayed()

    val accent by animateColorAsState(
        targetValue = when {
            isDelayed -> MaterialTheme.colorScheme.tertiary
            isOverdue -> MaterialTheme.colorScheme.error
            isDueToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "accent",
    )
    val shape = RoundedCornerShape(20.dp)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelay()
            // This is an action trigger, not a real dismiss -- the row always
            // springs back, whether or not the swipe reached the threshold.
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        backgroundContent = { DelaySwipeBackground(shape) },
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val scale by rememberPressScale(interactionSource, pressedScale = 0.97f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .glassCard(shape)
                .then(
                    when {
                        isDelayed -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f), shape)
                        isOverdue -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f), shape)
                        else -> Modifier
                    },
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onDelay,
                )
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
                        text = task.delayedUntil?.takeIf { isDelayed }?.let { formatDelayedUntil(it, today) }
                            ?: formatDueLabel(task.nextDue, task.dueState, today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent,
                        fontWeight = if (task.dueState.isActionable || isDelayed) FontWeight.Medium else FontWeight.Normal,
                    )
                    Text(
                        text = formatRecurrence(task.task.recurrence),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(8.dp))
                val completeInteractionSource = remember { MutableInteractionSource() }
                val completeScale by rememberPressScale(completeInteractionSource, pressedScale = 0.8f)
                FilledTonalIconButton(
                    onClick = onComplete,
                    interactionSource = completeInteractionSource,
                    modifier = Modifier.graphicsLayer { scaleX = completeScale; scaleY = completeScale },
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
}

@Composable
private fun DelaySwipeBackground(shape: RoundedCornerShape) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            "Delay",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
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
            Text("No visible chores", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Use the eye icon to see upcoming chores, or New to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
