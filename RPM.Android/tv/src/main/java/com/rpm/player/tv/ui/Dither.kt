package com.rpm.player.tv.ui

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Fine noise drawn over a soft picture that is shown larger than it was made: the room (an eighth of the screen,
 * stretched), a card's room, a blurred photograph, the page background dimmed. Stretched, a picture's 8-bit
 * steps become bands a few pixels wide, which read as rings around every glow and blur. The noise moves each
 * pixel up or down by at most [NOISE_LEVELS] of 255, at the screen's own pixels, so the bands break up and no
 * grain can be seen.
 *
 * One tile, made once and repeated; drawing it is one rectangle with a shader.
 */
fun Modifier.dithered(): Modifier = drawWithContent {
    drawContent()
    drawRect(NOISE)
}

private val NOISE: ShaderBrush by lazy {
    val random = Random(0x5EED)
    val pixels = IntArray(NOISE_TILE * NOISE_TILE) {
        // White lifts a pixel, black lowers it; the alpha is how far, in 255ths.
        val alpha = random.nextInt(NOISE_LEVELS + 1)
        val white = random.nextBoolean()
        (alpha shl 24) or (if (white) 0xFFFFFF else 0)
    }
    val bitmap = Bitmap.createBitmap(pixels, NOISE_TILE, NOISE_TILE, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** The noise tile's side, in pixels. */
private const val NOISE_TILE = 128

/** The most the noise moves a pixel, in 255ths of full white. */
private const val NOISE_LEVELS = 3
