package com.rpm.player.core.room

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The app's ten accents (RPM.Desktop/Assets/RpmPalette.xaml), in ring order, and the rule for snapping a
 * colour to the nearest of them: the iOS app's `RPMPalette.nearest`, with the same numbers.
 */
object RpmPalette {
    val RING: List<Rgb> = listOf(
        Rgb.of(0xD2, 0x31, 0x0E), Rgb.of(0xCC, 0x6E, 0x11), Rgb.of(0xEF, 0xAF, 0x12), Rgb.of(0xAD, 0xD0, 0x38),
        Rgb.of(0x13, 0x4C, 0x27), Rgb.of(0x03, 0x71, 0xD0), Rgb.of(0x47, 0x50, 0xAE), Rgb.of(0xAC, 0x52, 0x8D),
        Rgb.of(0xEA, 0x15, 0x4E), Rgb.of(0xEE, 0x07, 0x09),
    )

    /**
     * The accent nearest [colour], or null where the colour has too little colour in it (Oklab chroma
     * under 0.018) to be nearer one hue than another: a grey stays grey and does not take whichever
     * accent is least saturated. Compared in Oklch: hue first, then chroma, then lightness at half
     * weight, so a dark blue is still blue.
     */
    fun nearest(colour: Rgb): Rgb? {
        val (l, chroma, hue) = oklch(colour)
        if (chroma < NEUTRAL_CHROMA) return null
        return RING.minBy { distance(l, chroma, hue, it) }
    }

    private fun distance(l: Double, chroma: Double, hue: Double, accent: Rgb): Double {
        val (al, ac, ah) = oklch(accent)
        var dh = abs(hue - ah)
        if (dh > PI) dh = 2 * PI - dh
        val dc = (chroma - ac) * CHROMA_WEIGHT
        val dl = (l - al) * LIGHTNESS_WEIGHT
        return dh * dh + dc * dc + dl * dl
    }

    private fun oklch(colour: Rgb): Triple<Double, Double, Double> {
        val r = linear(colour.r)
        val g = linear(colour.g)
        val b = linear(colour.b)
        val cl = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val cm = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val cs = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val l = 0.2104542553 * cl + 0.7936177850 * cm - 0.0040720468 * cs
        val a = 1.9779984951 * cl - 2.4285922050 * cm + 0.4505937099 * cs
        val bb = 0.0259040371 * cl + 0.7827717662 * cm - 0.8086757660 * cs
        return Triple(l, sqrt(a * a + bb * bb), atan2(bb, a))
    }

    /** One channel with the sRGB transfer curve undone. */
    private fun linear(channel: Float): Double {
        val v = channel.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private const val NEUTRAL_CHROMA = 0.018
    private const val CHROMA_WEIGHT = 1.0
    private const val LIGHTNESS_WEIGHT = 0.5
}
