package com.rpm.player.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

/**
 * Draws [content] at [share] of the pixels of the box it fills, and scales the result up to fill the
 * box, with the same layout. The density is [share] of the screen's density, so a dp is the same share
 * of the screen at any share. Below 1, the content is drawn into a layer of its own size, which is then
 * drawn scaled, so the GPU fills [share]² of the pixels for it. Text and edges come out blurrier by
 * the same amount. At 1 it is drawn as it is. The whole is drawn at [alpha] and through [filter]. Both
 * are read when the layer is drawn, so a fade or a change of filter does not change the recording.
 *
 * It is placed at the top left of the box, so a position in the window is the position in the content
 * divided by [share]. [positionInScreens] and [boundsInScreens] convert between the two for anything
 * inside it that draws at a place it measured. A view with a surface of its own (the room, the
 * opening's sweep) must not be inside it, because the system places such a surface itself, from the
 * view's unscaled position.
 */
@Composable
fun DrawnAt(share: Float, alpha: () -> Float, filter: () -> ColorFilter?, content: @Composable () -> Unit) {
    val screen = LocalDensity.current
    val density = remember(screen, share) { Density(screen.density * share, screen.fontScale) }
    CompositionLocalProvider(LocalDensity provides density, LocalDrawnAt provides share) {
        Layout(content) { measurables, constraints ->
            val width = (constraints.maxWidth * share).roundToInt()
            val height = (constraints.maxHeight * share).roundToInt()
            val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { placeable ->
                    placeable.placeWithLayer(0, 0) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = 1f / share
                        scaleY = 1f / share
                        this.alpha = alpha()
                        // The picture setting (PictureLook): a colour filter on this layer. The layer
                        // holds no surface of its own, so nothing under it has to show through a hole in it.
                        colorFilter = filter()
                        compositingStrategy = if (share < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }
                }
            }
        }
    }
}

/** The share of the screen's pixels that the screens are drawn at ([DrawnAt]); 1 outside it. Provided by `RpmApp`. */
val LocalDrawnAt = compositionLocalOf<Float> { error("No DrawnAt share provided") }

/** Where this is in the pixels of the screens it is drawn in ([DrawnAt]): its position in the window times [share]. */
fun LayoutCoordinates.positionInScreens(share: Float): Offset = positionInRoot() * share

/** This one's bounds in the pixels of the screens it is drawn in ([DrawnAt]). */
fun LayoutCoordinates.boundsInScreens(share: Float): Rect {
    val bounds = boundsInRoot()
    return Rect(bounds.left * share, bounds.top * share, bounds.right * share, bounds.bottom * share)
}
