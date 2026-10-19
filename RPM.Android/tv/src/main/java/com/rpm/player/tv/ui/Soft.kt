package com.rpm.player.tv.ui

import android.graphics.Bitmap
import com.rpm.player.core.graphics.boxBlur
import coil3.size.Size
import coil3.transform.Transformation

/**
 * A picture made soft enough to sit under a card as its ground: shrunk to [WIDTH] pixels across and
 * blurred at that size (the desktop's ArtScaler.Wash, a Gaussian blur of about 8 % of the width). It is drawn
 * stretched over the card, as the desktop does with its 320 px wash.
 */
class Soft : Transformation() {
    override val cacheKey: String = "soft:$WIDTH:$RADIUS"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val h = (input.height * WIDTH / input.width.coerceAtLeast(1)).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, WIDTH, h, true).let {
            if (it.config == Bitmap.Config.ARGB_8888 && it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true)
        }
        boxBlur(small, RADIUS)
        return small
    }

    private companion object {
        const val WIDTH = 64
        const val RADIUS = 4
    }
}
