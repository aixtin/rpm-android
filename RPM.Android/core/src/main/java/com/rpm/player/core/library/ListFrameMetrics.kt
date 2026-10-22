package com.rpm.player.core.library

/**
 * Where a generated list's name is set in the band of its frame, and how big. It matches the iPhone's
 * `ListFrameMetrics` (RPMDesign/ListFrameMetrics.swift) and the desktop's `CoverOverlay.Letter`. The fractions are
 * measured in the frame drawing, which is 800 pixels square, and kept as fractions so they hold at any size.
 *
 * **Arithmetic, not a layout pass**, so every edge case can be checked by a test. y counts down, as in the drawing.
 */
object ListFrameMetrics {
    /** The middle of the band the name is set on, across the square, read from the "rpm" mark in the corner. */
    const val NAME_CENTRE = 0.90813f

    /** Where the lettering starts, down the square. */
    const val NAME_TOP = 0.0375f

    /** How tall the lettering is across the band. The name is turned on its side, so this sets the type's size. */
    const val NAME_CROSS = 0.08f

    /** How far down the square the name may run before it reaches the app's mark. A longer name is set smaller. */
    const val NAME_RUN = 0.8375f

    /**
     * The string every cover's size is measured on, so "Your mix" and "Loved and forgotten" are set at one size. It
     * has an ascender and a descender, the tallest span Latin text takes.
     */
    const val GAUGE = "fg"

    /** One ink box, measured on the glyphs' own outlines: where it starts from the pen and how wide it runs, and its
     * top and bottom about the baseline (both negative above the line). */
    data class Ink(val left: Float, val width: Float, val top: Float, val bottom: Float) {
        val height: Float get() = bottom - top
    }

    /** The type's size, and where the pen goes in the turned frame: [alongBand] cancels the name's own left bearing,
     * and [acrossBand] puts the middle of the gauge on the band's centre line. */
    data class Lettering(val size: Float, val alongBand: Float, val acrossBand: Float)

    /**
     * The size and the two offsets for [name] in a square of [edge], from a font that measures a string at a size.
     * Null where the font measures nothing at all; the frame is then drawn without a name rather than with boxes.
     */
    fun lettering(name: String, edge: Float, measuring: (String, Float) -> Ink): Lettering? {
        if (edge <= 0f || name.isEmpty()) return null
        val band = edge * NAME_CROSS
        var size = band
        // A font with no Latin letters measures the gauge as nothing; the name then sets the size.
        var gauged = measuring(GAUGE, size)
        if (gauged.height <= 0f) gauged = measuring(name, size)
        if (gauged.height <= 0f) return null
        size *= band / gauged.height
        var ink = measuring(name, size)
        if (ink.width <= 0f) return null
        if (ink.width > edge * NAME_RUN) {
            size *= (edge * NAME_RUN) / ink.width
            ink = measuring(name, size)
        }
        // Measured again at the size set, so the centre line is the one this font draws at.
        var across = measuring(GAUGE, size)
        if (across.height <= 0f) across = ink
        return Lettering(size, alongBand = -ink.left, acrossBand = -((across.top + across.bottom) / 2f))
    }

    /** Where the lettering is anchored in the square, before it is turned a quarter clockwise. */
    fun anchor(edge: Float): Pair<Float, Float> = Pair(edge * NAME_CENTRE, edge * NAME_TOP)
}
