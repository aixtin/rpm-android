package com.rpm.player.phone.stage

import android.opengl.GLES30
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The ribbon sea: fifteen contour lines of one rolling surface, seen nearly edge-on through a perspective camera, with
 * wrinkles born at the left from the music of the instant that travel right along each line like a wave sent down a
 * rope. The iPhone's `RibbonSea` (RPMStages/RibbonSea.swift), figure for figure: the desktop's vertex shader, moved
 * off the GPU.
 */
class RibbonSea(frameSize: Int = 2048) {
    private val audio = ShaderAudio(frameSize)
    private val spec = FloatArray(ShaderAudio.WIDTH)
    private val history = FloatArray(HISTORY_BANDS * HISTORY_COLUMNS)
    private var swellHold = 0.0

    /** How high the whole swell is, 0 at rest: a fast attack, a short hold and a slow settle, like an ocean. */
    var swell = 0f
        private set

    /** Takes a frame of played samples ([count] 0 for silence) and moves the sea on. */
    fun listen(frame: FloatArray, count: Int, now: Double) {
        audio.listen(frame, count)
        val heard = audio.spectrum
        for (i in spec.indices) {
            val k = if (heard[i] > spec[i]) 0.35f else 0.10f
            spec[i] += (heard[i] - spec[i]) * k
        }
        var sum = 0f
        for (i in 2 until 220) sum += spec[i]
        val level = sum / 218f
        if (level > swell) {
            swell += (level - swell) * 0.35f
            swellHold = now + 0.35
        } else if (now >= swellHold) {
            swell += (level - swell) * 0.010f
        }
        scroll()
    }

    /** Every band's row shifts one column right and takes this instant's energy at the left. Bass at row 0. */
    private fun scroll() {
        for (band in 0 until HISTORY_BANDS) {
            val lo = (2.0 * 200.0.pow(band.toDouble() / HISTORY_BANDS)).toInt()
            var hi = (2.0 * 200.0.pow((band + 1).toDouble() / HISTORY_BANDS)).toInt()
            if (hi <= lo) hi = lo + 1
            if (hi > 500) hi = 500
            var sum = 0f
            for (i in lo until hi) sum += spec[i]
            val row = band * HISTORY_COLUMNS
            for (c in HISTORY_COLUMNS - 1 downTo 1) history[row + c] = history[row + c - 1]
            history[row] = (sum / (hi - lo)).coerceIn(0f, 1f)
        }
    }

    /**
     * Projects every line into normalised device coordinates (y up) for a frame whose height over width is [aspect],
     * x then y per point, line by line.
     */
    fun trace(time: Double, aspect: Float, into: FloatArray) {
        val t = time.toFloat()
        val breath = 0.60f + 0.25f * sin(t * 0.42f) + 0.15f * sin(t * 0.27f + 1.3f)
        val seaTime = t * 0.2f + 0.18f * sin(t * 0.33f)
        val amplitude = 0.16f * breath + swell * 1.25f

        val focal = 2.6f
        val pitch = 20f * PI.toFloat() / 180f
        val cp = cos(pitch)
        val sp = sin(pitch)
        val dist = 3.2f
        val camY = dist * sp
        val camZ = -dist * cp
        // A wider window is given a longer stretch of sea, not a stretched one.
        val reach = max(1f, 0.62f / max(aspect, 0.01f))

        var k = 0
        for (line in 0 until LINES) {
            val v = line.toFloat() / (LINES - 1)
            val wz = (v - 0.5f) * 2.2f
            val span = (3.0f + v * 3.4f) * reach
            val fcoord = 1 - v
            val ramp = 0.06f + (0.15f - 0.06f) * v
            for (c in 0 until COLUMNS) {
                val u = c.toFloat() / (COLUMNS - 1)
                val wx = (u - 0.5f) * span
                var wy = sea(wx, wz, seaTime) * amplitude
                val born = read(u, fcoord)
                val carrier = sin(wx * 13.0f + wz * 1.6f - t * 6.0f) + sin(wx * 20.0f + wz * 2.4f - t * 8.4f) * 0.5f
                wy += carrier * (0.003f + born * ramp)

                val dy = wy - camY
                val dz = wz - camZ
                val cy = dy * cp + dz * sp
                var cz = -dy * sp + dz * cp
                if (cz < 0.05f) cz = 0.05f
                into[k++] = wx * focal / cz * aspect
                into[k++] = cy * focal / cz
            }
        }
    }

    /** How bright a line is drawn: the front ones lead, and everything lifts when the music does. */
    fun brightness(line: Int): Float {
        val v = line.coerceIn(0, LINES - 1).toFloat() / (LINES - 1)
        return (0.45f + 0.55f * (1 - v)) * (0.8f + 0.5f * swell)
    }

    /** Reads the band history as the shader sampled its texture: normalised, linearly filtered, clamped. */
    private fun read(u: Float, band: Float): Float {
        val fx = u.coerceIn(0f, 1f) * (HISTORY_COLUMNS - 1)
        val fy = band.coerceIn(0f, 1f) * (HISTORY_BANDS - 1)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, HISTORY_COLUMNS - 1)
        val y1 = min(y0 + 1, HISTORY_BANDS - 1)
        val tx = fx - x0
        val ty = fy - y0
        val a = history[y0 * HISTORY_COLUMNS + x0]
        val b = history[y0 * HISTORY_COLUMNS + x1]
        val c = history[y1 * HISTORY_COLUMNS + x0]
        val d = history[y1 * HISTORY_COLUMNS + x1]
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty
    }

    companion object {
        const val LINES = 15
        const val COLUMNS = 150
        private const val HISTORY_COLUMNS = 100
        private const val HISTORY_BANDS = 16

        /** An open sea: three long directional rollers, so a line carries a couple of flowing curves. */
        private fun sea(x: Float, z: Float, t: Float): Float =
            0.50f * sin(along(1.00f, 0.30f, x, z) * 1.1f - t * 0.55f) +
                0.28f * sin(along(0.60f, -0.80f, x, z) * 1.9f - t * 0.85f) +
                0.16f * sin(along(-0.35f, 1.00f, x, z) * 3.2f - t * 1.15f)

        private fun along(dx: Float, dz: Float, x: Float, z: Float): Float {
            val len = sqrt(dx * dx + dz * dz)
            return (dx / len) * x + (dz / len) * z
        }
    }
}

/**
 * The ribbon sea, drawn: the iPhone's `RibbonView`. Three passes over each centreline, widest first, added together
 * and not painted over, so where lines cross they sum into a bloom, as the desktop's halo term does. Each pass is a
 * strip along the line whose two edges are pulled in to the centre at the ends, so a line tapers away.
 */
class RibbonLook : StageRenderer {
    /** Two thirds: the lines are thin, and their filament needs pixels to stay a filament. */
    override val scale = 0.67f

    private val sea = RibbonSea()
    private val points = FloatArray(RibbonSea.LINES * RibbonSea.COLUMNS * 2)

    /** Every pass of every line as one strip each, side by side in one buffer, sent to the GPU once a frame. */
    private val strips = FloatArray(STRIP * RibbonSea.LINES * PASSES.size)
    private val upload = floats(strips.size)
    private var drawing = 0
    private var vertices = 0
    private var buffer = 0
    private var inkAt = 0

    override fun created() {
        drawing = program(VERTEX, FRAGMENT)
        inkAt = GLES30.glGetUniformLocation(drawing, "ink")
        vertices = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        buffer = arrayBuffer()
        GLES30.glBindVertexArray(vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, strips.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    override fun hear(frame: FloatArray, count: Int, now: Double, since: Double) {
        sea.listen(frame, count, now)
    }

    override fun draw(width: Int, height: Int, now: Double, since: Double) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        sea.trace(now, height.toFloat() / width, points)

        // The ribbon's half-width is 0.024 of the half-height; in device units that is 0.048 of the height.
        val half = HALF_WIDTH * 2f
        var k = 0
        for (line in 0 until RibbonSea.LINES) {
            val from = line * RibbonSea.COLUMNS * 2
            for (pass in PASSES) {
                val w = half * pass.width
                for (c in 0 until RibbonSea.COLUMNS) {
                    val x = points[from + c * 2]
                    val y = points[from + c * 2 + 1]
                    val edge = w * FADE[c]
                    strips[k++] = x
                    strips[k++] = y + edge
                    strips[k++] = x
                    strips[k++] = y - edge
                }
            }
        }
        upload.clear()
        upload.put(strips)
        upload.flip()

        GLES30.glEnable(GLES30.GL_BLEND)
        // Added, as SwiftUI's plus-lighter adds: the pass's colour at its strength on top of what is there.
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glUseProgram(drawing)
        GLES30.glBindVertexArray(vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, strips.size * 4, upload)
        val perStrip = RibbonSea.COLUMNS * 2
        var strip = 0
        for (line in 0 until RibbonSea.LINES) {
            val bright = sea.brightness(line)
            for (pass in PASSES) {
                GLES30.glUniform4f(inkAt, pass.r, pass.g, pass.b, min(1f, pass.alpha * bright))
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, strip * perStrip, perStrip)
                strip++
            }
        }
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /** One pass of a line: its half-width as a share of the ribbon's, its colour and how much of it is laid down. */
    private class Pass(val width: Float, rgb: Int, alpha: Int) {
        val r = (rgb shr 16 and 0xFF) / 255f
        val g = (rgb shr 8 and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val alpha = alpha / 255f
    }

    private companion object {
        /** Widest first: the deep-cyan bloom, the blue, and the white-hot filament last, so its edges stay clean. */
        val PASSES = listOf(Pass(1.00f, 0x0057FF, 0x4D), Pass(0.45f, 0x2E9BFF, 0x66), Pass(0.16f, 0xCCF2FF, 0xF2))

        const val HALF_WIDTH = 0.024f

        /** The floats in one strip: two corners a column, x and y each. */
        const val STRIP = RibbonSea.COLUMNS * 2 * 2

        /** The end fade per column, which depends only on the column. */
        val FADE = FloatArray(RibbonSea.COLUMNS) { c ->
            val u = c.toFloat() / (RibbonSea.COLUMNS - 1)
            smoothstep(0f, 0.08f, u) * smoothstep(1f, 0.92f, u)
        }

        fun smoothstep(from: Float, to: Float, x: Float): Float {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        const val VERTEX = """#version 300 es
layout(location = 0) in vec2 at;
void main() { gl_Position = vec4(at, 0.0, 1.0); }
"""

        const val FRAGMENT = """#version 300 es
precision mediump float;
uniform vec4 ink;
out vec4 colour;
void main() { colour = ink; }
"""
    }
}
