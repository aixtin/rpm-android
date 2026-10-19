package com.rpm.player.core.radio

import kotlin.math.max
import kotlin.math.min

/**
 * Where everything is printed on the phone's tuning dial. It matches the iOS app's `DialCard`
 * (RPMRadio/DialCard.swift) and is the arithmetic of the desktop's RadioDial, lifted out of the
 * drawing so it can be tested. Stations are set out from left to right. Each is printed as its call
 * sign ([StationSign]) followed by a square that is its place on the band, stacked into as many rows
 * as the card needs. The card can be wider than the glass and slide under it. Sizes are in points.
 *
 * The TV app draws its own dial (`DialFace` in `:tv`), which is not this one.
 */
class DialCard private constructor(
    /** How wide the printed card is: the glass width, until the call signs need more room. */
    val scaleWidth: Double,
    /** Where the graduations are, on the card. */
    val ticks: List<Double>,
    /** The stations that got a sign printed. A station that cannot clear its neighbours on any row keeps
     * its graduation and loses its caption. */
    val printed: List<Printed>,
    val nameRows: Int,
    /** Where the block sits vertically: the needle's head, the rows of signs and the graduations are
     * centred as one block in the room below the readout. */
    val namesTop: Double,
    val scaleTop: Double,
    val scaleBottom: Double,
    /** How far in the first mark sits. A sign is printed to the left of its square, so the first station
     * sits in by the room of the widest sign. */
    val leftEdge: Double,
) {
    /** One printed station: its row, its sign, where its square sits on the card, and where its sign starts. */
    data class Printed(val station: Int, val row: Int, val caption: String, val markX: Double, val nameLeft: Double, val width: Double)

    private val span: Double get() = max(1.0, scaleWidth - leftEdge - EDGE)

    /** Where a station's mark sits on the card. */
    fun markOf(station: Int, count: Int): Double {
        val fraction = if (count <= 0) 0.5 else (station + 0.5) / count
        return leftEdge + fraction.coerceIn(0.0, 1.0) * span
    }

    /** Which station a point on the card is nearest to: a release settles on a station, as a dial with detents does. */
    fun nearest(cardX: Double, count: Int): Int? {
        if (count <= 0) return null
        val fraction = ((cardX - leftEdge) / span).coerceIn(0.0, 1.0)
        return (fraction * count).toInt().coerceIn(0, count - 1)
    }

    /** How far the card must slide for the point at [cardX] to sit at [glassX], limited to what the card
     * can travel, so it is never dragged past its own ends. */
    fun slide(cardX: Double, glassX: Double, glass: Double): Double =
        min(max(glassX - cardX, -max(0.0, scaleWidth - glass)), 0.0)

    companion object {
        /** The height of one row of call signs (baseline to baseline), and the clear space between two signs on one row. */
        const val NAME_LINE = 11.0
        const val NAME_GAP = 7.0

        /** The square at the station's place, and the gap between it and its sign. */
        const val SQUARE_SIZE = 6.5
        const val SQUARE_GAP = 4.0
        const val SQUARE_SLOT = SQUARE_GAP + SQUARE_SIZE

        /** How tall the graduations stand, and how far the needle's chevron stands above them. */
        const val SCALE_SPAN = 16.0
        const val POINTER_RISE = 13.0

        /** The most rows of signs a card prints. With more than six, the signs read like a table of contents. */
        const val MOST_ROWS = 6

        /** The signs' size, and a generous estimate of a character's width at that size. A low estimate
         * would clip long signs. A high estimate only leaves more space. */
        const val NAME_SIZE = 9.0
        const val CHAR_WIDTH = NAME_SIZE * 0.7

        /** How many glass widths the card may grow to before signs are left off again. */
        const val MOST_SCREENS = 6.0

        /** The margin that the needle keeps from the glass's right edge. */
        const val EDGE = 16.0

        /** Lays out a card for one call sign per station, in the order they are listed. */
        fun of(captions: List<String>, glass: Double, height: Double = 176.0, readoutBottom: Double = 54.0): DialCard {
            val count = captions.size
            // As many rows as there is room for under the readout, up to six.
            val room = max(0.0, height - 8 - readoutBottom)
            val maxRows = min(max(((room - POINTER_RISE - SCALE_SPAN) / NAME_LINE).toInt(), 1), MOST_ROWS)
            val widths = captions.map { max(8.0, it.length * CHAR_WIDTH) }

            // How long the card has to be, worked back from the width of the signs: six short names fit the
            // glass, and a hundred long ones make it grow.
            val ink = widths.sumOf { it + SQUARE_SLOT + NAME_GAP }
            val wanted = ink / maxRows + 32
            val scaleWidth = min(max(wanted, glass), max(glass, glass * MOST_SCREENS))

            val widest = widths.maxOrNull() ?: 0.0
            val leftEdge = max(EDGE, widest + SQUARE_GAP + SQUARE_SIZE / 2)
            val inset = max(1.0, scaleWidth - leftEdge - EDGE)
            fun at(fraction: Double) = leftEdge + fraction.coerceIn(0.0, 1.0) * inset
            fun fraction(i: Int) = if (count <= 0) 0.5 else (i + 0.5) / count

            // Too crowded for a graduation per station: use evenly spaced graduations and no signs.
            val pitch = if (count > 0) inset / count else 0.0
            val perStation = count > 0 && pitch >= 4
            val ticks = if (perStation) {
                (0 until count).map { at(fraction(it)) }
            } else {
                val howMany = max(8, (inset / 11).toInt())
                (0 until howMany).map { at((it + 0.5) / howMany) }
            }

            // Each sign and its square go on the topmost row whose last entry they clear. The square sits on
            // the station's place, so the sign reaches back to its left.
            val printed = ArrayList<Printed>()
            var nameRows = 0
            if (perStation) {
                val rowEnd = DoubleArray(maxRows) { Double.NEGATIVE_INFINITY }
                for (i in 0 until count) {
                    val mark = at(fraction(i))
                    val left = mark - SQUARE_SIZE / 2 - SQUARE_GAP - widths[i]
                    for (row in 0 until maxRows) {
                        if (left < rowEnd[row]) continue
                        rowEnd[row] = mark + SQUARE_SIZE / 2 + NAME_GAP
                        printed += Printed(i, row, captions[i], mark, left, widths[i])
                        nameRows = max(nameRows, row + 1)
                        break
                    }
                }
            }

            val names = nameRows * NAME_LINE
            val block = POINTER_RISE + names + SCALE_SPAN
            val top = readoutBottom + max(0.0, (room - block) / 2) + POINTER_RISE
            return DialCard(scaleWidth, ticks, printed, nameRows, top, top + names, top + names + SCALE_SPAN, leftEdge)
        }
    }
}
