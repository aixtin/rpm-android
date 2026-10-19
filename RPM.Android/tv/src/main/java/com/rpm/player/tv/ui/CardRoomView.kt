package com.rpm.player.tv.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rpm.player.core.graphics.boxBlur
import com.rpm.player.core.motion.LocalMotion
import com.rpm.player.core.motion.Motion
import com.rpm.player.core.room.CardRoom
import com.rpm.player.core.room.RoomPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * A card's room ([CardRoom]) filling [modifier]'s box, stretched smooth from its small picture. A card
 * that does not [drift] is painted once, at the pose its colours put it in, and kept in a small cache
 * so a row scrolled back into view does not paint it again. A drifting card (the banner's front card)
 * paints about 25 times a second, off the main thread, into two pictures it takes turns with.
 * [fewest] is the fewest pixels either side of its picture may have (32 for a square card). [apart]
 * moves the pose along the drift ([CardRoom.still]): a generated list's kind number, otherwise 0.
 *
 * At the motion level [Motion.Still] ([LocalMotion]) no card drifts; the judge never steps there, so on the
 * screens a drifting card always drifts.
 *
 * [soften] blurs the picture after it is painted: a Gaussian of that share of the picture's short side, 0 for none.
 * The radio's essential cards are softened ([ESSENTIAL_SOFTEN]); every other card is drawn as the room paints it.
 *
 * Every drifting card shows its next picture on the same tick of one clock ([DriftClock]): each paints the
 * picture for the next tick while the last one shows, and all of them change in one frame. With a clock each,
 * two or three drifting cards on home would make the window draw 40 to 75 times a second instead of 25.
 */
@Composable
fun CardRoomView(palette: RoomPalette, apart: Int, drift: Boolean, fewest: Int, soften: Float, modifier: Modifier) {
    @Suppress("NAME_SHADOWING")
    // A card on a screen kept under another does not drift: its pictures would be painted 25 times a second for nobody.
    val drift = drift && LocalMotion.current < Motion.Still && LocalOnTop.current
    val clock = LocalDriftClock.current
    BoxWithConstraints(modifier) {
        val (w, h) = CardRoom.small(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), fewest)
        // The picture the cache already holds for these colours and this size, if any: a still card, and a drifting
        // card's first picture (the same pose), are then drawn from the first frame instead of after a paint.
        var picture by remember { mutableStateOf<ImageBitmap?>(STILL.get("$palette:$apart:$w:$h:$soften")) }
        // The blur's box radius at this picture's size: three passes of radius r are about a Gaussian of sqrt(r(r+1)).
        val blur = if (soften <= 0f) 0 else Math.round(sqrt(sq(soften * minOf(w, h)) + 0.25f) - 0.5f).coerceAtLeast(1)
        var frame by remember { mutableIntStateOf(0) }

        LaunchedEffect(palette, apart, w, h, drift, blur) {
            val key = "$palette:$apart:$w:$h:$soften"
            if (!drift) {
                picture = STILL.get(key) ?: withContext(Dispatchers.Default) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    CardRoom.paint(bitmap, palette, CardRoom.still(palette, apart), IntArray(w * h), CardRoom.Orbits())
                    if (blur > 0) boxBlur(bitmap, blur)
                    bitmap.asImageBitmap()
                }.also { STILL.put(key, it) }
                return@LaunchedEffect
            }
            val pictures = Array(2) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
            val images = pictures.map { it.asImageBitmap() }
            val pixels = IntArray(w * h)
            val orbits = CardRoom.Orbits()
            val start = System.nanoTime()
            var turn = 0
            // The picture for the first tick, then on each tick: show it and paint the next into the other
            // bitmap, which is not on screen until the tick after.
            withContext(Dispatchers.Default) {
                CardRoom.paint(pictures[turn], palette, CardRoom.still(palette, apart), pixels, orbits)
                if (blur > 0) boxBlur(pictures[turn], blur)
            }
            snapshotFlow { clock.tick }.collect {
                picture = images[turn]
                frame++
                turn = 1 - turn
                val seconds = CardRoom.still(palette, apart) + (System.nanoTime() - start) / 1e9 + DriftClock.TICK_MS / 1e3
                withContext(Dispatchers.Default) {
                    CardRoom.paint(pictures[turn], palette, seconds, pixels, orbits)
                    if (blur > 0) boxBlur(pictures[turn], blur)
                }
            }
        }

        // Drawn at a small size and stretched: dithered, so its steps do not show as rings ([dithered]).
        Canvas(androidx.compose.ui.Modifier.matchParentSize().dithered()) {
            frame
            val image = picture ?: return@Canvas
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

private val STILL = LruCache<String, ImageBitmap>(64)

private fun sq(x: Float) = x * x

/**
 * How soft the radio's essential cards are: a Gaussian of 11% of the picture's short side, about radius 3 at the
 * 32 pixels a card is painted at. This keeps the backgrounds of the essential stations calm behind the text.
 */
const val ESSENTIAL_SOFTEN = 0.11f

/**
 * The one clock every drifting card's room changes on: [tick] goes up every [TICK_MS] while [run] runs, which
 * `RpmApp` does for the app's life. Read on the main thread.
 */
@Stable
class DriftClock {
    var tick by mutableIntStateOf(0)
        private set

    /** Ticks until cancelled. */
    suspend fun run() {
        while (true) {
            delay(TICK_MS)
            tick++
        }
    }

    companion object {
        /** 25 pictures a second, as the desktop's drifting cards. */
        const val TICK_MS = 40L
    }
}

/** The app's [DriftClock]. Provided by `RpmApp`. */
val LocalDriftClock = staticCompositionLocalOf<DriftClock> { error("No DriftClock provided") }
