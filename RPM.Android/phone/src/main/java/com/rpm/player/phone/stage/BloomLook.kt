package com.rpm.player.phone.stage

import android.opengl.GLES30
import android.opengl.Matrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * The reading a flower of light is drawn from: a polar spectrogram and the swell under it. The iPhone's `BloomField`
 * (RPMStages/BloomField.swift), figure for figure. Angle is frequency and radius is time: rows are the frequency
 * wheel, columns are time with now at column 0, so a beat opens at the heart and travels outward as a ring.
 */
class BloomField(frameSize: Int = 2048) {
    private val audio = ShaderAudio(frameSize)
    private val spec = FloatArray(ShaderAudio.WIDTH)
    private var swellHold = 0.0
    private var turnedSoFar = 0.0

    /** The spectrogram, row-major: `history[band * COLUMNS + column]`, column 0 the newest, 0 to 1. */
    val history = FloatArray(BANDS * COLUMNS)

    /** The broadband swell: a fast attack and a held peak before a slow release. */
    var swell = 0f
        private set

    /** How far the bloom has turned, in radians: integrated, so a change of speed never jumps the angle. */
    val turned: Float get() = turnedSoFar.toFloat()

    fun turn(dt: Double) {
        turnedSoFar += dt.coerceIn(0.0, 0.1) * (0.085 + swell * 0.22)
    }

    fun listen(frame: FloatArray, count: Int, now: Double) {
        audio.listen(frame, count)
        val heard = audio.spectrum
        for (i in spec.indices) {
            val k = if (heard[i] > spec[i]) 0.42f else 0.12f
            spec[i] += (heard[i] - spec[i]) * k
        }
        var sum = 0f
        for (i in 2 until 220) sum += spec[i]
        val level = sum / 218f
        if (level > swell) {
            swell += (level - swell) * 0.35f
            swellHold = now + 0.30
        } else if (now >= swellHold) {
            swell += (level - swell) * 0.012f
        }
        scroll()
    }

    /** Every row shifts one column outward, then this instant goes in at column 0. Bands are log spaced. */
    private fun scroll() {
        for (band in 0 until BANDS) {
            val lo = (2.0 * 210.0.pow(band.toDouble() / BANDS)).toInt()
            var hi = (2.0 * 210.0.pow((band + 1).toDouble() / BANDS)).toInt()
            if (hi <= lo) hi = lo + 1
            if (hi > 460) hi = 460
            var sum = 0f
            for (i in lo until hi) sum += spec[i]
            // A gentle knee and no gain in front of it: real music fills far more of the wheel than a test tone.
            val v = min(1f, (sum / (hi - lo)).pow(0.85f))
            val row = band * COLUMNS
            for (c in COLUMNS - 1 downTo 1) history[row + c] = history[row + c - 1]
            history[row] = v
        }
    }

    companion object {
        /** The frequency wheel's resolution. */
        const val BANDS = 64

        /** Time columns: about two and a half seconds at the forty-hertz reading. */
        const val COLUMNS = 96
    }
}

/**
 * A flower of light that is also a picture of the music: the iPhone's `BloomView` and `Bloom.metal`. Twelve thousand
 * petals on a phyllotaxis spiral, each reading the spectrogram at its own angle and radius, and seven hundred motes
 * drifting up around it. One instanced draw of soft quads, added together with no depth and no sorting, so light
 * adds up where petals overlap and gives the heart its glow. The camera orbits slowly and rises and falls.
 */
class BloomLook : StageRenderer {
    /** Half: every petal is a soft blur, and twelve thousand of them added at full size cost the GPU the most here. */
    override val scale = 0.5f

    private val field = BloomField()
    private val bytes = bytes(BloomField.BANDS * BloomField.COLUMNS)
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private var drawing = 0
    private var history = 0
    private var empty = 0
    private var matrixAt = 0
    private var cameraAt = 0
    private var timeAt = 0
    private var swellAt = 0
    private var turnAt = 0
    private var resolutionAt = 0

    override fun created() {
        drawing = program(VERTEX, FRAGMENT)
        matrixAt = GLES30.glGetUniformLocation(drawing, "viewProjection")
        cameraAt = GLES30.glGetUniformLocation(drawing, "camera")
        timeAt = GLES30.glGetUniformLocation(drawing, "time")
        swellAt = GLES30.glGetUniformLocation(drawing, "swell")
        turnAt = GLES30.glGetUniformLocation(drawing, "turn")
        resolutionAt = GLES30.glGetUniformLocation(drawing, "resolution")
        history = byteTexture(BloomField.COLUMNS, BloomField.BANDS)
        empty = emptyVertexArray()
    }

    override fun hear(frame: FloatArray, count: Int, now: Double, since: Double) {
        field.listen(frame, count, now)
    }

    override fun draw(width: Int, height: Int, now: Double, since: Double) {
        field.turn(since)
        uploadBytes(history, BloomField.COLUMNS, BloomField.BANDS, field.history, bytes)

        val t = now.toFloat()
        val swell = field.swell
        val orbit = t * 0.043f
        val pitch = 0.62f + sin(t * 0.037f) * 0.34f
        val dist = 2.42f - swell * 0.20f + sin(t * 0.021f) * 0.22f
        val ex = cos(orbit) * cos(pitch) * dist
        val ey = sin(pitch) * dist
        val ez = sin(orbit) * cos(pitch) * dist
        Matrix.setLookAtM(view, 0, ex, ey, ez, 0f, 0.10f, 0f, 0f, 1f, 0f)
        perspective(projection, (PI / 3.4).toFloat(), width.toFloat() / height.coerceAtLeast(1), 0.05f, 24f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

        GLES30.glClearColor(0.006f, 0.010f, 0.024f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_BLEND)
        // Additive: overlapping petals accumulate into the glow at the heart.
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glUseProgram(drawing)
        GLES30.glUniformMatrix4fv(matrixAt, 1, false, viewProjection, 0)
        GLES30.glUniform3f(cameraAt, ex, ey, ez)
        GLES30.glUniform1f(timeAt, t)
        GLES30.glUniform1f(swellAt, swell)
        GLES30.glUniform1f(turnAt, field.turned)
        GLES30.glUniform2f(resolutionAt, width.toFloat(), height.toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, history)
        GLES30.glBindVertexArray(empty)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, PETALS + MOTES)
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /** A perspective in GL's own depth range, which is what the iPhone's matrix stands for in Metal's. */
    private fun perspective(into: FloatArray, fov: Float, aspect: Float, near: Float, far: Float) {
        val up = 1f / tan(fov * 0.5f)
        java.util.Arrays.fill(into, 0f)
        into[0] = up / aspect
        into[5] = up
        into[10] = (far + near) / (near - far)
        into[11] = -1f
        into[14] = 2f * far * near / (near - far)
    }

    private companion object {
        const val PETALS = 12_000
        const val MOTES = 700

        const val VERTEX = """#version 300 es
precision highp float;
uniform mat4 viewProjection;
uniform vec3 camera;
uniform float time;
uniform float swell;
uniform float turn;
uniform sampler2D history;
out vec2 corner;
out vec3 tint;
out float energy;
out float rim;

const int PETALS = 12000;
const float RADIUS = 1.62;
const float GOLDEN = 2.39996323;

float hash1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }

void main() {
    // One quad of two triangles, from the vertex number.
    int v = gl_VertexID;
    vec2 c = v == 0 ? vec2(-1.0, -1.0) : v == 1 ? vec2(1.0, -1.0) : v == 2 ? vec2(1.0, 1.0)
           : v == 3 ? vec2(-1.0, -1.0) : v == 4 ? vec2(1.0, 1.0) : vec2(-1.0, 1.0);
    corner = c;
    int instance = gl_InstanceID;
    float i = float(instance);

    // The last instances are motes: they drift up and around like pollen and answer the top end.
    if (instance >= PETALS) {
        float m = float(instance - PETALS);
        float ha = hash1(m * 1.13), hb = hash1(m * 2.71), hc = hash1(m * 5.37);
        float ma = ha * 6.2831853 + time * (0.02 + hb * 0.05);
        float mr = RADIUS * (0.35 + hb * 1.25);
        float rise = fract(hc + time * (0.010 + ha * 0.022));
        float my = -0.25 + rise * 1.55;
        vec3 centre = vec3(cos(ma) * mr, my, sin(ma) * mr);
        float spark = texture(history, vec2(0.04, 0.72 + hb * 0.26)).r;
        float twinkle = 0.45 + 0.55 * sin(time * (1.1 + hc * 2.2) + m);
        float e = (0.22 + spark * 0.85) * twinkle * smoothstep(0.0, 0.12, rise) * (1.0 - smoothstep(0.72, 1.0, rise));
        float msize = 0.0060 + hc * 0.0070;
        vec3 viewDir = normalize(camera - centre);
        vec3 mr1 = normalize(cross(viewDir, vec3(0.0, 1.0, 0.0)));
        vec3 mr2 = normalize(cross(viewDir, mr1));
        vec3 world = centre + mr1 * (c.x * msize) + mr2 * (c.y * msize);
        tint = mix(vec3(0.55, 0.88, 1.0), vec3(1.0), spark * 0.6);
        energy = e;
        rim = 1.0;
        gl_Position = viewProjection * vec4(world, 1.0);
        return;
    }

    float f = (i + 0.5) / float(PETALS);
    float rf = pow(f, 0.62);
    float jitter = hash1(i * 1.7);
    float ang = i * GOLDEN + turn * (1.35 - 0.55 * rf) + (jitter - 0.5) * 0.045;
    float r = RADIUS * (0.055 + 0.945 * rf) * (1.0 + (hash1(i * 3.1) - 0.5) * 0.035);

    // The petal's own value from the music: its angle gives the band, its radius gives the age.
    float band = fract(ang / 6.2831853);
    float age = clamp(rf, 0.0, 1.0);
    float e = texture(history, vec2(age, band)).r;
    e = e / (0.55 + e);
    e *= 1.0 - smoothstep(0.55, 1.0, rf);
    e = max(e, exp(-rf * 6.0) * (0.16 + swell * 0.30));
    energy = e;

    float dome = (1.0 - rf * rf) * (0.10 + swell * 0.18);
    float y = dome + e * (0.20 + swell * 0.16) + (hash1(i) - 0.5) * 0.010;
    vec3 centre = vec3(cos(ang) * r, y, sin(ang) * r);

    float size = (0.0105 + 0.0075 * rf) * (0.55 + e * 2.0 + swell * 0.25) * (1.0 + 1.9 * exp(-rf * 7.0));
    float longAxis = size * (1.45 + e * 2.3);
    float shortAxis = size * 0.55;

    vec3 radial = vec3(cos(ang), 0.0, sin(ang));
    vec3 viewDir = normalize(camera - centre);
    radial = normalize(radial - viewDir * dot(radial, viewDir));
    vec3 across = normalize(cross(viewDir, radial));
    float lean = 0.26 + smoothstep(0.02, 0.95, rf) * 0.62;
    vec3 along = normalize(mix(radial, across, lean));
    vec3 side = normalize(cross(viewDir, along));
    vec3 world = centre + along * (c.x * longAxis) + side * (c.y * shortAxis);

    vec3 low = vec3(0.05, 0.17, 0.95);
    vec3 middle = vec3(0.02, 0.78, 1.00);
    vec3 high = vec3(0.86, 0.97, 1.00);
    vec3 col = band < 0.5 ? mix(low, middle, band * 2.0) : mix(middle, high, (band - 0.5) * 2.0);
    col = mix(col, vec3(1.0), clamp(e * e * 0.35, 0.0, 0.40));

    tint = col;
    rim = rf;
    gl_Position = viewProjection * vec4(world, 1.0);
}
"""

        const val FRAGMENT = """#version 300 es
precision highp float;
uniform vec2 resolution;
in vec2 corner;
in vec3 tint;
in float energy;
in float rim;
out vec4 colour;

void main() {
    // A bright core inside a wide, weak halo, which joins neighbouring petals into arms of light.
    float d = dot(corner, corner);
    float core = exp(-d * 8.0);
    float halo = exp(-d * 2.0) * 0.45;
    float heart = 1.0 + 0.85 * exp(-rim * 4.0);
    float a = (core + halo) * (0.045 + energy * 0.42) * heart;
    vec3 col = tint * (0.55 + energy * 0.95) + vec3(0.50, 0.85, 1.0) * core * energy * 0.25;
    col = mix(col, vec3(0.72, 0.93, 1.0), exp(-rim * 6.0) * 0.45);
    // The corners of the frame stay dark.
    vec2 uv = gl_FragCoord.xy / resolution;
    a *= uv.x * (1.0 - uv.x) * uv.y * (1.0 - uv.y) * 26.0 * 0.6 + 0.34;
    colour = vec4(col * a, a);
}
"""
    }
}
