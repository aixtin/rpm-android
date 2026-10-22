package com.rpm.player.phone.library

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpm.player.core.playback.QueueRow
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.icon
import kotlinx.coroutines.delay

/*
 * Rearranging a stored playlist's songs by their grips. It is the same gesture as the queue's (QueuePage): rearranging
 * is a mode, as on the iPhone, its key shows a grip on every row, and a row carried by its grip goes where it is let
 * go, the rows it passes moving aside to make room. A stored playlist has no song playing in it, so every row can move.
 */

/** How close to the top or bottom of the list a carried row must be before the list scrolls, and by how much a frame. */
private val EDGE_ROOM = 48.dp
private const val EDGE_STEP = 18f

/**
 * The row being carried: which row, how far the finger has moved it, how tall it is, and whether it has been let go.
 * A let-go row is put down once the list shows its move.
 */
internal class ListCarry {
    var id by mutableStateOf<String?>(null)
    var dy by mutableFloatStateOf(0f)
    var size by mutableIntStateOf(0)
    var landed by mutableStateOf(false)

    fun clear() {
        id = null
        dy = 0f
        size = 0
        landed = false
    }
}

/** The grip at a row's end. Carrying it moves the row; letting go calls [onDrop]. */
@Composable
internal fun Grip(rowId: String, carry: ListCarry, list: LazyListState, onDrop: () -> Unit) {
    Box(
        Modifier
            .width(48.dp)
            .height(56.dp)
            .pointerInput(rowId) {
                detectDragGestures(
                    onDragStart = {
                        carry.id = rowId
                        carry.dy = 0f
                        carry.landed = false
                        carry.size = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rowId }?.size ?: 0
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        carry.dy += amount.y
                    },
                    onDragEnd = onDrop,
                    onDragCancel = carry::clear,
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(Glyph.grip, style = icon(16f).copy(color = Ink.white(0x88)))
    }
}

/**
 * Scrolls [list] while the carried row is near its top or bottom edge, so a row can be carried past the screen. The
 * row moves with the scroll and stays under the finger. [bottomRoom] is the part of the list the chrome covers.
 */
@Composable
internal fun EdgeScroll(carry: ListCarry, list: LazyListState, bottomRoom: Dp) {
    val density = LocalDensity.current
    val edge = with(density) { EDGE_ROOM.toPx() }
    val foot = with(density) { bottomRoom.toPx() }
    LaunchedEffect(carry.id) {
        while (carry.id != null && !carry.landed) {
            val info = list.layoutInfo
            val mine = info.visibleItemsInfo.firstOrNull { it.key == carry.id }
            if (mine != null) {
                val centre = mine.offset + carry.dy + mine.size / 2f
                val step = when {
                    centre < info.viewportStartOffset + edge -> -EDGE_STEP
                    centre > info.viewportEndOffset - foot - edge -> EDGE_STEP
                    else -> 0f
                }
                if (step != 0f) carry.dy += list.scrollBy(step)
            }
            delay(16)
        }
    }
}

/**
 * The row the carried row is over, as a place among [rows], or null when nothing is carried. It is read from where the
 * rows are laid out, not from where they have been moved aside to.
 */
internal fun hoverOf(list: LazyListState, rows: List<QueueRow<*>>, carry: ListCarry): Int? {
    val id = carry.id ?: return null
    val seen = list.layoutInfo.visibleItemsInfo
    val mine = seen.firstOrNull { it.key == id } ?: return null
    val centre = mine.offset + carry.dy + mine.size / 2f
    val places = rows.withIndex().associate { it.value.id to it.index }
    val shown = seen.filter { it.key in places }
    if (shown.isEmpty()) return null
    val under = shown.firstOrNull { centre >= it.offset && centre < it.offset + it.size }
        ?: if (centre < shown.first().offset) shown.first() else shown.last()
    return places.getValue(under.key as String)
}

/**
 * How far a row moves aside for the carried row: up by its height when the carried row has passed it going down,
 * down when it has passed it going up.
 */
internal fun shiftOf(row: QueueRow<*>, rows: List<QueueRow<*>>, list: LazyListState, carry: ListCarry): Float {
    val id = carry.id ?: return 0f
    if (row.id == id) return 0f
    val from = rows.indexOfFirst { it.id == id }
    val over = hoverOf(list, rows, carry) ?: return 0f
    val at = rows.indexOf(row)
    return when {
        over > from && at in (from + 1)..over -> -carry.size.toFloat()
        over < from && at in over until from -> carry.size.toFloat()
        else -> 0f
    }
}
