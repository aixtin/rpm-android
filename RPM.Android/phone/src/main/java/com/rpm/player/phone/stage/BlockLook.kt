package com.rpm.player.phone.stage

import android.opengl.GLES30
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A flight through a field of turning blocks, lit by the track: the iPhone's `BlockFlow` (RPMStages/BlockFlow.swift),
 * figure for figure. The field is a lattice that scrolls with the camera; everything a cell owns (its size, height,
 * spin, whether it burns and which band lights it) is hashed from its coordinates, as in the desktop's shader. The
 * placing, turning, lighting and projecting are done here, and [faces] is what is drawn: every visible face, far row
 * first, as two triangles of one colour each, x, y, r, g, b per corner.
 */
class BlockFlow(frameSize: Int = 2048) {
    private val audio = ShaderAudio(frameSize)
    private val spec = FloatArray(ShaderAudio.WIDTH)
    private val corner = FloatArray(8 * 3)
    private val screen = FloatArray(8 * 2)
    private var swellHold = 0.0
    private var flownSoFar = 0.0

    /** The broadband level, enveloped. It lifts the light, the block scale and the flight's speed at once. */
    var swell = 0f
        private set

    /** The frame's faces, six corners each (two triangles), five floats a corner. */
    val faces = FloatArray(COLUMNS * ROWS * 3 * 6 * 5)

    /** How many floats of [faces] are this frame's. */
    var filled = 0
        private set

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
    }

    /** Moves the flight on. Distance is integrated, so the music can push the speed without the field jumping. */
    fun advance(seconds: Double) {
        flownSoFar += seconds.coerceIn(0.0, 0.1) * (1.30 + swell * 1.10)
    }

    /** Builds the frame: every visible face of every block, placed, lit and projected. */
    fun fill(time: Double, widthOverHeight: Float) {
        filled = 0
        val t = time.toFloat()
        val camZ = flownSoFar.toFloat()

        // The camera glides above the sheet the blocks lie on and looks along it, following the sheet's height.
        val eyeX = sin(t * 0.13f) * 1.8f
        val aimX = sin(t * 0.11f + 1.3f) * 2.6f
        val ex = eyeX
        val ey = sheet(eyeX, camZ, t) + 0.55f + sin(t * 0.19f) * 0.30f
        val ez = camZ
        val ax = aimX
        val ay = sheet(aimX, camZ + 7.5f, t) + 0.10f
        val az = camZ + 7.5f
        val ux = sin(t * 0.05f) * 0.42f
        val uy = 1f
        val uz = 0f

        // zaxis = normalise(eye - aim), xaxis = normalise(up × z), yaxis = z × x.
        var zx = ex - ax
        var zy = ey - ay
        var zz = ez - az
        var n = sqrt(zx * zx + zy * zy + zz * zz)
        zx /= n; zy /= n; zz /= n
        var xx = uy * zz - uz * zy
        var xy = uz * zx - ux * zz
        var xz = ux * zy - uy * zx
        n = sqrt(xx * xx + xy * xy + xz * xz)
        xx /= n; xy /= n; xz /= n
        val yx = zy * xz - zz * xy
        val yy = zz * xx - zx * xz
        val yz = zx * xy - zy * xx

        val yScale = 1 / tan(PI.toFloat() / 3.1f * 0.5f)
        val xScale = yScale / max(0.1f, widthOverHeight)

        // The lamp is just above and behind the lens.
        val lx = ex
        val ly = ey + 3.2f
        val lz = ez + 2.0f

        for (row in 0 until ROWS) {
            // Far row first: the painter's order that keeps an opaque field readable without a depth buffer.
            val ck = floor(camZ / CELL) + (ROWS - 1 - row)
            for (col in 0 until COLUMNS) {
                val ci = col - COLUMNS * 0.5f + 0.5f
                val h1 = hash(ci, ck)
                val h2 = hash(ci + 17.31f, ck + 17.31f)
                val h3 = hash(ci + 41.77f, ck + 41.77f)
                val h4 = hash(ci + 73.19f, ck + 73.19f)

                val cx = (ci + (h3 - 0.5f) * 0.55f) * CELL
                val cz = (ck + (h4 - 0.5f) * 0.55f) * CELL + NEAR_SKIP
                val cy = (h2 - 0.5f) * 1.5f + sheet(cx, cz, t)

                val size = (0.05f + h1 * 0.22f) * (1.0f + swell * 0.30f)
                val a1 = t * (0.35f + h3 * 0.55f) + h1 * 2 * PI.toFloat()
                val a2 = t * (0.28f + h4 * 0.45f) + h2 * 2 * PI.toFloat()
                val s1 = sin(a1)
                val c1 = kotlin.math.cos(a1)
                val s2 = sin(a2)
                val c2 = kotlin.math.cos(a2)

                var behind = false
                for (i in 0 until 8) {
                    // Spin about Y, then tumble about X, then place.
                    val px = CUBE[i * 3] * size
                    val py = CUBE[i * 3 + 1] * size
                    val pz = CUBE[i * 3 + 2] * size
                    val qx = px * c1 - pz * s1
                    val qz = px * s1 + pz * c1
                    val wx = qx + cx
                    val wy = py * c2 - qz * s2 + cy
                    val wz = py * s2 + qz * c2 + cz
                    corner[i * 3] = wx
                    corner[i * 3 + 1] = wy
                    corner[i * 3 + 2] = wz
                    val fx = wx - ex
                    val fy = wy - ey
                    val fz = wz - ez
                    val w = -(fx * zx + fy * zy + fz * zz)
                    // A block straddling the lens is dropped, not clipped.
                    if (w <= 0.001f) {
                        behind = true
                        break
                    }
                    screen[i * 2] = (fx * xx + fy * xy + fz * xz) * xScale / w
                    screen[i * 2 + 1] = (fx * yx + fy * yy + fz * yz) * yScale / w
                }
                if (behind) continue

                // A minority of blocks burn rather than reflect, each on its own band.
                var glow = 0f
                if (h4 >= 0.78f) {
                    val where = frac(h3 * 0.8f + t * 0.01f) * 0.55f + 0.02f
                    glow = band(where).pow(2.2f) * 1.9f
                }
                val tr = 0.05f + (0.03f - 0.05f) * h1
                val tg = 0.26f + (0.15f - 0.26f) * h1
                val tb = 0.62f + (0.42f - 0.62f) * h1

                for (f in 0 until 6) {
                    val nx0 = NORMALS[f * 3]
                    val ny0 = NORMALS[f * 3 + 1]
                    val nz0 = NORMALS[f * 3 + 2]
                    val qx = nx0 * c1 - nz0 * s1
                    val qz = nx0 * s1 + nz0 * c1
                    val nx = qx
                    val ny = ny0 * c2 - qz * s2
                    val nz = ny0 * s2 + qz * c2

                    val q0 = QUADS[f * 4]
                    val q1 = QUADS[f * 4 + 1]
                    val q2 = QUADS[f * 4 + 2]
                    val q3 = QUADS[f * 4 + 3]
                    val mx = (corner[q0 * 3] + corner[q1 * 3] + corner[q2 * 3] + corner[q3 * 3]) * 0.25f
                    val my = (corner[q0 * 3 + 1] + corner[q1 * 3 + 1] + corner[q2 * 3 + 1] + corner[q3 * 3 + 1]) * 0.25f
                    val mz = (corner[q0 * 3 + 2] + corner[q1 * 3 + 2] + corner[q2 * 3 + 2] + corner[q3 * 3 + 2]) * 0.25f
                    val tcx = ex - mx
                    val tcy = ey - my
                    val tcz = ez - mz
                    // Facing away.
                    if (nx * tcx + ny * tcy + nz * tcz <= 0f) continue

                    val sx = (screen[q0 * 2] + screen[q1 * 2] + screen[q2 * 2] + screen[q3 * 2]) * 0.25f
                    val sy = (screen[q0 * 2 + 1] + screen[q1 * 2 + 1] + screen[q2 * 2 + 1] + screen[q3 * 2 + 1]) * 0.25f

                    // One lamp, then the fog into the navy, then the vignette at the face's place on the screen.
                    val dist = sqrt(tcx * tcx + tcy * tcy + tcz * tcz)
                    val vx = tcx / max(dist, 0.001f)
                    val vy = tcy / max(dist, 0.001f)
                    val vz = tcz / max(dist, 0.001f)
                    var lgx = lx - mx
                    var lgy = ly - my
                    var lgz = lz - mz
                    val ln = sqrt(lgx * lgx + lgy * lgy + lgz * lgz)
                    if (ln > 1e-6f) { lgx /= ln; lgy /= ln; lgz /= ln } else { lgx = 0f; lgy = 0f; lgz = 0f }
                    val diffuse = max(nx * lgx + ny * lgy + nz * lgz, 0f)
                    // reflected = -light - 2 * dot(-light, n) * n
                    val dn = -(lgx * nx + lgy * ny + lgz * nz)
                    val rx = -lgx - 2 * dn * nx
                    val ry = -lgy - 2 * dn * ny
                    val rz = -lgz - 2 * dn * nz
                    val specular = pow26(max(rx * vx + ry * vy + rz * vz, 0f))
                    val atten = 5.5f / (1 + dist * 0.28f + dist * dist * 0.045f)
                    val lit = (0.12f + diffuse * (0.85f + swell * 0.6f)) * atten
                    val shine = specular * atten * 0.45f
                    val burn = glow * min(1.4f, max(0.25f, atten))
                    var r = tr * lit + 0.75f * shine + 1.0f * burn
                    var g = tg * lit + 0.90f * shine + 0.58f * burn
                    var b = tb * lit + 1.0f * shine + 0.10f * burn
                    val fog = exp(-dist * FOG)
                    val vig = vignette(sx, sy)
                    r = (BACKGROUND_R + (r - BACKGROUND_R) * fog) * vig
                    g = (BACKGROUND_G + (g - BACKGROUND_G) * fog) * vig
                    b = (BACKGROUND_B + (b - BACKGROUND_B) * fog) * vig

                    put(q0, r, g, b); put(q1, r, g, b); put(q2, r, g, b)
                    put(q0, r, g, b); put(q2, r, g, b); put(q3, r, g, b)
                }
            }
        }
    }

    private fun put(i: Int, r: Float, g: Float, b: Float) {
        faces[filled++] = screen[i * 2]
        faces[filled++] = screen[i * 2 + 1]
        faces[filled++] = r
        faces[filled++] = g
        faces[filled++] = b
    }

    private fun band(where: Float): Float = spec[(where.coerceIn(0f, 1f) * (ShaderAudio.WIDTH - 1)).toInt()]

    companion object {
        const val COLUMNS = 20
        const val ROWS = 36
        private const val CELL = 0.62f
        private const val NEAR_SKIP = 1.4f
        private const val FOG = 2.3f / (ROWS * CELL)

        /** The navy everything fogs into; the look clears to it. */
        const val BACKGROUND_R = 0.012f
        const val BACKGROUND_G = 0.020f
        const val BACKGROUND_B = 0.043f

        private val CUBE = floatArrayOf(
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
        )
        private val QUADS = intArrayOf(4, 5, 6, 7, 1, 0, 3, 2, 5, 1, 2, 6, 0, 4, 7, 3, 7, 6, 2, 3, 0, 1, 5, 4)
        private val NORMALS = floatArrayOf(0f, 0f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f)

        private fun sheet(x: Float, z: Float, t: Float): Float = sin(z * 0.35f + t * 0.30f) * 0.85f + sin(x * 0.42f - t * 0.13f) * 0.65f

        private fun hash(x: Float, y: Float): Float = frac(sin(x * 127.1f + y * 311.7f) * 43758.5453f)

        private fun frac(v: Float): Float = v - floor(v)

        private fun vignette(x: Float, y: Float): Float {
            val u = x * 0.5f + 0.5f
            val v = y * 0.5f + 0.5f
            return (u * (1 - u) * v * (1 - v) * 32 * 0.75f + 0.25f).coerceIn(0f, 1.8f)
        }

        private fun pow26(x: Float): Float {
            val x2 = x * x
            val x4 = x2 * x2
            val x8 = x4 * x4
            return x8 * x8 * x8 * x2
        }
    }
}

/**
 * The flight through the block field, drawn: the iPhone's `BlockView`. Every face already knows its colour, so the
 * faces are drawn in the order given, far row first, in one draw. On the iPhone a thousand separate fills hold this
 * look to about 22 frames a second; here they are one buffer of triangles.
 */
class BlockLook : StageRenderer {
    override val scale = 0.67f

    private val flow = BlockFlow()
    private val upload = floats(flow.faces.size)
    private var drawing = 0
    private var vertices = 0
    private var buffer = 0

    override fun created() {
        drawing = program(VERTEX, FRAGMENT)
        vertices = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
        buffer = arrayBuffer()
        GLES30.glBindVertexArray(vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, flow.faces.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 20, 8)
        GLES30.glBindVertexArray(0)
    }

    override fun hear(frame: FloatArray, count: Int, now: Double, since: Double) {
        flow.listen(frame, count, now)
    }

    override fun draw(width: Int, height: Int, now: Double, since: Double) {
        // The flight advances every frame, not every reading: distance is travelled by the clock.
        flow.advance(since)
        flow.fill(now, width.toFloat() / height)

        GLES30.glClearColor(BlockFlow.BACKGROUND_R, BlockFlow.BACKGROUND_G, BlockFlow.BACKGROUND_B, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_BLEND)
        if (flow.filled == 0) return
        upload.clear()
        upload.put(flow.faces, 0, flow.filled)
        upload.flip()
        GLES30.glUseProgram(drawing)
        GLES30.glBindVertexArray(vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, flow.filled * 4, upload)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, flow.filled / 5)
        GLES30.glBindVertexArray(0)
    }

    private companion object {
        const val VERTEX = """#version 300 es
layout(location = 0) in vec2 at;
layout(location = 1) in vec3 lit;
out vec3 shade;
void main() {
    shade = lit;
    gl_Position = vec4(at, 0.0, 1.0);
}
"""

        const val FRAGMENT = """#version 300 es
precision mediump float;
in vec3 shade;
out vec4 colour;
void main() { colour = vec4(clamp(shade, 0.0, 1.0), 1.0); }
"""
    }
}
