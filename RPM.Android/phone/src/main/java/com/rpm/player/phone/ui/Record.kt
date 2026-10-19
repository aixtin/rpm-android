package com.rpm.player.phone.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import com.rpm.player.core.R as CoreR

// ── the wordmark ──

/**
 * The rpm wordmark as SVG path data. It has the same geometry as the iPhone's `RPMWordmark`
 * (RPMSharedUI/RecordDisc.swift) and the desktop splash screen. It can be drawn at any size on the first frame.
 */
object Wordmark {
    private const val DATA =
        "M109 -143H102C85 -143 73 -136 66 -121V-140H18V0H67V-55C67 -81 76 -94 93 -94C98 -94 103 " +
            "-94 109 -93ZM207 -34C189 -34 180 -46 180 -71C180 -94 189 -106 207 -106C226 -106 236 -94 " +
            "236 -71C236 -46 226 -34 207 -34ZM218 3C259 3 284 -29 284 -69C284 -110 260 -143 216 " +
            "-143C199 -143 187 -137 180 -126V-140H132V52H178V-14C186 -3 199 3 218 3ZM525 -96C525 -127 " +
            "510 -143 480 -143C462 -143 448 -136 437 -123C427 -136 414 -143 399 -143C383 -143 369 " +
            "-136 358 -122V-140H312V0H361V-79C361 -96 367 -104 378 -104C389 -104 395 -97 395 -83V0H442" +
            "V-79C442 -96 448 -104 459 -104C470 -104 476 -97 476 -83V0H525Z"

    private val mark = VectorMark(DATA)

    /** How wide it is for its height. */
    val aspect: Float get() = mark.aspect

    fun fitted(box: Rect): Path = mark.fitted(box)
}

/**
 * A mark drawn from SVG path data. [fitted] scales it evenly to fit a box and centres it, as the iPhone's
 * `VectorPath.fitted` does.
 */
class VectorMark(data: String) {
    private val drawn: Path = PathParser().parsePathString(data).toPath()
    private val bounds: Rect = drawn.getBounds()

    /** How wide it is for its height. */
    val aspect: Float = if (bounds.height > 0) bounds.width / bounds.height else 1f

    fun fitted(box: Rect): Path {
        val scale = min(box.width / bounds.width, box.height / bounds.height)
        val width = bounds.width * scale
        val height = bounds.height * scale
        return Path().apply {
            addPath(drawn)
            transform(
                Matrix().apply {
                    translate(box.left + (box.width - width) / 2, box.top + (box.height - height) / 2)
                    scale(scale, scale)
                    translate(-bounds.left, -bounds.top)
                },
            )
        }
    }
}

/**
 * The icon for the artists view of the library. It is the iPhone's `ArtistMark` (RPMUI/ArtistMark.swift): a
 * person and a microphone. Font Awesome has no glyph for this.
 */
val ArtistMark = VectorMark(
    "M254.8 225.6Q224.4 256 181.5 256Q138.5 256 108.2 225.6Q77.8 195.2 77.8 152.3" +
    "Q77.8 109.4 108.2 79Q138.5 48.6 181.5 48.6Q224.4 48.6 254.8 79Q285.2 109.4 285.2 152.3" +
    "Q285.2 195.2 254.8 225.6ZM233.3 398.6Q233.3 436.7 267.3 463.4H38.9Q22.7 463.4 11.3 452.1" +
    "Q0 440.7 0 424.5V390.5Q0 345.9 32 313.9Q64 281.9 108.6 281.9H122.3Q150.7 294.9 181.5 294.9" +
    "Q212.3 294.9 240.6 281.9H254.4Q294.9 281.9 325.7 309.5Q285.2 315.9 259.2 340.7" +
    "Q233.3 365.4 233.3 398.6ZM353.3 303.5V210.2Q353.3 186.9 369.7 170.5Q386 154.2 409.3 154.2" +
    "Q432.7 154.2 449 170.5Q465.3 186.9 465.3 210.2V303.5Q465.3 326.9 449 343.2" +
    "Q432.7 359.5 409.3 359.5Q386 359.5 369.7 343.2Q353.3 326.9 353.3 303.5ZM502.7 266.2" +
    "Q506.8 266.2 509.4 268.8Q512 271.5 512 275.5V303.5Q512 342 486.6 370.9Q461.3 399.8 423.3 405" +
    "V424.9H456Q460.1 424.9 462.7 427.5Q465.3 430.1 465.3 434.2V443.5Q465.3 447.6 462.7 450.2" +
    "Q460.1 452.9 456 452.9H362.7Q358.6 452.9 356 450.2Q353.3 447.6 353.3 443.5V434.2" +
    "Q353.3 430.1 356 427.5Q358.6 424.9 362.7 424.9H395.3V405Q357.4 399.8 332.1 369.2" +
    "Q306.7 338.5 306.7 298.9V275.5Q306.7 271.5 309.3 268.8Q311.9 266.2 316 266.2H325.3" +
    "Q329.4 266.2 332.1 268.8Q334.7 271.5 334.7 275.5V300Q334.7 329.8 353.9 352.5" +
    "Q373.2 375.3 401.8 377.6Q435 381.1 459.5 358.7Q484 336.2 484 303.5V275.5" +
    "Q484 271.5 486.6 268.8Q489.3 266.2 493.3 266.2Z",
)

/** [mark] in [color], filling [modifier]'s box as far as its shape allows. */
@Composable
fun MarkView(mark: VectorMark, color: Color, modifier: Modifier) {
    Canvas(modifier) { drawPath(mark.fitted(Rect(Offset.Zero, size)), color) }
}

/** The wordmark in [color], filling [modifier]'s box as far as its shape allows. */
@Composable
fun WordmarkView(color: Color, modifier: Modifier) {
    Canvas(modifier) { drawPath(Wordmark.fitted(Rect(Offset.Zero, size)), color) }
}

// ── the pressing ──

/**
 * A record pressed in one colour. This matches the iPhone's `VinylPressing` (RPMSharedUI/VinylPressing.swift).
 * It uses one render, `vinyl_record.png`, which is the same file the iPhone ships. The render is multiplied
 * by a hue scaled to unit luminance. So the colour changes, but the brightness does not. The brightness is
 * the light, the grooves and the rim.
 */
object VinylPressing {
    /** How far the hue is applied. At 0 the vinyl stays black. */
    private const val SATURATION = 0.88

    /** The highest value any one channel may reach. Above it, the highlight would clip. */
    private const val CEILING = 3.2

    /** Rec. 709 luma weights. */
    private const val LUMA_R = 0.2126
    private const val LUMA_G = 0.7152
    private const val LUMA_B = 0.0722

    /** The default pressing: the app's azure colour over black. It is shown when nothing is playing. */
    val HOUSE_HUE: Int = android.graphics.Color.rgb(0x01, 0x1C, 0x34)

    /** One pressing per colour, kept for the life of the process, as on iPhone. The null key is black. */
    private val held = HashMap<Int?, ImageBitmap>()

    /**
     * The record in [hue], or black for null. Runs off the main thread, because it processes about a million pixels.
     */
    suspend fun press(context: Context, hue: Int?): ImageBitmap? {
        synchronized(held) { held[hue] }?.let { return it }
        val pressed = withContext(Dispatchers.Default) {
            val neutral = BitmapFactory.decodeResource(context.resources, CoreR.drawable.vinyl_record) ?: return@withContext null
            val out = Bitmap.createBitmap(neutral.width, neutral.height, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(neutral, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix(hue)) })
            neutral.recycle()
            out.asImageBitmap()
        } ?: return null
        synchronized(held) { held[hue] = pressed }
        return pressed
    }

    /**
     * One row per channel, each changing only its own channel. Black uses a grey matrix, not a hue of zero.
     * The render's body is blue-black, so only its blue is removed. Alpha is left alone, so the disc stays round.
     */
    private fun matrix(hue: Int?): ColorMatrix {
        if (hue == null) return grey()
        val r = android.graphics.Color.red(hue) / 255.0
        val g = android.graphics.Color.green(hue) / 255.0
        val b = android.graphics.Color.blue(hue) / 255.0
        val brightness = r * LUMA_R + g * LUMA_G + b * LUMA_B
        if (brightness <= 1e-6) return grey()
        val press = doubleArrayOf(r / brightness, g / brightness, b / brightness)
        val peak = press.max()
        if (peak > CEILING) for (i in 0..2) press[i] = 1 + (press[i] - 1) * ((CEILING - 1) / (peak - 1))
        for (i in 0..2) press[i] = 1 + (press[i] - 1) * SATURATION
        return ColorMatrix(
            floatArrayOf(
                press[0].toFloat(), 0f, 0f, 0f, 0f,
                0f, press[1].toFloat(), 0f, 0f, 0f,
                0f, 0f, press[2].toFloat(), 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun grey(): ColorMatrix {
        val row = floatArrayOf(LUMA_R.toFloat(), LUMA_G.toFloat(), LUMA_B.toFloat(), 0f, 0f)
        return ColorMatrix(row + row + row + floatArrayOf(0f, 0f, 0f, 1f, 0f))
    }
}

// ── the record ──

/** The shadow a record casts. The phone's shadow is round and concentric, and reaches 8% past the edge. */
enum class Halo { Round }

/**
 * The turning record. It matches the iPhone's `RecordDisc` (RPMSharedUI/RecordDisc.swift) in every figure.
 * The pressing and its halo stand still, and only the label turns. Real grooves are concentric, so their
 * turning would not show, and a still ring keeps the light where the lamp is.
 *
 * The label shows the rpm mark at its dim strength, pressed in rather than printed on. The album's own label,
 * and a photo of a disc, are not supported yet.
 */
@Composable
fun RecordDisc(diameter: Dp, modifier: Modifier = Modifier, halo: Halo = Halo.Round, spinning: Boolean = true, hue: Int? = VinylPressing.HOUSE_HUE) {
    val context = LocalContext.current
    val pressed by produceState<ImageBitmap?>(null, hue) { value = VinylPressing.press(context, hue) }
    val started = remember { System.nanoTime() }
    val now = remember { mutableLongStateOf(started) }
    LaunchedEffect(spinning) {
        while (spinning) withFrameNanos { now.longValue = it }
    }

    Box(modifier.size(diameter * HALO_SCALE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter * HALO_SCALE)) {
            val d = diameter.toPx()
            val angle = if (spinning) angle(after = (now.longValue - started).coerceAtLeast(0) / 1e9) else 0.0
            val orbit = d * WOBBLE
            val wobble = Offset((orbit * cos(Math.toRadians(angle))).toFloat(), (orbit * sin(Math.toRadians(angle))).toFloat())
            translate(wobble.x, wobble.y) { drawRecord(d, angle.toFloat(), pressed, halo) }
        }
    }
}

private fun DrawScope.drawRecord(d: Float, angle: Float, pressed: ImageBitmap?, halo: Halo) {
    val c = center

    // The halo. It is a separate shape, not part of the record, so it does not turn with it.
    val spread = when (halo) { Halo.Round -> HALO_SCALE }
    drawCircle(
        Brush.radialGradient(
            0.86f to Color.Black.copy(alpha = 0xB3 / 255f),
            0.94f to Color.Black.copy(alpha = 0x59 / 255f),
            1f to Color.Transparent,
            center = c,
            radius = d * spread / 2,
        ),
        radius = d * spread / 2,
        center = c,
    )

    // The pressing. Until it is ready, the disc is drawn black, because drawing nothing would look like a hole.
    if (pressed != null) {
        drawImage(
            pressed,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(pressed.width, pressed.height),
            dstOffset = IntOffset((c.x - d / 2).toInt(), (c.y - d / 2).toInt()),
            dstSize = IntSize(d.toInt(), d.toInt()),
        )
    } else {
        drawCircle(Color(0xFF0A0A0A), radius = d / 2, center = c)
    }

    // The label. This is the only part that turns.
    rotate(angle, pivot = c) {
        val edge = d * LABEL_EDGE
        drawCircle(Color(0xFF0A0A0A), radius = edge / 2, center = c)
        val markWidth = d * MARK_WIDTH
        val markHeight = d * MARK_HEIGHT
        val top = c.y - edge / 2 + d * MARK_DROP
        drawPath(Wordmark.fitted(Rect(Offset(c.x - markWidth / 2, top), Size(markWidth, markHeight))), Color.White.copy(alpha = 0x40 / 255f))
        val line = max(0.5f, d / 200)
        drawCircle(Color.Black.copy(alpha = 0x59 / 255f), radius = edge / 2 - line / 2, center = c, style = Stroke(line))
    }

    // The spindle hole. It is in the centre, does not turn, and is drawn over everything.
    val spindle = d * SPINDLE_EDGE
    drawCircle(Color(0xFF0B111A), radius = spindle / 2, center = c)
    val rim = max(0.5f, d / 400)
    drawCircle(Color.White.copy(alpha = 0x22 / 255f), radius = spindle / 2 - rim / 2, center = c, style = Stroke(rim))
}

/** One turn takes 1.8 s, which is 33 and a third turns a minute. */
private const val REVOLUTION = 1.8

/** Seconds for the record to reach full speed. */
private const val SPIN_UP = 1.0

/** The orbit of the off-centre pressing, as a fraction of the disc. */
private const val WOBBLE = 0.005f

/** The size of the halo's box, compared with the disc. */
private const val HALO_SCALE = 1.16f

// Fractions of the 200-unit box that the desktop draws the disc in.
private const val LABEL_EDGE = 66f / 200f
private const val MARK_WIDTH = 28.6f / 200f
private const val MARK_HEIGHT = 11f / 200f
private const val MARK_DROP = 16.3f / 200f
private const val SPINDLE_EDGE = 4.8f / 200f

/**
 * The angle of the record after [after] seconds from rest. It ramps up to full speed with a smoothstep curve.
 * The result depends only on the clock, not on how many frames were drawn.
 */
internal fun angle(after: Double): Double {
    if (after <= 0) return 0.0
    val turned = if (after >= SPIN_UP) {
        SPIN_UP / 2 + (after - SPIN_UP)
    } else {
        val u = after / SPIN_UP
        SPIN_UP * (u * u * u - u * u * u * u / 2)
    }
    return (turned / REVOLUTION * 360) % 360
}
