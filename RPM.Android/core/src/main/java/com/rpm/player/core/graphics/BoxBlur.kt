package com.rpm.player.core.graphics

import android.graphics.Bitmap

/** Blurs [bitmap] in place, including alpha, with three box passes: each pass is horizontal and then vertical. */
fun boxBlur(bitmap: Bitmap, radius: Int) {
    val w = bitmap.width
    val h = bitmap.height
    val a = IntArray(w * h)
    val b = IntArray(w * h)
    bitmap.getPixels(a, 0, w, 0, 0, w, h)
    repeat(3) {
        pass(a, b, w, h, radius, horizontal = true)
        pass(b, a, w, h, radius, horizontal = false)
    }
    bitmap.setPixels(a, 0, w, 0, 0, w, h)
}

private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val span = 2 * r + 1
    for (line in 0 until lines) {
        var sa = 0
        var sr = 0
        var sg = 0
        var sb = 0
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) src[line * w + c] else src[c * w + line]
        }
        for (i in -r..r) {
            val p = at(i)
            sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
        }
        for (i in 0 until length) {
            val out = ((sa / span) shl 24) or ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
            if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
            val add = at(i + r + 1)
            val drop = at(i - r)
            sa += (add ushr 24) - (drop ushr 24)
            sr += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
            sb += (add and 0xFF) - (drop and 0xFF)
        }
    }
}
