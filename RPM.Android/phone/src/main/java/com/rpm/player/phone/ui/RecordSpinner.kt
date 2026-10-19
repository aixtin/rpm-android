package com.rpm.player.phone.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

/**
 * A loading indicator that looks like a glass record turning. It matches the iPhone's `RecordSpinner`
 * (RPMSharedUI/RecordSpinner.swift): the same rings, and the same gradient stops, drawn in a 100-unit box
 * with every length a fraction of [diameter]. It is shown while data loads, in place of the platform's
 * spinning ring.
 *
 * Only the glint on the rim and the label turn. The grooves are concentric circles, so turning them would
 * not show. Every spinner starts at the angle that a record spinning since the Unix epoch would be at. So
 * all spinners on screen are in step.
 */
@Composable
fun RecordSpinner(diameter: Dp, modifier: Modifier = Modifier) {
    val began = remember { ((System.currentTimeMillis() / 1000.0 * DEGREES_PER_SECOND) % 360).toFloat() }
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween((REVOLUTION * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    Canvas(modifier.size(diameter)) { drawSpinner(began + turn) }
}

private fun DrawScope.drawSpinner(angle: Float) {
    val d = size.minDimension
    fun u(length: Float) = length * d / 100
    val c = center

    // The disc, lit from the top left as everywhere else in the app.
    drawCircle(glass(listOf(0f to 0x3D, 0.45f to 0x1F, 1f to 0x14), 0.15f, 0f, 0.85f, 1f), radius = d / 2, center = c)

    // The rim.
    drawCircle(glass(listOf(0f to 0x8C, 0.5f to 0x26, 1f to 0x5E), 0.1f, 0f, 0.9f, 1f), radius = d / 2 - u(1f) / 2, center = c, style = Stroke(u(1f)))

    // The grooves. They do not turn.
    for ((edge, ink) in GROOVES) {
        drawCircle(Color.White.copy(alpha = ink / 255f), radius = u(edge) / 2 - u(0.75f) / 2, center = c, style = Stroke(u(0.75f)))
    }

    rotate(angle, pivot = c) {
        // The light on the rim. At this size it is the only part that shows motion.
        drawCircle(
            glass(listOf(0f to 0, 0.34f to 0, 0.54f to 0x5E, 0.74f to 0, 1f to 0), 0f, 0f, 1f, 1f),
            radius = u(94f) / 2 - u(6f) / 2,
            center = c,
            style = Stroke(u(6f)),
        )
        // The label. Its light is off-centre, so the turn can be seen.
        drawCircle(glass(listOf(0f to 0x4D, 1f to 0x1F), 0.2f, 0f, 0.8f, 1f, box = u(35f)), radius = u(35f) / 2, center = c)
        drawCircle(Color.White.copy(alpha = 0x4D / 255f), radius = u(35f) / 2 - u(1f) / 2, center = c, style = Stroke(u(1f)))
    }

    // The spindle hole, in the centre. It does not turn.
    drawCircle(Color(0xFF0B111A).copy(alpha = 0x66 / 255f), radius = u(4f) / 2, center = c)
    drawCircle(Color.White.copy(alpha = 0x33 / 255f), radius = u(4f) / 2 - u(0.5f) / 2, center = c, style = Stroke(u(0.5f)))
}

/**
 * A white gradient with alpha values at the given offsets, from one point of a box to another. The points
 * are fractions of [box], the side of a square centred on the spinner. [box] is the whole spinner unless
 * set otherwise.
 */
private fun DrawScope.glass(stops: List<Pair<Float, Int>>, fromX: Float, fromY: Float, toX: Float, toY: Float, box: Float = size.minDimension): Brush {
    val left = center.x - box / 2
    val top = center.y - box / 2
    return Brush.linearGradient(
        *stops.map { (at, ink) -> at to Color.White.copy(alpha = ink / 255f) }.toTypedArray(),
        start = Offset(left + fromX * box, top + fromY * box),
        end = Offset(left + toX * box, top + toY * box),
    )
}

/** The grooves: their diameter in the 100-unit box, and the alpha they are drawn with. The alpha alternates. */
private val GROOVES = listOf(89f to 0x1F, 80f to 0x14, 71f to 0x1F, 62f to 0x14, 53f to 0x1F)

/** Seconds for one turn, at 33 and a third turns a minute. */
private const val REVOLUTION = 60.0 / 33.3333
private const val DEGREES_PER_SECOND = 360 / REVOLUTION
