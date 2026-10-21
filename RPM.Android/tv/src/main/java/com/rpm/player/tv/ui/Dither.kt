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

/**
 * The grain of frosted glass, drawn over a glass card's tint: the same kind of noise as [dithered], but strong
 * enough to be seen ([FROST_LEVELS]), so a card over something already soft (the room) still reads as glass and not
 * as a flat grey panel.
 */
fun Modifier.frosted(): Modifier = drawWithContent {
    drawContent()
    drawRect(FROST)
}

private val NOISE: ShaderBrush by lazy { noise(0x5EED, NOISE_LEVELS) }

private val FROST: ShaderBrush by lazy { noise(0xF205, FROST_LEVELS) }

private fun noise(seed: Int, levels: Int): ShaderBrush {
    val random = Random(seed)
    val pixels = IntArray(NOISE_TILE * NOISE_TILE) {
        // White lifts a pixel, black lowers it; the alpha is how far, in 255ths.
        val alpha = random.nextInt(levels + 1)
        val white = random.nextBoolean()
        (alpha shl 24) or (if (white) 0xFFFFFF else 0)
    }
    val bitmap = Bitmap.createBitmap(pixels, NOISE_TILE, NOISE_TILE, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** The noise tile's side, in pixels. */
private const val NOISE_TILE = 128

/** The most the noise moves a pixel, in 255ths of full white. */
private const val NOISE_LEVELS = 3

/** The most the frost moves a pixel, in 255ths of full white: a fine grain seen from the sofa. */
private const val FROST_LEVELS = 14
