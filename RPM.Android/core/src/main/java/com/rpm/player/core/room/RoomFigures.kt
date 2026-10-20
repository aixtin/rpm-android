package com.rpm.player.core.room

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The room's animations, picked in the TV's settings. [Original] is the room every app draws
 * (RoomSource.Glsl); the others are the TV's own, drawn by the same renderer in the same eighth-size pass
 * with a program of their own ([RoomShaders]), so they cost what the original costs.
 */
enum class RoomStyle {
    /** The three families of lobes over the plane warped by the music ([RoomOrbits]). */
    Original,

    /** Two big balls on the bass and two small ones on the air, turning about the middle with fading
     * echoes behind them, over one big blurred ball in the third colour. */
    Yang,

    /** Blobs rising and sinking like a lava lamp's, melting into a pool at the floor and one at the top,
     * over one huge blurred ball in the third colour that swells with the middle. */
    Lava,

    /** Soft columns of light rising from a glowing floor into a dark top, each lit by one of the three
     * levels: the RPaMp skin's summed ribbons (make_rpamp_ground.py) stood upright and set moving. */
    RPaMp,
}

/**
 * Where the figures of [RoomStyle.Yang], [RoomStyle.Lava] and [RoomStyle.RPaMp] are this frame, and how
 * large and bright: worked out once per frame on the CPU, as [RoomOrbits] does for the original, so the
 * shaders spend nothing per pixel on numbers that are the same for every pixel. Written as vec4s, ready
 * for `glUniform4fv`. Allocation-free per frame.
 *
 * Each style's main motion runs on a clock of its own ([spin], [flow], [rise]) that [advance] moves on
 * faster while its level is high, so the music speeds the figures up without making them jump: a level
 * that scaled the time itself would throw every figure forward the moment the bass came in. A still room
 * is advanced by nothing and keeps its pose. Slow drifts that the music does not touch read the room's
 * own clock instead.
 */
class RoomFigures {
    /** Yang's two big balls, each followed by [ECHOES] echoes: (x, y, radius, weight). */
    val big = FloatArray(YANG_LOBES * 4)
    /** Yang's two small balls and their echoes, as [big]. */
    val small = FloatArray(YANG_LOBES * 4)
    /** Yang's blurred middle ball: (x, y, radius, unused). */
    val centre = FloatArray(4)

    /** Lava's blobs: (x, y, radius, how far from the tint to the glow its colour is). */
    val blobs = FloatArray(LAVA_BLOBS * 4)
    /** Lava's two pools: (floor's line, floor's thickness, top's line, top's thickness). */
    val pools = FloatArray(4)
    /** Lava's background ball: (x, y, radius, the phase of the pools' ripple). */
    val back = FloatArray(4)

    /** RPaMp's columns: (x at the floor, where it fades out, half-width, brightness). */
    val strandA = FloatArray(STRANDS * 4)
    /** RPaMp's columns: (sway, sway phase, colour: 0 tint, 1 glow, 2 shade, unused). */
    val strandB = FloatArray(STRANDS * 4)
    /** RPaMp's heads, the round glow at the top of each column: (x, y). */
    val heads = FloatArray(STRANDS * 2)
    /** RPaMp's floor: (height of its glow, brightness, ripple phase, unused). */
    val floor = FloatArray(4)

    /** Yang's turn, in radians. */
    var spin = 0.0
        private set
    /** Lava's clock, in seconds of lava. */
    var flow = 0.0
        private set
    /** RPaMp's clock, in seconds of rising. */
    var rise = 0.0
        private set

    /** Moves the three clocks on by [dt] seconds at the speed the levels give them; 0 for a still room. */
    fun advance(dt: Float, bass: Float, mid: Float) {
        spin += dt * (YANG_SPIN + bass * YANG_SPIN_BASS)
        flow += dt * (1.0 + bass * 0.6)
        rise += dt * (1.0 + mid * 0.8)
    }

    /**
     * Yang at [t] of the room's clock. The big pair stands opposite each other on an orbit 0.24 of the
     * height out, the small pair on one 0.39 out, turning [SMALL_RATE] times as fast a quarter turn
     * apart, so the small ones keep passing the big ones. Both orbits are stretched [WIDE] across, to
     * use the width. The big balls swell with the bass as the original's figures do (1 + bass x 0.62),
     * the small ones with the air (1 + air x 0.85), and the middle ball with the middle.
     *
     * The trail is not a framebuffer kept from frame to frame, which would be a second full pass and a
     * read of last frame's texture on the Amiko: it is [ECHOES] more balls per ball, each further back
     * along the orbit, smaller and lighter, summed into the same field. Where they overlap they melt
     * into one tail. The louder the ball's level, the faster it turns and the longer its tail.
     */
    fun yang(t: Double, bass: Float, mid: Float, air: Float) {
        val bigOrbit = 0.24 + 0.03 * sin(t * 0.23)
        val smallOrbit = 0.39 + 0.04 * sin(t * 0.31 + 1.0)
        val bigR = 0.15 * (1.0 + bass * 0.62)
        val smallR = 0.07 * (1.0 + air * 0.85)
        // Far enough apart along the orbit to show as a tail, near enough to melt into one: a big ball's
        // echoes are 0.09 of the height apart, a small one's 0.09, against radii of 0.15 and 0.07.
        val bigLag = 0.36 * (1.0 + bass * 0.6)
        val smallLag = 0.22 * (1.0 + air * 0.6)
        for (ball in 0 until 2) {
            orbit(big, ball, spin + ball * PI, bigOrbit, bigR, bigLag)
            orbit(small, ball, spin * SMALL_RATE + PI / 2 + ball * PI, smallOrbit, smallR, smallLag)
        }
        put(centre, 0, 0.0, 0.0, 0.30 * (1.0 + mid * 0.35), 0.0)
    }

    /** One ball at [angle] on an orbit [distance] out, and its echoes behind it. */
    private fun orbit(into: FloatArray, ball: Int, angle: Double, distance: Double, radius: Double, lag: Double) {
        for (k in 0..ECHOES) {
            val a = angle - k * lag
            put(into, ball * (ECHOES + 1) + k, cos(a) * distance * WIDE, sin(a) * distance, radius * (1.0 - 0.12 * k), ECHO_FADE.pow(k))
        }
    }

    /**
     * Lava. Seven blobs spread across the width, each rising and sinking on a sine of its own period (24
     * to 66 seconds), bent so it lingers at the floor and the top as a lamp's wax does, and drifting a
     * little sideways. Blobs of different periods pass each other, melt into one and part again; at the
     * ends they melt into the pools. They swell with the bass as the original's figures do. The
     * background ball wanders slowly and swells with the middle (1 + mid x 0.45).
     */
    fun lava(bass: Float, mid: Float) {
        val f = flow
        for (i in 0 until LAVA_BLOBS) {
            val period = 24.0 + 7.0 * ((i * 3) % LAVA_BLOBS)
            val s = sin(f * 2 * PI / period + i * 2.39996)
            val y = sign(s) * abs(s).pow(0.85) * 0.44
            val x = -0.70 + i * (1.40 / (LAVA_BLOBS - 1)) + 0.12 * sin(f * 0.071 + i * 1.7)
            val r = (0.09 + 0.03 * sin(f * 0.13 + i * 2.3)) * (1.0 + bass * 0.62)
            put(blobs, i, x, y, r, (i * 0.41 + 0.15) % 1.0)
        }
        put(pools, 0, -0.565, 0.11 * (1.0 + bass * 0.3), 0.575, 0.07 * (1.0 + bass * 0.2))
        put(back, 0, 0.20 * sin(f * 0.043), -0.04 + 0.07 * sin(f * 0.031 + 1.0), 0.40 * (1.0 + mid * 0.45), f * 0.21)
    }

    /**
     * RPaMp. Seven columns spread across the width, each lit by one level ([STRAND_BAND]: neighbours
     * hear different ones), which lifts its top (+0.22 of the height at full) and brightens it. Each
     * sways in a wave that travels up it, and its head, a round glow at the top, now and then lifts
     * clear of it and sinks back, as in the skin's picture. The floor glows higher and brighter with the
     * bass. Colours step through tint, glow and shade along the row.
     */
    fun strands(bass: Float, mid: Float, air: Float) {
        val r = rise
        for (i in 0 until STRANDS) {
            val level = when (STRAND_BAND[i]) {
                0 -> bass
                1 -> mid
                else -> air
            }
            val x = -0.76 + i * (1.52 / (STRANDS - 1)) + 0.05 * sin(r * 0.047 + i * 3.1)
            val top = 0.02 + 0.10 * sin(r * 0.19 + i * 1.9) + level * 0.22
            val width = (0.065 + 0.015 * sin(r * 0.23 + i * 0.7)) * (1.0 + level * 0.3)
            put(strandA, i, x, top, width, 0.45 + level * 0.85)
            val sway = 0.025 + 0.015 * sin(r * 0.11 + i * 2.2)
            val phase = r * (1.3 + 0.17 * i) + i * 1.7
            put(strandB, i, sway, phase, (i * 0.73 + 0.2) % 2.0, 0.0)
            // On the column's centre line at the head's height (the shader's sin(y x 7 - phase)).
            val head = top + 0.03 + 0.07 * max(0.0, sin(r * 0.13 + i * 2.1))
            heads[i * 2] = (x + sway * sin(head * 7.0 - phase)).toFloat()
            heads[i * 2 + 1] = head.toFloat()
        }
        put(floor, 0, 0.22 + bass * 0.08, 1.0 + bass * 0.8, r * 0.3, 0.0)
    }

    private fun put(into: FloatArray, i: Int, a: Double, b: Double, c: Double, d: Double) {
        into[i * 4] = a.toFloat()
        into[i * 4 + 1] = b.toFloat()
        into[i * 4 + 2] = c.toFloat()
        into[i * 4 + 3] = d.toFloat()
    }

    companion object {
        /** Echoes behind each of Yang's balls. */
        const val ECHOES = 4
        /** Yang's balls of one size and their echoes: two balls of 1 + [ECHOES]. */
        const val YANG_LOBES = 2 * (ECHOES + 1)
        const val LAVA_BLOBS = 7
        const val STRANDS = 7

        /** Each echo is this much lighter than the one before it. */
        const val ECHO_FADE = 0.6
        /** Yang's turn with no music, radians a second: a turn in about 20 seconds. */
        const val YANG_SPIN = 0.32
        /** How much faster the bass at full turns it. */
        const val YANG_SPIN_BASS = 0.9
        const val SMALL_RATE = 1.6
        /** Yang's orbits are this much wider than high. */
        const val WIDE = 1.3

        /** Which level lights each of RPaMp's columns: 0 bass, 1 middle, 2 air. */
        val STRAND_BAND = intArrayOf(0, 2, 1, 0, 1, 2, 0)
    }
}
