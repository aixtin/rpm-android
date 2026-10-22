package com.rpm.player.phone.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity

/**
 * The iPhone's rubber band at the top of a page: pulled down past its top, the content comes down with the finger,
 * held back more the further it goes, and springs back when the finger lets go. A fling that reaches the top runs on
 * a little past it and comes back. It replaces Android's stretch on the pages whose picture grows with the pull (home's
 * banner, an artist's photograph), because a stretch would pull the picture out of shape instead of showing the
 * ground above it for the picture to cover.
 *
 * [pulled] is how far the content stands below its top, in pixels: the iPhone's `ScrollReach.pulled`. **Read it only in
 * a draw or layer lambda** ([growsWithPull]), as the iPhone reads it only in `GrowsWithPull`, so a pull moves layers
 * and recomposes nothing.
 *
 * Only the top is a band. The foot keeps no effect at all, as a list that ends under the tab bar should not bounce
 * its last row.
 */
@Stable
class TopPull internal constructor() : OverscrollEffect {
    /** How far the finger has pulled past the top. What is shown is this, held back ([rubber]). */
    private var travel by mutableFloatStateOf(0f)

    /** The page's height, which sets how hard the band holds back. Measured by [node]. */
    private var viewport = 1f

    val pulled: Float get() = rubber(travel)

    override val isInProgress: Boolean get() = travel != 0f

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        var y = delta.y
        var used = 0f
        // Pushing back up while pulled takes the pull away first, before the page scrolls.
        if (travel > 0f && y < 0f) {
            val back = maxOf(y, -travel)
            travel += back
            y -= back
            used += back
        }
        val scrolled = performScroll(Offset(delta.x, y))
        used += scrolled.y
        val over = y - scrolled.y
        // Only a finger pulls the band. A fling that reaches the top bounces in [applyToFling] instead.
        if (over > 0f && source == NestedScrollSource.UserInput) {
            travel += over
            used += over
        }
        return Offset(scrolled.x, used)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (travel > 0f) {
            // Let go while pulled: back to the top. The fling is spent on the way back, as on iPhone.
            animate(pulled, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { value, _ -> show(value) }
            travel = 0f
            return
        }
        val left = performFling(velocity)
        if (left.y > 0f) {
            // Thrown into the top: it runs on past it by what the throw had left, and comes back.
            animate(0f, 0f, initialVelocity = left.y, animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { value, _ -> show(value) }
            travel = 0f
        }
    }

    /** Puts the band where [shown] pixels show, from the finger travel that shows that much. */
    private fun show(shown: Float) {
        val held = shown.coerceIn(0f, viewport * 0.999f)
        travel = if (held <= 0f) 0f else viewport / BAND * (1f / (1f - held / viewport) - 1f)
    }

    /** UIScrollView's band: the finger's [travel] shown as `(1 - 1 / (travel × 0.55 / height + 1)) × height`. */
    private fun rubber(travel: Float): Float =
        if (travel <= 0f) 0f else (1f - 1f / (travel * BAND / viewport + 1f)) * viewport

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            if (placeable.height > 0) viewport = placeable.height.toFloat()
            return layout(placeable.width, placeable.height) {
                // In the layer, so a pull moves the content without laying anything out again.
                placeable.placeWithLayer(0, 0) { translationY = pulled }
            }
        }
    }

    private companion object {
        /** How hard UIScrollView's band holds back. */
        const val BAND = 0.55f
    }
}

/** A [TopPull] for one page. */
@Composable
fun rememberTopPull(): TopPull = remember { TopPull() }

/**
 * Scales this up about [origin] by how far [pull] has the page down past its top, so a picture [plate] pixels tall keeps
 * its top at the top of the screen and the ground never shows above it (`growsWithPull` on iPhone). [origin] is the
 * picture's foot, given as a share of this view. Only the layer reads the pull.
 */
fun Modifier.growsWithPull(pull: TopPull, plate: Float, origin: TransformOrigin): Modifier = graphicsLayer {
    val grown = 1f + pull.pulled / plate.coerceAtLeast(1f)
    scaleX = grown
    scaleY = grown
    transformOrigin = origin
}
