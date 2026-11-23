package com.chorereminder.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a receipt reverses. [Completed] and [Skipped] both undo through the same
 * completion-row mechanism (see `undoSpecific`); [Delayed] undoes by restoring
 * whatever snooze state preceded it.
 */
sealed interface UndoAction {
    data object Completed : UndoAction
    data object Skipped : UndoAction
    data class Delayed(
        val previousSnoozedUntil: java.time.Instant?,
        val previousSnoozeRevision: Long?,
        /** What `reminderHistory.lastNotifiedOn` was before the delay cleared it, if anything. */
        val previousLastNotifiedOn: java.time.LocalDate?,
    ) : UndoAction
}

data class UndoReceipt(val completionId: Long, val revision: Long, val expiresAt: Long, val taskId: Long,
    val activatedAt: Long? = null, val timeoutAdjusted: Boolean = false,
    val validUntil: Long = expiresAt,
    val action: UndoAction = UndoAction.Completed,
)

/** One foreground confirmation at a time; pending actions have a bounded lifetime. */
class UndoQueue {
    private val pending = MutableStateFlow<List<UndoReceipt>>(emptyList())
    val receipts = pending.asStateFlow()

    @Synchronized fun add(id: Long, revision: Long, now: Long, taskId: Long = id, action: UndoAction = UndoAction.Completed) {
        removeTask(taskId, now)
        refresh(now)
        pending.value = pending.value + UndoReceipt(id, revision, now + if (pending.value.isEmpty()) 8000 else 120000, taskId,
            activatedAt = now.takeIf { pending.value.isEmpty() }, validUntil = now + 120000, action = action)
    }

    @Synchronized fun refresh(now: Long) {
        val old = pending.value
        val live = old.filter { it.expiresAt > now }
        pending.value = if (live.firstOrNull()?.completionId != old.firstOrNull()?.completionId)
            activate(live, now) else live
    }

    @Synchronized fun take(id: Long, now: Long): UndoReceipt? {
        refresh(now)
        val first = pending.value.firstOrNull()?.takeIf { it.completionId == id } ?: return null
        pending.value = activate(pending.value.drop(1), now)
        return first
    }

    @Synchronized fun removeTask(taskId: Long, now: Long) {
        val old = pending.value
        val live = old.filter { it.taskId != taskId }
        pending.value = if (old.firstOrNull() != live.firstOrNull()) activate(live, now) else live
    }

    @Synchronized fun extendFirst(id: Long, duration: Long, now: Long) {
        refresh(now)
        val first = pending.value.firstOrNull() ?: return
        if (first.completionId != id || first.timeoutAdjusted) return
        pending.value = pending.value.mapIndexed { i, item ->
            if (i == 0) item.copy(expiresAt = minOf(item.validUntil, (item.activatedAt ?: now) + duration.coerceIn(8000, 120000)), timeoutAdjusted = true) else item
        }
    }

    private fun activate(items: List<UndoReceipt>, now: Long): List<UndoReceipt> =
        items.filter { it.expiresAt > now }.mapIndexed { i, item ->
            if (i == 0 && item.activatedAt == null) item.copy(expiresAt = minOf(item.validUntil, now + 8000), activatedAt = now) else item
        }

    @Synchronized fun clear() { pending.value = emptyList() }
}
