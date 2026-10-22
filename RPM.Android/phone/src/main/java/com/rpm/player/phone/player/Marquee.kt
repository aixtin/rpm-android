package com.rpm.player.phone.player

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Where a line too long for its column sits in its travel. It is the iPhone's `MarqueeWalk` (RPMDesign), with the same
 * numbers. A line that fits never moves. A line too long rests at its start, walks left at reading pace until its tail
 * shows, holds long enough to read the tail, runs back, and rests again. **The rest at the start matters most**: the
 * title is there, and a reader looks for the title first.
 */
internal object MarqueeWalk {
    const val START_PAUSE = 10.0
    const val END_PAUSE = 2.0

    /** Reading pace, and the quicker pace of the run back, in points a second. */
    const val PACE = 42.0
    const val BACK_PACE = 130.0

    /** How far past its tail a line walks before it holds, so the last letter does not stop on the clip's edge. */
    const val TAIL_SLACK = 10.0

    /** How far a line has to walk to show its tail. Zero when it fits. */
    fun travel(text: Double, column: Double): Double = max(0.0, text + TAIL_SLACK - column)

    /** How much of the line is hidden past the trailing edge at [offset]. */
    fun hiddenAfter(offset: Double, travel: Double): Double = if (travel <= 0) 0.0 else max(0.0, travel - TAIL_SLACK + offset)

    fun period(travel: Double): Double = if (travel <= 0) 0.0 else START_PAUSE + travel / PACE + END_PAUSE + travel / BACK_PACE

    /** Where the line sits [seconds] into its travel: zero at rest, negative while it is walked left. */
    fun offset(seconds: Double, travel: Double): Double {
        if (travel <= 0) return 0.0
        val cycle = period(travel)
        var at = seconds % cycle
        if (at < 0) at += cycle
        if (at < START_PAUSE) return 0.0
        at -= START_PAUSE
        val walking = travel / PACE
        if (at < walking) return -at * PACE
        at -= walking
        if (at < END_PAUSE) return -travel
        at -= END_PAUSE
        // Never above zero, so the line does not bounce off its own start as the cycle wraps.
        return min(0.0, -travel + at * BACK_PACE)
    }

    /** How long the line stands still from [seconds] on: the rest, or the hold at its tail. Zero while it moves. */
    fun stillFor(seconds: Double, travel: Double): Double {
        if (travel <= 0) return Double.POSITIVE_INFINITY
        val cycle = period(travel)
        var at = seconds % cycle
        if (at < 0) at += cycle
        if (at < START_PAUSE) return START_PAUSE - at
        at -= START_PAUSE + travel / PACE
        if (at in 0.0..END_PAUSE) return END_PAUSE - at
        return 0.0
    }
}

/** How deep an edge dissolves at full stretch, the iPhone's `EdgeFade.depth`. */
private val EDGE_DEPTH = 30.dp

/**
 * A line of text that walks sideways when it does not fit. It is the iPhone's `Marquee` (RPMUI/Marquee.swift): it rests
 * for ten seconds, walks at reading pace, holds, and runs back ([MarqueeWalk]). An edge dissolves while letters are hidden
 * past it, as a mask on the letters and not a colour over them, because the line sits on a picture.
 *
 * **Only the drawing moves.** The offset is a state read in the layer and the mask, so a frame redraws this line and
 * recomposes nothing. While the line rests or holds, no frames are asked for at all: it waits out the pause instead.
 *
 * @param height one line of this type, given by the caller as on iPhone.
 */
@Composable
internal fun Marquee(text: String, style: TextStyle, height: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var line by remember { mutableIntStateOf(0) }
    var at by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.CenterStart) {
        val column = with(density) { maxWidth.toPx() }
        val travelPx = max(0f, line + with(density) { MarqueeWalk.TAIL_SLACK.dp.toPx() } - column)
        val travel = with(density) { travelPx.toDp().value.toDouble() }
        val depth = with(density) { EDGE_DEPTH.toPx() }

        // A new title rests for ten seconds instead of carrying on the old one's walk.
        LaunchedEffect(text, travel) {
            at = 0f
            if (travel <= 0) return@LaunchedEffect
            // The seconds into the travel, counted by the pauses waited and the frames walked.
            var waited = 0.0
            while (true) {
                val still = MarqueeWalk.stillFor(waited, travel)
                if (still > 0) {
                    delay((still * 1000).toLong().coerceAtLeast(16))
                    waited += still
                    at = with(density) { MarqueeWalk.offset(waited, travel).dp.toPx() }
                    continue
                }
                // Moving: one offset per frame until the next pause.
                val from = withFrameNanos { it }
                var now = from
                while (MarqueeWalk.stillFor(waited + (now - from) / 1e9, travel) <= 0) {
                    at = with(density) { MarqueeWalk.offset(waited + (now - from) / 1e9, travel).dp.toPx() }
                    now = withFrameNanos { it }
                }
                waited += (now - from) / 1e9
                at = with(density) { MarqueeWalk.offset(waited, travel).dp.toPx() }
            }
        }

        BasicText(
            text,
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .onSizeChanged { line = it.width }
                .graphicsLayer {
                    translationX = at
                    // The layer is cut to the column, and drawn off screen so the mask can take letters away.
                    compositingStrategy = if (travelPx > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .drawWithContent {
                    drawContent()
                    if (travelPx <= 0) return@drawWithContent
                    val offset = at
                    val half = column / 2
                    val lead = min(max(-offset, 0f), min(depth, half))
                    val trail = min(max(travelPx - with(density) { MarqueeWalk.TAIL_SLACK.dp.toPx() } + offset, 0f), min(depth, half))
                    // The mask is in the column's frame, which is the line's frame moved back by the offset.
                    val left = -offset
                    val stops = arrayOf(
                        0f to Color.Black.copy(alpha = if (lead > 0) 0f else 1f),
                        (lead / column).coerceIn(0f, 1f) to Color.Black,
                        (1 - trail / column).coerceIn(0f, 1f) to Color.Black,
                        1f to Color.Black.copy(alpha = if (trail > 0) 0f else 1f),
                    )
                    drawRect(Brush.horizontalGradient(*stops, startX = left, endX = left + column), blendMode = BlendMode.DstIn)
                    // Nothing outside the column is shown.
                    if (left > 0) drawRect(Color.Transparent, size = size.copy(width = left), blendMode = BlendMode.Clear)
                    val right = left + column
                    if (right < size.width) {
                        drawRect(Color.Transparent, topLeft = androidx.compose.ui.geometry.Offset(right, 0f), blendMode = BlendMode.Clear)
                    }
                },
            maxLines = 1,
            softWrap = false,
            style = style,
        )
    }
}

/**
 * A line that scrolls through once when it is wider than its space, and stops where it started: the iPhone's
 * `OnceTicker`, for the mini player. It waits two seconds, moves at 30 points a second, and its copy follows 32 points
 * behind. A line that does not fit fades out at its trailing edge rather than ending in an ellipsis.
 */
@Composable
internal fun OnceTicker(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    var box by remember { mutableIntStateOf(0) }
    var full by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val fade = with(density) { 14.dp.toPx() }
    Box(modifier.fillMaxWidth().onSizeChanged { box = it.width }) {
        BasicText(
            text,
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (full <= box || box == 0) return@drawWithContent
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - fade, endX = size.width),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .basicMarquee(iterations = 1, initialDelayMillis = 2_000, spacing = MarqueeSpacing(32.dp), velocity = 30.dp),
            maxLines = 1,
            softWrap = false,
            style = style,
            onTextLayout = { full = it.multiParagraph.width.toInt() },
        )
    }
}
