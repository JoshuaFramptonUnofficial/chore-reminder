package com.chorereminder.domain

import org.junit.Assert.*
import org.junit.Test

class UndoQueueTest {
    @Test fun rapidCompletionsQueue_withoutStackingOrTakingWrongAction() {
        val queue = UndoQueue()
        queue.add(1, 4, 1000, 1); queue.add(2, 8, 1100, 2)
        assertNull(queue.take(2, 1200))
        assertEquals(4L, queue.take(1, 1300)!!.revision)
        assertNull(queue.take(1, 1400))
        assertEquals(2L, queue.receipts.value.first().completionId)
        assertEquals(9300L, queue.receipts.value.first().expiresAt)
    }
    @Test fun expiredAndRestartedActionsCannotBeUndone() {
        val queue = UndoQueue()
        queue.add(1, 1, 1000)
        assertNull(queue.take(1, 9000))
        assertTrue(queue.receipts.value.isEmpty())
        assertTrue(UndoQueue().receipts.value.isEmpty())
    }
    @Test fun sameChoreConsolidates_andClearInvalidatesImportedDataActions() {
        val queue = UndoQueue()
        queue.add(1, 1, 1000, 42); queue.add(2, 2, 2000, 42)
        assertNull(queue.take(1, 2100))
        assertEquals(2L, queue.receipts.value.single().completionId)
        queue.clear()
        assertNull(queue.take(2, 2200))
    }
    @Test fun expiredFrontAdvances_andLongBackgroundExpiresPendingActions() {
        val queue = UndoQueue()
        queue.add(1, 1, 1000); queue.add(2, 2, 1100)
        queue.refresh(9000)
        assertEquals(2L, queue.receipts.value.single().completionId)
        queue.refresh(200000)
        assertTrue(queue.receipts.value.isEmpty())
    }

    @Test fun recreationAndHiddenPresentationDoNotResetDeadline() {
        val queue = UndoQueue()
        queue.add(1, 1, 1000)
        queue.extendFirst(1, 8000, 1100)
        assertEquals(9000L, queue.receipts.value.single().expiresAt)
        queue.extendFirst(1, 8000, 6000) // recreated lifetime owner / return from details
        assertEquals(9000L, queue.receipts.value.single().expiresAt)
        queue.refresh(8999)
        assertEquals(1, queue.receipts.value.size)
        queue.refresh(9000)
        assertTrue(queue.receipts.value.isEmpty())
    }

    @Test fun actionDefaultsToCompleted_andIsCarriedThroughToTheReceipt() {
        val queue = UndoQueue()
        queue.add(1, 1, 1000)
        assertEquals(UndoAction.Completed, queue.receipts.value.single().action)
    }

    @Test fun nonDefaultActionsArePreservedAcrossQueueOperations() {
        val queue = UndoQueue()
        val delayed = UndoAction.Delayed(previousSnoozedUntil = null, previousSnoozeRevision = null)
        queue.add(1, 1, 1000, taskId = 1, action = UndoAction.Skipped)
        queue.add(2, 2, 2000, taskId = 2, action = delayed)
        assertEquals(UndoAction.Skipped, queue.take(1, 2100)!!.action)
        assertEquals(delayed, queue.receipts.value.single().action)
    }

    @Test fun accessibilityTimeoutAppliedOnceFromActivationAndBoundedByPendingAge() {
        val queue = UndoQueue()
        queue.add(1, 1, 1000)
        queue.extendFirst(1, 20000, 1500)
        assertEquals(21000L, queue.receipts.value.single().expiresAt)
        queue.extendFirst(1, 120000, 2000)
        assertEquals(21000L, queue.receipts.value.single().expiresAt)
        queue.add(2, 1, 2000)
        queue.take(1, 20000)
        queue.extendFirst(2, 120000, 20001)
        assertEquals(122000L, queue.receipts.value.single().expiresAt)
    }
}
