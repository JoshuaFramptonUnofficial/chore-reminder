package com.chorereminder.ui.home

/** Positional anchoring overrides LazyColumn's key-following when items reorder. */
data class ScrollPosition(val index: Int, val offset: Int)
fun stablePosition(index: Int, offset: Int, itemCount: Int): ScrollPosition =
    ScrollPosition(index.coerceIn(0, (itemCount - 1).coerceAtLeast(0)), offset.coerceAtLeast(0))
