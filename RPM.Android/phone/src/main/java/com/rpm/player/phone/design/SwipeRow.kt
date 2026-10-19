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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A key under a row: its icon, its label, the colour of its background, and the action it runs. */
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
fun SwipeRow(leading: SwipeKey?, trailing: SwipeKey?, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val key = with(density) { KEY_WIDTH.toPx() }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = with(density) { maxWidth.toPx() }
        val reach = (if (leading != null) width else 0f) to (if (trailing != null) width else 0f)
        fun settle(to: Float) = scope.launch { offset.animateTo(to, spring(dampingRatio = 0.85f, stiffness = 500f)) }
        fun release() {
            val at = offset.value
            when {
                at < -width * FULL_SWIPE && trailing != null -> { settle(0f); trailing.onPress() }
                at > width * FULL_SWIPE && leading != null -> { settle(0f); leading.onPress() }
                at < -key / 2 -> settle(-key)
                at > key / 2 -> settle(key)
                else -> settle(0f)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-reach.second, reach.first)) }
                    },
                    onDragStopped = { release() },
                ),
        ) {
            // The keys sit under the row and are as tall as it. Each key is as wide as the row has been dragged.
            Box(Modifier.matchParentSize()) {
                if (offset.value < 0 && trailing != null) {
                    Under(trailing, with(density) { abs(offset.value).toDp() }, Modifier.align(Alignment.CenterEnd)) { settle(0f); trailing.onPress() }
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
        BasicText(key.label, maxLines = 1, style = system(11f).copy(color = Color.White))
    }
}
