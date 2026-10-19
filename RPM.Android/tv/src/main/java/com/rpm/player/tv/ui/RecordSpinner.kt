package com.rpm.player.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * The waiting indicator: a record turning in the app's glass style. It is the desktop's RecordSpinner
 * (Controls/RecordSpinner.xaml), drawn to the same figures at 100 units and scaled to the size that [modifier]
 * gives. It is shown wherever the TV waits, instead of a ring, because it tells the person that a record is
 * coming.
 *
 * It turns while it is composed, so a caller shows it while it waits and removes it when the answer is in.
 * Only the light on the rim and the label turn. The disc, its edge, the grooves and the spindle hole are round
 * and still, and are drawn once. The angle comes from one clock ([spinAngle]), so every record on screen is in
 * step, and a frame missed under load is caught up and not dropped. The angle is written about 30 times a
 * second ([SPIN_BEAT_MS]), as on the desktop; that is smooth enough for a disc of this size. The angle is read
 * in the turning layer's own block, so each beat redraws that layer and nothing else.
 */
@Composable
fun RecordSpinner(modifier: Modifier) {
    var angle by remember { mutableFloatStateOf(spinAngle()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { angle = spinAngle() }
            delay(SPIN_BEAT_MS)
        }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) { drawStill() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) { drawTurning() }
    }
}

/** 33 1/3 revolutions a minute, the speed of an LP. */
private const val DEGREES_PER_SECOND = 360.0 * (100.0 / 3.0) / 60.0

/** How often the angle is written: the desktop's 30 ms beat. */
private const val SPIN_BEAT_MS = 30L

/** Where every record on screen stands now. */
private fun spinAngle(): Float = (System.nanoTime() / 1e9 * DEGREES_PER_SECOND % 360.0).toFloat()

/** The disc, its edge, the grooves and the spindle hole. The light comes from the top left, as in the rest
 * of the app. */
private fun DrawScope.drawStill() {
    // The disc.
    disc(100f, across(100f, Offset(0.15f, 0f), Offset(0.85f, 1f), 0f to Color(0x3DFFFFFF), 0.45f to Color(0x1FFFFFFF), 1f to Color(0x14FFFFFF)))
    // Its edge, lit where the light falls: the same hairline that every frosted card in the app has, drawn as a circle.
    ring(100f, 1f, across(100f, Offset(0.1f, 0f), Offset(0.9f, 1f), 0f to Color(0x8CFFFFFF), 0.5f to Color(0x26FFFFFF), 1f to Color(0x5EFFFFFF)))
    // Grooves are not rotated: real grooves are concentric, so their turning would not be visible.
    for ((wide, alpha) in GROOVES) ring(wide, 0.75f, SolidColor(Color.White.copy(alpha = alpha)))
    // The spindle hole: in the exact centre and not rotated, as on a real record.
    disc(4f, SolidColor(Color(0x660B111A)))
    ring(4f, 0.5f, SolidColor(Color(0x33FFFFFF)))
}

/** The light on the rim, which is what shows the motion at the sizes this is drawn at, and the label. The
 * label is lit off centre so that its turning is also visible. */
private fun DrawScope.drawTurning() {
    ring(
        94f, 6f,
        across(
            94f, Offset(0f, 0f), Offset(1f, 1f),
            0f to Color(0x00FFFFFF), 0.34f to Color(0x00FFFFFF), 0.54f to Color(0x5EFFFFFF), 0.74f to Color(0x00FFFFFF), 1f to Color(0x00FFFFFF),
        ),
    )
    disc(35f, across(35f, Offset(0.2f, 0f), Offset(0.8f, 1f), 0f to Color(0x4DFFFFFF), 1f to Color(0x1FFFFFFF)))
    ring(35f, 1f, SolidColor(Color(0x4DFFFFFF)))
}

/** The grooves' diameters and strengths: the desktop's five ellipses. */
private val GROOVES = listOf(89f to 0x1F / 255f, 80f to 0x14 / 255f, 71f to 0x1F / 255f, 62f to 0x14 / 255f, 53f to 0x1F / 255f)

/** One unit of the 100-unit drawing, in pixels. */
private val DrawScope.unit: Float get() = size.minDimension / 100f

/** A filled circle [diameter] units across, centred. */
private fun DrawScope.disc(diameter: Float, brush: Brush) {
    drawCircle(brush, radius = diameter / 2 * unit, center = center)
}

/** A circle's outline [width] units wide, inside a circle [diameter] units across, as a XAML Ellipse strokes
 * inside its bounds. */
private fun DrawScope.ring(diameter: Float, width: Float, brush: Brush) {
    drawCircle(brush, radius = (diameter - width) / 2 * unit, center = center, style = Stroke(width * unit))
}

/** A linear gradient from [from] to [to], given as fractions of the bounds of a circle [diameter] units across,
 * as XAML's relative StartPoint and EndPoint are. */
private fun DrawScope.across(diameter: Float, from: Offset, to: Offset, vararg stops: Pair<Float, Color>): Brush {
    val side = diameter * unit
    val left = center.x - side / 2
    val top = center.y - side / 2
    return Brush.linearGradient(
        *stops,
        start = Offset(left + from.x * side, top + from.y * side),
        end = Offset(left + to.x * side, top + to.y * side),
    )
}
