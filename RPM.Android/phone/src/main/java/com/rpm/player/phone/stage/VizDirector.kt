package com.rpm.player.phone.stage

import kotlin.math.floor

/**
 * One arrangement of the space a marched visualiser draws: how many times it is folded around the axis, how far the
 * fold pushes the walls out, how hard it twists, how often the cells repeat, how much metal there is, and how much
 * of each cell the cross takes back out. The iPhone's `VizRoom` (RPMStages/VizDirector.swift). The six numbers are
 * not named after any one picture: the tunnel, the kaleidoscope and the lattice each read them their own way, so one
 * phrase of a record changes all three at once.
 */
data class VizRoom(val folds: Float, val arm: Float, val twist: Float, val ring: Float, val girth: Float, val cross: Float) {
    /** Which of the four solids this room is built from, picked by its cross-cut. */
    val solid: Float get() = floor(minOf(3.99f, (cross - 0.25f) / 0.95f * 4))

    companion object {
        /** The room the walls are in this frame. The fold count is taken whole: half a fold leaves a seam. */
        fun blend(was: VizRoom, now: VizRoom, t: Float): VizRoom {
            fun to(a: Float, b: Float) = a + (b - a) * t
            return VizRoom(now.folds, to(was.arm, now.arm), to(was.twist, now.twist), to(was.ring, now.ring), to(was.girth, now.girth), to(was.cross, now.cross))
        }
    }
}

/** One camera set-up and the room it looks at: everything a marched visualiser needs for a frame. */
data class VizShot(
    val now: VizRoom,
    val was: VizRoom,
    val blend: Float,
    val palette: Int,
    val orbit: Float,
    val advance: Float,
    val travelled: Float,
    val fov: Float,
    val sway: Float,
    val beats: Float,
    val pulse: Float,
    val drop: Float,
)

/**
 * The cutting room the marched visualisers share: given where the record is in beats, where the camera stands, which
 * arrangement of the space is up and how hard this instant hits. The iPhone's `VizDirector`, figure for figure, down
 * to its integer hash, so the same phrase gives the same room on every machine.
 *
 * **On the phones it always runs on the stand-in clock**: nothing here analyses a record, so the picture cuts and
 * moves at a steady 120 beats a minute and not to the music. The iPhone does the same.
 */
object VizDirector {
    const val PALETTES = 4
    const val BEATS_PER_CUT = 8
    const val BARS_PER_SECTION = 8
    private const val BLEND_BEATS = 2.0
    private const val FREE_BPM = 120.0

    /** The shot for [seconds] on the stand-in clock: seconds counted as beats at a steady pace, four to the bar. */
    fun free(seconds: Double): VizShot = shot(seconds * (FREE_BPM / 60), beatsPerBar = 4)

    fun shot(beats: Double, beatsPerBar: Int): VizShot {
        val perBar = maxOf(1, beatsPerBar)
        val perSection = (perBar * BARS_PER_SECTION).toDouble()
        val cut = floor(beats / BEATS_PER_CUT).toInt()
        val section = floor(beats / perSection).toInt()
        val a = hash(cut, 0)
        val c = hash(cut, 2)
        val intoBeat = beats - floor(beats)
        val pulse = ((1 - intoBeat) * (1 - intoBeat)).toFloat()
        // No record here has drops, so the drop is always 0.
        val drop = 0f
        val intoSection = beats - section * perSection
        val blend = (intoSection / BLEND_BEATS).coerceIn(0.0, 1.0).toFloat()
        return VizShot(
            now = room(section),
            was = room(section - 1),
            blend = blend,
            palette = (hash32(section, 11).toLong() and 0xFFFFFFFFL).rem(PALETTES).toInt(),
            orbit = (a - 0.5f) * 2.4f,
            advance = speed(cut),
            travelled = travelled(beats),
            fov = 0.55f + 0.45f * c,
            sway = (1 - drop) * (0.08f + 0.5f * hash(cut, 3)),
            beats = beats.toFloat(),
            pulse = pulse,
            drop = drop,
        )
    }

    private fun speed(cut: Int): Float = 0.9f + 1.4f * hash(cut, 1)

    /** How far the camera has flown by this beat: every finished shot at its own speed, plus the current one so far. */
    fun travelled(beats: Double): Float {
        val cut = floor(beats / BEATS_PER_CUT).toInt()
        val span = minOf(20_000, maxOf(-64, cut))
        var sum = 0.0
        if (span >= 0) {
            for (i in 0 until span) sum += speed(i)
        } else {
            for (i in span until 0) sum -= speed(i)
        }
        sum *= BEATS_PER_CUT
        return (sum + speed(span) * (beats - span * BEATS_PER_CUT)).toFloat()
    }

    fun room(section: Int): VizRoom = VizRoom(
        folds = Math.round(3 + 6 * hash(section, 21)).toFloat(),
        arm = 2.2f + 2.6f * hash(section, 22),
        twist = 0.05f + 0.30f * hash(section, 23),
        ring = 1.6f + 2.8f * hash(section, 24),
        girth = 0.035f + 0.075f * hash(section, 25),
        cross = 0.25f + 0.95f * hash(section, 26),
    )

    private fun hash(n: Int, which: Int): Float = (hash32(n, which) and 0xFF_FFFF).toFloat() / 0x100_0000.toFloat()

    /** The iPhone's 32-bit integer mix, in Kotlin's wrapping integers: the same bits on every machine. */
    private fun hash32(n: Int, which: Int): Int {
        var h = n * -1640531535 + which * -2048144777
        h = h xor (h ushr 15)
        h *= -2048144777
        h = h xor (h ushr 13)
        h *= -1028477379
        h = h xor (h ushr 16)
        return h
    }
}
