package com.rpm.player.phone.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A key under a row: its icon, its label (empty for the icon alone), the colour of its background, and the action it
 * runs. */
class SwipeKey(val glyph: String, val label: String, val tint: Color, val onPress: () -> Unit)

/** How wide a key under a row is once the row is open. */
private val KEY_WIDTH = 80.dp

/**
 * The fraction of the row's width past which releasing runs the key's action at once. This is a full swipe.
 */
private const val FULL_SWIPE = 0.6f

/**
 * A row with a key under each side. Drag it sideways to show the key, as the iPhone's swipe actions do.
 * Dragging left shows [trailing], dragging right shows [leading]. Releasing past half a key leaves the row
 * open on that key. Dragging most of the way across runs the key's action at once. A tap on the open row
 * closes it. A side with no key cannot be dragged, so the row never opens onto nothing.
 */
@Composable
fun SwipeRow(leading: SwipeKey?, trailing: SwipeKey?, content: @Composable () -> Unit) =
    SwipeRow(leading, listOfNotNull(trailing), fullTrailing = true, content)

/**
 * A row with one key or none under its leading side, and any number under its trailing side, as SwiftUI's
 * `swipeActions` holds several buttons on one edge. The first of [trailing] sits at the outer edge, as SwiftUI places
 * the first button it is given. Open, the row stands one key's width aside per key; while it is dragged the keys share
 * the width it has moved.
 *
 * @param fullTrailing whether dragging most of the way across to the left runs the outer key at once, which SwiftUI's
 * `allowsFullSwipe` decides. Dragging right always may, as SwiftUI's default for the leading edge.
 */
@Composable
fun SwipeRow(leading: SwipeKey?, trailing: List<SwipeKey>, fullTrailing: Boolean, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val key = with(density) { KEY_WIDTH.toPx() }
    // The row's width, taken from its layout rather than from a `BoxWithConstraints`, which would make every row of a
    // long list a subcomposition of its own. Nothing is drawn from it, so it is not state.
    val measured = remember { FloatArray(1) }
    val open = key * trailing.size

    fun settle(to: Float) = scope.launch { offset.animateTo(to, spring(dampingRatio = 0.85f, stiffness = 500f)) }
    fun release() {
        val width = measured[0]
        val at = offset.value
        when {
            at < -width * FULL_SWIPE && fullTrailing && trailing.isNotEmpty() -> { settle(0f); trailing.first().onPress() }
            at > width * FULL_SWIPE && leading != null -> { settle(0f); leading.onPress() }
            at < -open / 2 && trailing.isNotEmpty() -> settle(-open)
            at > key / 2 && leading != null -> settle(key)
            else -> settle(0f)
        }
    }

    Box(Modifier.fillMaxWidth().onSizeChanged { measured[0] = it.width.toFloat() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val width = measured[0]
                        val reach = (if (leading != null) width else 0f) to (if (trailing.isNotEmpty()) (if (fullTrailing) width else open * 1.5f) else 0f)
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-reach.second, reach.first)) }
                    },
                    onDragStopped = { release() },
                ),
        ) {
            // The keys sit under the row and are as tall as it. The keys of a side share the width the row has been
            // dragged.
            Box(Modifier.matchParentSize()) {
                if (offset.value < 0 && trailing.isNotEmpty()) {
                    val each = with(density) { (abs(offset.value) / trailing.size).toDp() }
                    Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                        // Drawn from the inner key outwards, so the first key ends at the outer edge.
                        for (under in trailing.asReversed()) {
                            Under(under, each, Modifier) { settle(0f); under.onPress() }
                        }
                    }
                }
                if (offset.value > 0 && leading != null) {
                    Under(leading, with(density) { offset.value.toDp() }, Modifier.align(Alignment.CenterStart)) { settle(0f); leading.onPress() }
                }
            }
            Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) {
                content()
                // An open row uses a tap to close itself, instead of passing the tap to its content.
                if (offset.value != 0f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(remember { MutableInteractionSource() }, indication = null) { settle(0f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Under(key: SwipeKey, width: androidx.compose.ui.unit.Dp, modifier: Modifier, onPress: () -> Unit) {
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(key.tint)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onPress),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(key.glyph, style = icon(15f).copy(color = Color.White))
        // A key may be the icon alone, as SwiftUI draws a `Label` in a swipe action.
        if (key.label.isNotEmpty()) BasicText(key.label, maxLines = 1, style = system(11f).copy(color = Color.White))
    }
}
