package com.rpm.player.phone.stage

import android.opengl.GLES30
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The ribbon sea's reading: fifteen contour lines of one rolling surface, with wrinkles born at the left from the music
 * of the instant that travel right along each line like a wave sent down a rope. The listening half of the iPhone's
 * `RibbonSea` (RPMStages/RibbonSea.swift), figure for figure: the smoothed spectrum, the swell and the band history.
 *
 * The other half, the projection, is the desktop's vertex shader, which the iPhone moved onto the CPU because SwiftUI
 * has no vertex stage. Here it is back in a vertex shader ([RibbonLook]), with the iPhone's arithmetic line for line:
 * on this phone the CPU took 20 ms a frame to trace and stroke the lines.
 */
class RibbonSea(frameSize: Int = 2048) {
    private val audio = ShaderAudio(frameSize)
    private val spec = FloatArray(ShaderAudio.WIDTH)
    private var swellHold = 0.0

    /**
     * The scrolling record of recent band energy: columns are time with now at column 0, rows are frequency from bass
     * at row 0. A point at u reads column u, so a crest born at the left flows right over the length of the history.
     */
    val history = FloatArray(HISTORY_BANDS * HISTORY_COLUMNS)

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

    companion object {
        /** Contour lines, low band at the front. */
        const val LINES = 15

        /** Points along each line. */
        const val COLUMNS = 150

        const val HISTORY_COLUMNS = 100
        const val HISTORY_BANDS = 16
    }
}

/**
 * The ribbon sea, drawn: the iPhone's `RibbonView`. Three passes over each centreline, widest first, added together and
 * not painted over, so where lines cross they sum into a bloom, as the desktop's halo term does. Each pass is a strip
 * whose two edges are pulled in to the centre at the ends, so a line tapers away.
 *
 * The strips never change shape on the CPU: one buffer holds, for every point, its column, its line and which edge it
 * is, and the vertex shader places it on the sea. Each frame sends the clock, the swell and the band history.
 */
class RibbonLook : StageRenderer {
    /**
     * Half: every line is three wide strips added over each other, and at two thirds the Titan's GPU held the sea to
     * about 35 frames a second.
     */
    override val scale = 0.5f

    private val sea = RibbonSea()
    private val bytes = bytes(RibbonSea.HISTORY_BANDS * RibbonSea.HISTORY_COLUMNS)
    private var drawing = 0
    private var vertices = 0
    private var history = 0
    private val at = HashMap<String, Int>()

    override fun created() {
        drawing = program(VERTEX, FRAGMENT)
        at.clear()
        history = byteTexture(RibbonSea.HISTORY_COLUMNS, RibbonSea.HISTORY_BANDS)
        // Every line as one strip: two corners a column, each its column, its line and its edge (+1 above, -1 below).
        val corners = FloatArray(RibbonSea.LINES * RibbonSea.COLUMNS * 2 * 3)
        var k = 0
        for (line in 0 until RibbonSea.LINES) {
            for (c in 0 until RibbonSea.COLUMNS) {
                for (side in floatArrayOf(1f, -1f)) {
                    corners[k++] = c.toFloat()
                    corners[k++] = line.toFloat()
                    corners[k++] = side
                }
            }
        }
        val upload = floats(corners.size).apply {
            put(corners)
            flip()
        }
        vertices = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        val buffer = arrayBuffer()
        GLES30.glBindVertexArray(vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.size * 4, upload, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun uniform(name: String): Int = at.getOrPut(name) { GLES30.glGetUniformLocation(drawing, name) }

    override fun hear(frame: FloatArray, count: Int, now: Double, since: Double) {
        sea.listen(frame, count, now)
    }

    override fun draw(width: Int, height: Int, now: Double, since: Double) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        uploadBytes(history, RibbonSea.HISTORY_COLUMNS, RibbonSea.HISTORY_BANDS, sea.history, bytes)

        // Breathing: the resting amplitude swells and relaxes on two slow sines with unrelated periods, and the roll's
        // own pace varies with a third, so the sea never travels at a constant rate.
        val t = now.toFloat()
        val breath = 0.60f + 0.25f * sin(t * 0.42f) + 0.15f * sin(t * 0.27f + 1.3f)
        val aspect = height.toFloat() / width
        GLES30.glUseProgram(drawing)
        GLES30.glUniform1f(uniform("time"), t)
        GLES30.glUniform1f(uniform("seaTime"), t * 0.2f + 0.18f * sin(t * 0.33f))
        GLES30.glUniform1f(uniform("amplitude"), 0.16f * breath + sea.swell * 1.25f)
        GLES30.glUniform1f(uniform("aspect"), aspect)
        // A wider window is given a longer stretch of sea, not a stretched one.
        GLES30.glUniform1f(uniform("reach"), max(1f, 0.62f / max(aspect, 0.01f)))
        GLES30.glUniform1f(uniform("swell"), sea.swell)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, history)

        GLES30.glEnable(GLES30.GL_BLEND)
        // Added, as SwiftUI's plus-lighter adds: the pass's colour at its strength on top of what is there.
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glBindVertexArray(vertices)
        val perLine = RibbonSea.COLUMNS * 2
        val halfAt = uniform("halfWidth")
        val inkAt = uniform("ink")
        for (line in 0 until RibbonSea.LINES) {
            for (pass in PASSES) {
                // The ribbon's half-width is 0.024 of the half-height; in device units that is 0.048 of the height.
                GLES30.glUniform1f(halfAt, HALF_WIDTH * 2f * pass.width)
                GLES30.glUniform4f(inkAt, pass.r, pass.g, pass.b, pass.alpha)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, line * perLine, perLine)
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

        /** `RibbonSea.trace` and `RibbonView`'s end fade from the iPhone, for one point of one line. */
        const val VERTEX = """#version 300 es
precision highp float;
layout(location = 0) in vec3 corner;
uniform float time;
uniform float seaTime;
uniform float amplitude;
uniform float aspect;
uniform float reach;
uniform float swell;
uniform float halfWidth;
uniform sampler2D history;
out float bright;

float along(vec2 d, float x, float z) {
    d = normalize(d);
    return d.x * x + d.y * z;
}

// An open sea: three long directional rollers, so a line carries a couple of flowing curves.
float sea(float x, float z, float t) {
    return 0.50 * sin(along(vec2(1.00, 0.30), x, z) * 1.1 - t * 0.55)
         + 0.28 * sin(along(vec2(0.60, -0.80), x, z) * 1.9 - t * 0.85)
         + 0.16 * sin(along(vec2(-0.35, 1.00), x, z) * 3.2 - t * 1.15);
}

float rise(float from, float to, float x) {
    float t = clamp((x - from) / (to - from), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

void main() {
    float u = corner.x / 149.0;
    float v = corner.y / 14.0;
    float wz = (v - 0.5) * 2.2;
    // Wider toward the back, or perspective pulls the far lines' ends inside the frame.
    float span = (3.0 + v * 3.4) * reach;
    // The front reads treble, the back reads bass.
    float fcoord = 1.0 - v;
    float ramp = 0.06 + (0.15 - 0.06) * v;
    float wx = (u - 0.5) * span;

    float wy = sea(wx, wz, seaTime) * amplitude;
    // The band's history, read where the iPhone reads it: linearly between the columns and the bands.
    float born = texture(history, vec2((u * 99.0 + 0.5) / 100.0, (fcoord * 15.0 + 0.5) / 16.0)).r;
    float carrier = sin(wx * 13.0 + wz * 1.6 - time * 6.0) + sin(wx * 20.0 + wz * 2.4 - time * 8.4) * 0.5;
    wy += carrier * (0.003 + born * ramp);

    // The camera: a pinhole raised above the sea and pitched down twenty degrees.
    float focal = 2.6;
    float pitch = 20.0 * 3.14159265 / 180.0;
    float cp = cos(pitch), sp = sin(pitch);
    float dist = 3.2;
    float dy = wy - dist * sp;
    float dz = wz + dist * cp;
    float cy = dy * cp + dz * sp;
    float cz = max(-dy * sp + dz * cp, 0.05);
    vec2 p = vec2(wx * focal / cz * aspect, cy * focal / cz);

    // Both edges pulled in to the centre at the ends, so the line tapers away.
    float fade = rise(0.0, 0.08, u) * rise(1.0, 0.92, u);
    p.y += corner.z * halfWidth * fade;

    bright = (0.45 + 0.55 * (1.0 - v)) * (0.8 + 0.5 * swell);
    gl_Position = vec4(p, 0.0, 1.0);
}
"""

        const val FRAGMENT = """#version 300 es
precision mediump float;
uniform vec4 ink;
in float bright;
out vec4 colour;
void main() { colour = vec4(ink.rgb, min(1.0, ink.a * bright)); }
"""
    }
}
