package com.rpm.player.phone.design

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * A scrolling list fades out at its top edge instead of being cut off there. The iPhone app has the same
 * `DissolvingScroll` before iOS 26. The faded band grows as the list scrolls, up to [depth]. So a list at
 * its top is not faded at all (`EdgeFade`). The depth is 30 dp under a header, or under the status bar and
 * the back key's row on a page with no header.
 *
 * [scrolled] is read during drawing, so a scroll redraws the band without recomposing anything.
 */
fun Modifier.dissolvesAtTop(depth: Dp, scrolled: () -> Float): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val band = minOf(scrolled(), depth.toPx(), size.height)
        if (band > 0f) {
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = band),
                size = Size(size.width, band),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * How far a grid has scrolled off its top. The fade band only needs to know this. Once the first row is
 * off screen, the result is `Float.MAX_VALUE`.
 */
fun LazyGridState.scrolled(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/** The same for a list. */
fun LazyListState.scrolled(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()
