package com.rpm.player.phone.stage

import android.opengl.GLES30
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A plate of sand, and the music playing it: the iPhone's `ChladniPlate` (RPMStages/ChladniPlate.swift), figure for
 * figure, with nothing that draws.
 *
 * Chladni's experiment, 1787: scatter sand on a metal plate, sound it, and the grains bounce away from everywhere the
 * plate is moving and pile up along the lines where it stands still. A plate holds a whole set of modes at once, so
 * the displacement is the sum of its modes, each weighted by how much music is sounding it, and the sand settles on
 * the zeros of that sum. A bass line and a hi-hat wake different modes than a dense chord does, so the figure belongs
 * to the balance of this spectrum and no other. Silence leaves the sand exactly where it lies.
 *
 * The grains are kept as one array of x and y pairs, which is what the GPU is handed.
 */
class ChladniPlate(count: Int = 30_000, frameSize: Int = 2048, seed: Long = 20_260_726) {
    private val cosine = FloatArray(MODES.size * 2 * TABLE)
    private val sine = FloatArray(MODES.size * 2 * TABLE)
    private val weight = FloatArray(MODES.size)
    private val audio = ShaderAudio(frameSize)
    private val spec = FloatArray(ShaderAudio.WIDTH)
    private val light = FloatArray(count) { 1f }
    private val roll = Roll(seed)
    private var phase = 0

    /** Where every grain is, in plate coordinates, -1 to 1 on both axes: x then y for each grain. */
    val grains = FloatArray(count * 2)

    /** How many grains there are. */
    val count: Int = count

    /** How hard the plate is being sounded, 0 at silence. Zero means the sand does not move at all. */
    var drive = 0f
        private set

    init {
        buildTheTables()
        throwTheSand()
    }

    /** Samples every mode's cos and sin across the plate, once: the only trigonometry the plate ever does. */
    private fun buildTheTables() {
        for (m in MODES.indices) {
            for (axis in 0 until 2) {
                val k = (if (axis == 0) MODES[m].first else MODES[m].second) * PI.toFloat()
                val at = (m * 2 + axis) * TABLE
                for (i in 0 until TABLE) {
                    val x = i.toFloat() / (TABLE - 1) * 2 - 1
                    cosine[at + i] = cos(k * x)
                    sine[at + i] = sin(k * x)
                }
            }
        }
    }

    /**
     * Sand thrown on the plate once, by hand: thicker in some places than others, and that unevenness never fully
     * washes out, which is why one arm of a figure ends up heavier than its mirror.
     */
    private fun throwTheSand() {
        val tiltX = roll.unit() * 2 - 1
        val tiltY = roll.unit() * 2 - 1
        val swirl = roll.unit() * 2 * PI
        for (i in 0 until count) {
            var x: Double
            var y: Double
            while (true) {
                x = roll.unit() * 2 - 1
                y = roll.unit() * 2 - 1
                val d = 0.62 + 0.20 * (x * tiltX + y * tiltY) + 0.18 * cos(x * 2.1 + swirl) * cos(y * 1.7 - swirl)
                if (roll.unit() < d) break
            }
            grains[i * 2] = x.toFloat()
            grains[i * 2 + 1] = y.toFloat()
            // Two in a hundred are light enough never to settle: the dust that keeps moving.
            light[i] = if (roll.unit() < 0.02) 0.16f else 0.75f + roll.unit().toFloat() * 0.55f
        }
    }

    /** Takes the plate's note from the music: which modes it is sounding, and how hard. [count] is 0 for silence. */
    fun listen(frame: FloatArray, count: Int) {
        audio.listen(frame, count)
        val heard = audio.spectrum
        for (i in spec.indices) {
            val k = if (heard[i] > spec[i]) 0.40f else 0.06f
            spec[i] += (heard[i] - spec[i]) * k
        }
        var total = 0f
        for (mi in MODES.indices) {
            val lo = (2.0 * 150.0.pow(mi.toDouble() / MODES.size)).toInt()
            var hi = (2.0 * 150.0.pow((mi + 1).toDouble() / MODES.size)).toInt()
            if (hi <= lo) hi = lo + 1
            if (hi > 320) hi = 320
            var sum = 0f
            for (i in lo until hi) sum += spec[i]
            val band = (sum / (hi - lo)).pow(0.70f)
            val k = if (band > weight[mi]) 0.10f else 0.035f
            weight[mi] += (band - weight[mi]) * k
            total += weight[mi]
        }
        // The figure is the balance between the modes; how loud the passage is says only how hard the sand is thrown.
        if (total > 0.0001f) for (mi in weight.indices) weight[mi] /= total

        var level = 0f
        for (i in 2 until 300) level += spec[i]
        level /= 298f
        val want = min(1f, (level * 3.2f).pow(0.75f))
        drive += (want - drive) * (if (want > drive) 0.30f else 0.04f)
        if (drive < 0.004f) drive = 0f
    }

    /**
     * One step of the sand. Each grain still far from a nodal line is thrown again a fixed distance, mostly at random
     * and a little towards the quiet; one within a ridge has arrived and stays. Half the sand moves each step, at
     * twice the step, which halves the cost and leaves the figure the same.
     */
    fun settle(dt: Float) {
        if (drive <= 0f || dt <= 0f) return
        phase = phase xor 1
        val step = dt * 2
        val hop = RATE * step * drive
        val restless = drive * drive * 0.006f * step
        val span = (TABLE - 1).toFloat()

        var i = phase
        while (i < count) {
            var x = grains[i * 2]
            var y = grains[i * 2 + 1]
            val fx = (x + 1) * 0.5f * span
            val fy = (y + 1) * 0.5f * span
            val ix = min(max(fx.toInt(), 0), TABLE - 2)
            val iy = min(max(fy.toInt(), 0), TABLE - 2)
            val tx = fx - ix
            val ty = fy - iy

            var u = 0f
            var dux = 0f
            var duy = 0f
            for (m in MODES.indices) {
                val w = weight[m]
                // A mode nothing is sounding costs nothing.
                if (w < 0.004f) continue
                val ax = m * 2 * TABLE
                val bx = (m * 2 + 1) * TABLE
                val can = sample(cosine, ax + ix, tx)
                val san = sample(sine, ax + ix, tx)
                val cam = sample(cosine, ax + iy, ty)
                val sam = sample(sine, ax + iy, ty)
                val cbn = sample(cosine, bx + ix, tx)
                val sbn = sample(sine, bx + ix, tx)
                val cbm = sample(cosine, bx + iy, ty)
                val sbm = sample(sine, bx + iy, ty)
                val n = MODE_N[m]
                val mm = MODE_M[m]
                u += w * (can * cbm - cbn * cam)
                dux += w * (-n * san * cbm + mm * sbn * cam)
                duy += w * (-mm * can * sbm + n * cbn * sam)
            }

            val slope = sqrt(dux * dux + duy * duy) + 1e-5f
            if (abs(u) > CAPTURE * slope) {
                val gx = u * dux
                val gy = u * duy
                val g = sqrt(gx * gx + gy * gy) + 1e-5f
                x -= gx / g * hop * AIM * light[i]
                y -= gy / g * hop * AIM * light[i]
                x += roll.nudge() * hop * SCATTER
                y += roll.nudge() * hop * SCATTER
            }
            x += roll.nudge() * restless
            y += roll.nudge() * restless

            // Sand thrown off an edge is poured back on somewhere at random, so no line builds along the rim.
            if (x < -1 || x > 1 || y < -1 || y > 1) {
                x = (roll.unit() * 2 - 1).toFloat()
                y = (roll.unit() * 2 - 1).toFloat()
            }
            grains[i * 2] = x
            grains[i * 2 + 1] = y
            i += 2
        }
    }

    private fun sample(of: FloatArray, i: Int, t: Float): Float = of[i] + (of[i + 1] - of[i]) * t

    private companion object {
        /** The plate's modes as (n, m) pairs, from the lowest note it holds to the highest. */
        val MODES = listOf(2 to 3, 3 to 4, 2 to 5, 4 to 5, 3 to 6, 4 to 7, 5 to 8, 6 to 9)
        val MODE_N = FloatArray(MODES.size) { MODES[it].first * PI.toFloat() }
        val MODE_M = FloatArray(MODES.size) { MODES[it].second * PI.toFloat() }
        const val TABLE = 2048

        // A hop is a fixed size, so the time a grain takes to arrive is how far it has to go.
        const val RATE = 0.0024f
        const val AIM = 0.55f
        const val SCATTER = 1.30f

        /** What counts as arriving, as a distance to the nodal line. */
        const val CAPTURE = 0.008f
    }
}

/**
 * Draws Chladni's plate of sand: the iPhone's `PlateView`. Thirty thousand grains as square points in one draw, white
 * on flat black, the plate fitted to the shorter side and centred so the figure keeps the proportions the physics
 * computed. A grain keeps its size on screen at any density, as the desktop's point-size uniform does.
 *
 * The plate listens and settles at the listening rate, as the iPhone's does, but on a thread of its own: thirty
 * thousand grains through eight modes is more arithmetic than a frame of the GL thread has room for on this phone.
 * The GL thread hands each reading over and draws whichever positions the last finished step left; a reading that
 * arrives while a step is still running is skipped, so the sand falls behind the music rather than the picture.
 */
class PlateLook : StageRenderer {
    /** Full size: a grain is a point and a half across, and a stretched point is a smear. */
    override val scale = 1f

    private val plate = ChladniPlate()
    private val sand = floats(plate.count * 2)

    /** The positions the last finished step left, copied for the GL thread under this lock. */
    private val settled = plate.grains.copyOf()
    private val heard = FloatArray(2048)
    private val stepping = java.util.concurrent.atomic.AtomicBoolean(false)
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { job ->
        Thread(job, "rpm.stage.plate").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private var drawing = 0
    private var points = 0
    private var buffer = 0
    private var fitAt = 0
    private var grainAt = 0

    override fun created() {
        drawing = program(VERTEX, FRAGMENT)
        fitAt = GLES30.glGetUniformLocation(drawing, "fit")
        grainAt = GLES30.glGetUniformLocation(drawing, "grain")
        points = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        buffer = arrayBuffer()
        GLES30.glBindVertexArray(points)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, plate.count * 2 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    override fun hear(frame: FloatArray, count: Int, now: Double, since: Double) {
        if (!stepping.compareAndSet(false, true)) return
        System.arraycopy(frame, 0, heard, 0, minOf(count, heard.size))
        val dt = since.toFloat()
        try {
            worker.execute {
                try {
                    plate.listen(heard, count)
                    plate.settle(dt)
                    synchronized(settled) { System.arraycopy(plate.grains, 0, settled, 0, settled.size) }
                } finally {
                    stepping.set(false)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // The look has been put away.
        }
    }

    override fun released() {
        worker.shutdown()
    }

    override fun draw(width: Int, height: Int, now: Double, since: Double) {
        GLES30.glClearColor(0x03 / 255f, 0x03 / 255f, 0x04 / 255f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_BLEND)

        sand.clear()
        synchronized(settled) { sand.put(settled) }
        sand.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, plate.count * 2 * 4, sand)

        // The plate is square and the screen need not be: fitted to the shorter side. The plate's y counts down, as
        // the iPhone's canvas does, so it is turned over for GL.
        val side = min(width, height).toFloat()
        GLES30.glUseProgram(drawing)
        GLES30.glUniform2f(fitAt, side / width, -side / height)
        GLES30.glUniform1f(grainAt, max(1.4f * side / POINTS_ACROSS, side * GRAIN_PER_SIDE))
        GLES30.glBindVertexArray(points)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, plate.count)
        GLES30.glBindVertexArray(0)
    }

    private companion object {
        /** A grain is 1.4 points, or 1/560 of the plate, whichever is larger. */
        const val GRAIN_PER_SIDE = 1f / 560f

        /** About how many points the shorter side of a turned phone is, to turn the 1.4-point grain into pixels. */
        const val POINTS_ACROSS = 576f

        const val VERTEX = """#version 300 es
layout(location = 0) in vec2 at;
uniform vec2 fit;
uniform float grain;
void main() {
    gl_Position = vec4(at * fit, 0.0, 1.0);
    gl_PointSize = grain;
}
"""

        const val FRAGMENT = """#version 300 es
precision mediump float;
out vec4 colour;
void main() { colour = vec4(1.0); }
"""
    }
}
