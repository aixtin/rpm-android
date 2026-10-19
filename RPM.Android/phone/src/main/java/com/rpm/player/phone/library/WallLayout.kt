package com.rpm.player.phone.library

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How a wall of covers is divided into columns. It matches the iPhone's `WallLayout`
 * (RPMDesign/WallLayout.swift). Sizes are in points (dp), as on iPhone.
 */
object WallLayout {
    /** How many cards fit across [available] at [targetStride]: the nearest whole column, at least [min]. */
    fun columns(available: Double, targetStride: Double, min: Int = 1): Int {
        if (targetStride <= 0 || !available.isFinite() || available <= 0) return min
        return max(min, (available / targetStride).roundToInt())
    }

    /**
     * The stride that a row really gets once it is split into whole cards. It is rounded down, so the last
     * card is never pushed off the edge. It is capped at [maxStride].
     */
    fun stride(available: Double, columns: Int, maxStride: Double): Double {
        val n = max(1, columns)
        if (!available.isFinite() || available <= 0) return maxStride
        return min(max(floor(available / n), 1.0), maxStride)
    }

    /**
     * The column count for a target stride, kept between [minColumns] and [maxColumns]. Also returns the
     * stride that the columns are laid out at.
     */
    fun fit(available: Double, targetStride: Double, maxColumns: Int, minColumns: Int = 1): Pair<Int, Double> {
        val low = max(1, minColumns)
        val high = max(low, maxColumns)
        val count = columns(available, targetStride, low).coerceIn(low, high)
        return count to stride(available, count, max(1.0, available))
    }

    /**
     * The whole sizing of a wall: how many covers go across [available], and how wide each one is drawn.
     * The margins are part of the sizing. A row of n covers is n strides, with the last gap hanging off the
     * end. So the usable width is the wall inside its [edge]s, plus one [gutter] given back.
     */
    fun wall(available: Double, targetStride: Double, edge: Double, gutter: Double, maxColumns: Int, minColumns: Int = 1): Pair<Int, Double> {
        val room = max(1.0, available - 2 * edge + gutter)
        val (columns, stride) = fit(room, targetStride, maxColumns, minColumns)
        return columns to max(1.0, stride - gutter)
    }
}

/**
 * How big the text under a cover is, for the cover's size. It matches the iPhone's `AlbumCaption`. The scale
 * factor is the square root of the change in the cover's edge, clamped once. So the text keeps a steady share
 * of the card's area. Every card in a row has the same art edge, so the cards get the same figures. That keeps
 * a row at one height.
 */
data class AlbumCaption(
    val art: Double,
    val album: Float,
    val artist: Float,
    val badge: Float,
    val albumLine: Double,
    val artistLine: Double,
) {
    /** The card's whole height: the cover, the two text lines and the gaps. */
    val card: Double get() = art + ART_GAP + albumLine + LINE_GAP + artistLine + BELOW

    companion object {
        /** The art edge that the figures were drawn for: two columns on a normal phone. */
        const val ANCHOR = 170.0
        const val ART_GAP = 10.0
        const val LINE_GAP = 2.0
        const val BELOW = 10.0

        private const val ANCHOR_ALBUM = 15.0
        private const val ANCHOR_ARTIST = 14.0
        private const val ANCHOR_BADGE = 13.0
        private const val ANCHOR_ALBUM_LINE = 20.0
        private const val ANCHOR_ARTIST_LINE = 19.0
        private const val SMALLEST = 11.0
        private const val LARGEST = 20.0

        fun of(art: Double): AlbumCaption {
            val factor = (if (art > 0) sqrt(art / ANCHOR) else 1.0).coerceIn(SMALLEST / ANCHOR_ALBUM, LARGEST / ANCHOR_ALBUM)
            fun tenths(v: Double) = ((v * 10).roundToInt() / 10.0).toFloat()
            return AlbumCaption(
                art = if (art > 0) art else ANCHOR,
                album = tenths(ANCHOR_ALBUM * factor),
                artist = tenths(ANCHOR_ARTIST * factor),
                badge = tenths(ANCHOR_BADGE * factor),
                // Whole points. A line height sets the height of a row.
                albumLine = (ANCHOR_ALBUM_LINE * factor).roundToInt().toDouble(),
                artistLine = (ANCHOR_ARTIST_LINE * factor).roundToInt().toDouble(),
            )
        }
    }
}
