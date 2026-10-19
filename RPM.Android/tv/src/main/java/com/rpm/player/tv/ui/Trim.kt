package com.rpm.player.tv.ui

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * A logo cut to its lettering: the smallest rectangle that holds every pixel with an alpha above [FLOOR].
 * The desktop's `ArtScaler.Trim` cuts it the same way before the banner draws it.
 *
 * The catalogues publish artist logos on a fixed canvas (800 by 310), whatever size the mark inside it is.
 * A short mark drawn as it comes sits in the middle of mostly empty space: it is scaled to a height that is
 * mostly empty and starts a third of the way in. When the logo is cut first, the banner's logo box holds the
 * lettering itself, as on the desktop. The floor is above zero because a soft drop shadow fades to an alpha of
 * one or two long before it reaches zero. A picture with no alpha, nothing visible or nothing to cut is
 * returned unchanged.
 */
class Trim : Transformation() {
    override val cacheKey: String = "trim:$FLOOR"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        val bitmap = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) <= FLOOR) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                bottom = y
            }
        }
        if (right < left || bottom < top) return bitmap
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    private companion object {
        const val FLOOR = 8
    }
}
