package com.chorereminder.domain

import com.chorereminder.ui.home.ScrollPosition
import com.chorereminder.ui.home.stablePosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollPolicyTest {
    @Test fun preservesPositionAcrossReorderingAndUndo() {
        assertEquals(ScrollPosition(4, 32), stablePosition(4, 32, 20))
        assertEquals(ScrollPosition(4, 32), stablePosition(4, 32, 19))
    }
    @Test fun clampsWhenItemsDisappearAtEndOrListBecomesEmpty() {
        assertEquals(ScrollPosition(2, 10), stablePosition(8, 10, 3))
        assertEquals(ScrollPosition(0, 0), stablePosition(4, -1, 0))
    }
}
