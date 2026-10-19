package com.rpm.player.tv.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Where Down goes from the keys in the top right corner of a grid: the last card of the first row whose top
 * is on screen, which is the card under the keys. The target is named explicitly because the remote's own
 * search from those keys finds the mini player beside them. Down from the mini player gives the focus back to
 * the item that last had it, which is the key, so the focus would go between the two and never reach the
 * grid. The index is worked out from the grid's layout whenever it changes, so it is always a card that is
 * composed.
 */
@Stable
class UnderKeys(private val grid: LazyGridState, private val columns: Int) {
    val requester = FocusRequester()

    /** The card's place in the grid; -1 while the grid has none. */
    val index: Int by derivedStateOf {
        val info = grid.layoutInfo
        val top = info.visibleItemsInfo.firstOrNull { it.offset.y >= 0 } ?: info.visibleItemsInfo.firstOrNull()
        if (top == null) -1 else minOf(top.index / columns * columns + columns - 1, info.totalItemsCount - 1)
    }

    /** The card at [at] carries the requester while it is the one under the keys. */
    fun mark(at: Int): Modifier = if (at == index) Modifier.focusRequester(requester) else Modifier
}

@Composable
fun rememberUnderKeys(grid: LazyGridState, columns: Int): UnderKeys = remember(grid, columns) { UnderKeys(grid, columns) }
