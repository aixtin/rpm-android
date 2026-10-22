package com.rpm.player.phone.stage

import android.content.res.Resources
import android.opengl.GLES30

/**
 * The marched visualisers: the lattice, the kaleidoscope and the tunnel. The iPhone's `MarchedView` with its three
 * shaders and their finish (`MarchShaders.metal`), ported to GLSL ES 3.00 line for line.
 *
 * **Two passes, one march.** The picture is marched into a buffer, then a finish reads it back: bloom, tone map,
 * saturate, vignette. Drawing the picture twice to blur one copy would march every ray twice, and the march is the
 * whole cost. The march writes its light range-compressed, so eight bits do not clip what the bloom is made of, and
 * the finish expands it again.
 *
 * **The timing comes from the cutting room, not from a clock** ([VizDirector]): the camera holds a shot for two bars
 * and cuts on a beat, the room changes on the eight-bar phrase. On the phones that grid is the stand-in clock.
 *
 * Every look is lit by the same three bands ([MarchBands]), which the iPhone's stage also shares between them.
 */
class MarchedLook(private val kind: Kind) : StageRenderer {
    enum class Kind { Lattice, Kaleidoscope, Tunnel }

    /**
     * A sixth of the screen's pixels across. A ray march a pixel is what this phone's GPU spends most on: on the Titan
     * (Mali-G72 MP3) a quarter across ran the lattice at 20 frames a second and the tunnel, at 0.22, at 16. The
     * finish's bloom softens the stretch to the screen. The tunnel marches a second ray for its reflection, so it gets
     * fewer pixels again.
     */
    override val scale = when (kind) {
        Kind.Tunnel -> 0.15f
        else -> 0.17f
    }

    private val bands = MarchBands()
    private val density = Resources.getSystem().displayMetrics.density
    private var march = 0
    private var finish = 0
    private var empty = 0
    private var target = 0
    private var picture = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private val at = HashMap<String, Int>()
    private val finishAt = HashMap<String, Int>()

    override fun created() {
        march = program(FULL_FRAME, COMMON + when (kind) {
            Kind.Lattice -> LATTICE
            Kind.Kaleidoscope -> KALEIDOSCOPE
            Kind.Tunnel -> TUNNEL
        })
        finish = program(FULL_FRAME, FINISH)
        empty = emptyVertexArray()
        at.clear()
        finishAt.clear()
        targetWidth = 0
        targetHeight = 0
    }

    override fun hear(frame: FloatArray, count: Int, now: Double, since: Double) {
        bands.listen(frame, count)
    }

    private fun uniform(name: String): Int = at.getOrPut(name) { GLES30.glGetUniformLocation(march, name) }

    private fun finishUniform(name: String): Int = finishAt.getOrPut(name) { GLES30.glGetUniformLocation(finish, name) }

    /** The buffer the march draws into, the size of the surface, read back linearly by the finish. */
    private fun ensureTarget(width: Int, height: Int) {
        if (width == targetWidth && height == targetHeight && target != 0) return
        if (target != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(target), 0)
            GLES30.glDeleteTextures(1, intArrayOf(picture), 0)
        }
        picture = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, picture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        target = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, picture, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        targetWidth = width
        targetHeight = height
    }

    override fun draw(width: Int, height: Int, now: Double, since: Double) {
        ensureTarget(width, height)
        val shot = VizDirector.free(now)
        val walls = VizRoom.blend(shot.was, shot.now, shot.blend)
        val w = width.toFloat()
        val h = height.toFloat()

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(march)
        GLES30.glUniform2f(uniform("size"), w, h)
        GLES30.glUniform3f(uniform("camera"), shot.orbit, shot.fov, shot.sway)
        GLES30.glUniform1f(uniform("palette"), shot.palette.toFloat())
        GLES30.glUniform1f(uniform("steps"), STEPS)
        GLES30.glUniform3f(uniform("bands"), bands.bass, bands.middle, bands.air)
        when (kind) {
            Kind.Lattice -> {
                // The rotations are smooth only, and slow: a four-dimensional rotation changes every cell at once.
                val b = shot.beats
                GLES30.glUniform4f(uniform("slice"), b * 0.22f, b * 0.010f, b * 0.007f, b * 0.004f)
                GLES30.glUniform4f(
                    uniform("room"),
                    0.85f + walls.ring * 0.15f,
                    walls.girth * (0.35f + 0.55f * bands.middle),
                    walls.girth * (0.45f + 0.85f * bands.bass) * (1 + walls.cross * 0.4f),
                    3.2f + 2.4f * bands.air,
                )
                GLES30.glUniform3f(uniform("beat"), shot.beats, shot.pulse, shot.drop)
            }
            Kind.Kaleidoscope -> {
                // The glitch fires as a section starts: derived from the blend, which knows how long a bar is.
                val glitch = maxOf(if (shot.blend < 0.25f) 1 - shot.blend * 4 else 0f, if (shot.drop > 0.85f) (shot.drop - 0.85f) * 6.6f else 0f)
                GLES30.glUniform1f(uniform("travel"), shot.travelled * 1.3f)
                GLES30.glUniform3f(uniform("room1"), shot.now.folds, walls.arm, walls.twist)
                GLES30.glUniform3f(uniform("room2"), walls.ring, walls.girth, walls.cross)
                GLES30.glUniform3f(uniform("beat"), shot.beats, glitch, shot.drop)
                GLES30.glUniform3f(uniform("shape"), shot.now.solid, shot.was.solid, shot.blend)
            }
            Kind.Tunnel -> {
                GLES30.glUniform1f(uniform("travelled"), shot.travelled * 1.6f)
                GLES30.glUniform3f(uniform("room1"), shot.now.folds, walls.arm, walls.twist)
                GLES30.glUniform3f(uniform("room2"), walls.ring, walls.girth, walls.cross)
                GLES30.glUniform3f(uniform("beat"), shot.beats, shot.pulse, shot.drop)
            }
        }
        drawFullFrame(empty)

        // The finish, onto the surface.
        val (bloom, saturate) = when (kind) {
            Kind.Lattice -> (0.6f + 0.6f * bands.air) to 1.35f
            Kind.Kaleidoscope -> (0.20f + 0.35f * bands.middle) to 1.55f
            Kind.Tunnel -> 0.6f to 1.0f
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(finish)
        GLES30.glUniform2f(finishUniform("size"), w, h)
        GLES30.glUniform1f(finishUniform("bloom"), bloom)
        GLES30.glUniform1f(finishUniform("saturate"), saturate)
        // The halo's taps are set in points on the iPhone; this is a point in the buffer's pixels.
        GLES30.glUniform1f(finishUniform("point"), density * scale)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, picture)
        drawFullFrame(empty)
    }

    private companion object {
        /**
         * The most steps a ray may take. The iPhone takes 64. The desktop's governor lowers the steps before the
         * resolution, because fewer steps only shorten the view distance, which fades out at the far end; this phone
         * takes 48.
         */
        const val STEPS = 48f

        const val FINISH = """#version 300 es
precision highp float;
uniform sampler2D layer;
uniform vec2 size;
uniform float bloom;
uniform float saturate;
uniform float point;
out vec4 colour;

const float TAU = 6.28318530718;

// Undoes the range compression the march applied, clamped away from 1, where the inverse goes to infinity.
vec3 expand(vec3 c) { return c / max(1.0 - c, 0.004); }

vec3 at(vec2 position) { return texture(layer, position / size).rgb; }

void main() {
    vec2 position = gl_FragCoord.xy;
    vec2 uv = position / size;
    vec3 col = expand(at(position));

    // Two rings of taps, near and far: a halo with a bright core and a wider spread, not a box's cross.
    vec3 halo = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = TAU * (float(i) + 0.5) / 8.0;
        vec2 dir = vec2(cos(a), sin(a));
        halo += expand(at(position + dir * 2.5 * point)) * 0.65;
        halo += expand(at(position + dir * 7.0 * point)) * 0.35;
    }
    col += halo * bloom * 0.045;

    // Tone map, then restore the colour the curve took out.
    col = 1.0 - exp(-col * 0.95);
    float grey = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = clamp(mix(vec3(grey), col, saturate), 0.0, 1.0);

    col *= 0.55 + 0.45 * pow(16.0 * uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y), 0.22);

    colour = vec4(pow(max(col, 0.0), vec3(1.0 / 2.2)), 1.0);
}
"""

        const val COMMON = """#version 300 es
precision highp float;
out vec4 colour;

const float TAU = 6.28318530718;

// As the iPhone's `rot`: a row vector times this matrix turns it by a.
mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

// Folds the plane into `count` identical wedges about the origin.
vec2 wedge(vec2 p, float count) {
    float sector = TAU / count;
    float a = mod(atan(p.y, p.x) + 0.5 * sector, sector) - 0.5 * sector;
    return vec2(cos(a), sin(a)) * length(p);
}

float boxDistance(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

// A regular prism: one cut across a folded wedge gives all its faces at once.
float prism(vec3 p, float sides, float across, float deep) {
    float sector = TAU / sides;
    float a = mod(atan(p.y, p.x) + 0.5 * sector, sector) - 0.5 * sector;
    float face = cos(a) * length(p.xy) - across;
    return max(face, abs(p.z) - deep);
}

float octa(vec3 p, float s) { return (abs(p.x) + abs(p.y) + abs(p.z) - s) * 0.57735; }

// The three axis-aligned bars through a cell, as one distance.
float cutout(vec3 p, float r) {
    vec3 q = abs(p);
    return min(max(q.x, q.y), min(max(q.y, q.z), max(q.x, q.z))) - r;
}
"""

        const val LATTICE = """
uniform vec2 size;
uniform vec3 camera;
uniform vec4 slice;
uniform vec4 room;
uniform vec3 beat;
uniform vec3 bands;
uniform float palette;
uniform float steps;

// A grid rotated in four dimensions and turned inside out about the origin. `swell` is how much the inversion
// magnified this point; the distance is divided by it to stay a distance.
float latticeGrid(vec3 p, out float swell, out vec3 cellId, out vec3 offset) {
    float cellSize = room.x, wire = room.y, bead = room.z, pull = room.w;
    vec4 q = vec4(p, slice.x);
    q.xw = q.xw * rot(slice.y);
    q.yw = q.yw * rot(slice.z);
    q.zw = q.zw * rot(slice.w);
    // Both ends clamped: an inversion has a singularity at the origin, and the clamp is the difference between an
    // inversion and a hang.
    swell = clamp(pull / max(dot(q, q), 0.6), 0.15, 6.0);
    q *= swell;
    q -= slice.x * 0.35;
    vec4 cell = q / cellSize;
    vec4 home = floor(cell + 0.5);
    cellId = home.xyz;
    vec4 away = (cell - home) * cellSize;
    offset = away.xyz;
    vec4 a = abs(away);
    float wires = min(min(length(a.yzw), length(a.xzw)), min(length(a.xyw), length(a.xyz))) - wire;
    float knot = length(a) - bead;
    return min(wires, knot) / swell;
}

// A cell's own colour, hashed from its identity, iridescent with the viewing angle.
vec3 latticeColour(vec3 id, float swell, float sheen, float palette) {
    float h = fract(sin(dot(id, vec3(12.989, 78.233, 37.719))) * 43758.5453);
    vec3 phase = palette < 0.5 ? vec3(0.15, 0.45, 0.75)
               : palette < 1.5 ? vec3(0.95, 0.30, 0.60)
               : palette < 2.5 ? vec3(0.40, 0.70, 0.15)
                               : vec3(0.05, 0.35, 0.65);
    float shift = h * 0.35 + 0.12 * log2(max(swell, 0.05)) + 0.28 * sheen;
    return clamp(0.5 + 0.72 * cos(TAU * (shift + phase)), 0.0, 1.0);
}

void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = (frag - 0.5 * size) / size.y;
    float beats = beat.x, drop = beat.z;
    int allowed = int(steps);

    vec3 ro = vec3(0.0, 0.0, -3.0);
    vec3 rd = normalize(vec3(uv, camera.y));
    rd.xy = rd.xy * rot(camera.x * 0.5);

    float dither = fract(sin(dot(frag, vec2(11.317, 57.909)) + beats) * 39181.3717);
    float t = dither * 0.2;
    vec3 col = vec3(0.0);

    for (int i = 0; i < 96; i++) {
        if (i >= allowed) break;
        float swell;
        vec3 id, offset;
        float d = latticeGrid(ro + rd * t, swell, id, offset);
        float sheen = dot(normalize(offset + 1e-5), rd);
        float stride = max(abs(d), 0.02) * 0.75;
        // Gathered and never stopped: a lattice of light.
        col += latticeColour(id, swell, sheen, palette) * (stride * (0.026 / (0.004 + d * d))) * (1.0 + 0.5 * drop) * exp(-t * 0.055);
        t += stride;
    }

    float level = 0.35 + 1.15 * (bands.x * 0.4 + bands.y * 0.3 + bands.z * 0.3);
    col *= level * 1.05;
    colour = vec4(col / (1.0 + col), 1.0);
}
"""

        const val KALEIDOSCOPE = """
uniform vec2 size;
uniform float travel;
uniform vec3 camera;
uniform vec3 room1;
uniform vec3 room2;
uniform vec3 beat;
uniform vec3 shape;
uniform float palette;
uniform vec3 bands;
uniform float steps;

// The solid the mirrors show, one of four per section.
float seed(vec3 p, float which, float girth, float arm) {
    if (which < 0.5) return boxDistance(p, vec3(girth * 1.5, arm * 0.22, girth * 1.5));
    if (which < 1.5) return prism(p, 3.0, girth * 2.0, arm * 0.22);
    if (which < 2.5) return prism(p, 6.0, girth * 1.8, arm * 0.24);
    return octa(p, girth * 2.6);
}

// Folded about the axis, turned, folded again at twice the count, mirrored, and cut into long cells along the
// flight: tracery too fine to count, seen from inside.
float filigree(vec3 p) {
    float folds = room1.x, arm = room1.y, twist = room1.z;
    float ring = room2.x, girth = room2.y, bite = room2.z;
    p.xy = p.xy * rot(p.z * twist);
    p.xy = wedge(p.xy, folds);
    p.x -= arm;
    p.xy = p.xy * rot(p.z * twist * 0.6);
    p.xy = wedge(p.xy, folds * 2.0);
    p.x -= arm * 0.42;
    p.xy = abs(p.xy) - vec2(arm * 0.16);
    p.z = mod(p.z + 1.1 * ring, 2.2 * ring) - 1.1 * ring;
    float solid = mix(seed(p, shape.y, girth, arm), seed(p, shape.x, girth, arm), shape.z);
    return max(solid, -cutout(p, girth * 0.85 * bite));
}

void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = (frag - 0.5 * size) / size.y;
    float beats = beat.x, glitch = beat.y, drop = beat.z;
    int allowed = int(steps);

    // The break: for a moment after the room changes, the picture is cut into blocks and its rows pulled sideways.
    if (glitch > 0.0) {
        float blocks = mix(140.0, 26.0, glitch);
        uv = floor(uv * blocks) / blocks;
        float band = floor(uv.y * 22.0);
        uv.x += (fract(sin(band * 91.7 + floor(beats)) * 4713.31) - 0.5) * glitch * 0.35;
    }

    vec3 ro = vec3(sin(beats * 0.11) * camera.z * 0.5, cos(beats * 0.08) * camera.z * 0.5, travel);
    vec3 rd = normalize(vec3(uv, camera.y));
    rd.xy = rd.xy * rot(camera.x * 0.5 + sin(beats * 0.05) * camera.z * 0.2);

    float dither = fract(sin(dot(frag, vec2(21.577, 43.191)) + beats) * 24634.6345);
    float t = dither * 0.3;
    float core = 0.0;
    float haze = 0.0;

    for (int i = 0; i < 96; i++) {
        if (i >= allowed) break;
        float d = max(abs(filigree(ro + rd * t)), 0.006);
        float air = exp(-t * 0.11);
        core += (0.0022 / (0.0022 + d * d)) * air;
        haze += (0.004 / (0.260 + d * d)) * air;
        t += d * 0.6;
        if (t > 70.0) break;
    }

    float fall = clamp(length(uv) * 1.20, 0.0, 1.0);
    vec3 lit  = palette < 0.5 ? vec3(1.00, 0.76, 0.30)
              : palette < 1.5 ? vec3(1.00, 0.42, 0.60)
              : palette < 2.5 ? vec3(0.35, 1.00, 0.68)
                              : vec3(0.45, 0.76, 1.00);
    vec3 dark = palette < 0.5 ? vec3(0.09, 0.07, 0.16)
              : palette < 1.5 ? vec3(0.14, 0.05, 0.12)
              : palette < 2.5 ? vec3(0.04, 0.12, 0.11)
                              : vec3(0.05, 0.08, 0.18);
    vec3 wash = mix(lit * 0.9, dark, fall);
    float level = 0.35 + 1.25 * (bands.x * 0.35 + bands.y * 0.45 + bands.z * 0.20);
    float strike = 0.85 + 0.5 * drop;
    vec3 metal = mix(lit, vec3(1.0), clamp(bands.z * 0.7, 0.0, 0.30));
    vec3 col = wash * (0.0025 + 0.004 * haze * level) + metal * core * 0.045 * level * strike;
    colour = vec4(col / (1.0 + col), 1.0);
}
"""

        const val TUNNEL = """
uniform vec2 size;
uniform float travelled;
uniform vec3 camera;
uniform vec3 room1;
uniform vec3 room2;
uniform vec3 beat;
uniform vec3 bands;
uniform float palette;
uniform float steps;

float slabHash(float n, float salt) { return fract(sin(n * 41.317 + salt * 7.913) * 24634.6345); }

float valueNoise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = 0.0;
    for (int i = 0; i < 8; i++) {
        vec3 corner = vec3(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1));
        float h = fract(sin(dot(cell + corner, vec3(127.1, 311.7, 74.7))) * 43758.5453);
        vec3 w = mix(1.0 - f, f, corner);
        n += h * w.x * w.y * w.z;
    }
    return n;
}

// Two octaves: a third would add detail below the size of a pixel.
float grain(vec3 p) { return 0.65 * valueNoise(p) + 0.35 * valueNoise(p * 2.07); }

// The tunnel, and how lit the nearest thing to p is: 0 for machinery, 1 for a lamp. Nothing closes the bore.
float tunnelRoom(vec3 p, float beats, out float lamp) {
    float bore = room1.y * 2.4;
    float pitch = room2.x * 2.6;
    float thick = room2.y * 4.0;
    float r = max(length(p.xy), 1e-4);
    float ang = atan(p.y, p.x);
    float tube = bore - r;
    float id = floor(p.z / pitch + 0.5);
    float qz = p.z - id * pitch;
    float rib = max(bore * 0.82 - r, abs(qz) - thick * (0.6 + slabHash(id, 2.0)));

    float around = 26.0;
    float sector = TAU / around;
    float run = pitch * 0.25;
    float column = floor((ang + 3.14159265) / sector);
    float row = floor(p.z / run + 0.5);
    float hash = slabHash(column * 7.0 + row * 13.0, 9.0);
    float tall = slabHash(column * 3.0 + row * 29.0, 17.0);
    float across = (mod(ang + 3.14159265 + 0.5 * sector, sector) - 0.5 * sector) * r;
    float along = p.z - row * run;
    float stand = bore * (0.045 + 0.16 * hash * tall);
    float face = bore - stand;
    vec3 slot = vec3(max(face - r, r - bore), abs(across) - sector * r * (0.30 + 0.14 * tall), abs(along) - run * (0.26 + 0.16 * hash));
    float block = max(slot.x, max(slot.y, slot.z));
    float lit = step(0.93, hash);

    float strip = max(abs(r - bore * 0.93) - thick * 0.30, abs(qz - pitch * 0.5) - thick * 0.55);
    float ringLit = step(0.42, slabHash(id, 3.0));

    vec3 q = p;
    q.z = qz;
    q.xy = q.xy * rot(slabHash(id, 1.0) * TAU + beats * 0.02 * (slabHash(id, 5.0) - 0.5));
    vec3 fin = abs(q - vec3(bore * 0.62, 0.0, 0.0)) - vec3(bore * 0.30, thick * 1.6, thick * 0.9);
    float blade = length(max(fin, 0.0)) + min(max(fin.x, max(fin.y, fin.z)), 0.0);

    float d = min(min(tube, rib), min(min(strip, blade), block));
    lamp = max(ringLit * (1.0 - smoothstep(0.0, thick * 0.5, strip - d)), lit * (1.0 - smoothstep(0.0, thick * 0.4, block - d)));
    return d;
}

float tunnelDistance(vec3 p, float beats) {
    float ignored;
    return tunnelRoom(p, beats, ignored);
}

vec3 tunnelNormal(vec3 p, float beats) {
    vec2 e = vec2(0.0025, -0.0025);
    return normalize(e.xyy * tunnelDistance(p + e.xyy, beats) + e.yyx * tunnelDistance(p + e.yyx, beats)
                   + e.yxy * tunnelDistance(p + e.yxy, beats) + e.xxx * tunnelDistance(p + e.xxx, beats));
}

// The bass lights what is near and the treble what is far, so a kick arrives and a hi-hat recedes.
vec3 lampColour(float depth) {
    vec3 cold = palette < 1.5 ? vec3(0.12, 0.85, 0.30) : palette < 2.5 ? vec3(0.20, 0.62, 1.00) : vec3(0.95, 0.35, 0.55);
    vec3 hot  = palette < 1.5 ? vec3(0.45, 1.00, 0.35) : palette < 2.5 ? vec3(0.50, 0.85, 1.00) : vec3(1.00, 0.50, 0.35);
    float close = exp(-depth * 0.06);
    float band = mix(bands.z, bands.x, close);
    return mix(cold, hot, clamp(band * 1.6, 0.0, 1.0)) * (0.25 + 2.6 * band);
}

float tunnelTravel(vec3 ro, vec3 rd, int allowed, float from, float beats) {
    float t = from;
    for (int i = 0; i < 96; i++) {
        if (i >= allowed) break;
        float d = tunnelDistance(ro + rd * t, beats);
        if (d < 0.0025) return t;
        if (t > 90.0) break;
        t += d * 0.85;
    }
    return -1.0;
}

void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = (frag - 0.5 * size) / size.y;
    float beats = beat.x, drop = beat.z;
    int allowed = int(steps);

    float smear = fract(sin(dot(frag.yx, vec2(31.71, 19.37)) + beats) * 17631.417);
    vec3 ro = vec3(sin(beats * 0.13) * camera.z, cos(beats * 0.09) * camera.z, travelled + smear * 0.35);
    vec3 rd = normalize(vec3(uv, camera.y));
    rd.xy = rd.xy * rot(camera.x * 0.4 + sin(beats * 0.07) * camera.z * 0.25);

    float dither = fract(sin(dot(frag, vec2(12.9898, 78.233)) + beats) * 43758.5453);
    vec3 col = vec3(0.0);
    float t = tunnelTravel(ro, rd, allowed, 0.02 + dither * 0.12, beats);

    if (t > 0.0) {
        vec3 p = ro + rd * t;
        float lamp;
        tunnelRoom(p, beats, lamp);
        vec3 n = tunnelNormal(p, beats);

        // The metal, generated where the ray hit it; detail past 30 units is below a pixel and is skipped.
        float detail = 1.0 - smoothstep(14.0, 30.0, t);
        vec3 skin = p * 2.7;
        float alloyCast = detail > 0.02 ? grain(skin) : 0.5;
        float dirt = grain(vec3(p.xy * 0.9, p.z * 0.16));
        float seam = max(abs(fract(p.z * 3.1) - 0.5), abs(fract(atan(p.y, p.x) * 5.7) - 0.5));
        float bolt = length(fract(vec2(p.z * 3.1, atan(p.y, p.x) * 5.7)) - 0.5);

        if (detail > 0.25) {
            float e = 0.02;
            vec3 bump = vec3(grain(skin + vec3(e, 0.0, 0.0)) - alloyCast,
                             grain(skin + vec3(0.0, e, 0.0)) - alloyCast,
                             grain(skin + vec3(0.0, 0.0, e)) - alloyCast) / e;
            n = normalize(n - (bump - n * dot(bump, n)) * 0.06 * detail);
        }

        float sky = 0.5 + 0.5 * dot(n, normalize(vec3(0.2, 0.7, -0.6)));
        vec3 alloy = mix(vec3(0.020, 0.026, 0.022), vec3(0.048, 0.052, 0.040), alloyCast);
        alloy *= 0.55 + 0.75 * mix(0.5, dirt, detail);
        alloy *= 1.0 - 0.55 * smoothstep(0.40, 0.49, seam);
        alloy *= 1.0 - 0.35 * smoothstep(0.10, 0.05, bolt);

        vec3 wall = alloy * (0.3 + 1.1 * sky);
        vec3 light = lampColour(t) * lamp;

        // One bounce, only from a wall the camera is near: a lamp reflected in the wall.
        vec3 rr = reflect(rd, n);
        float t2 = t < 26.0 ? tunnelTravel(p + n * 0.02, rr, allowed / 3, 0.02, beats) : -1.0;
        if (t2 > 0.0) {
            vec3 p2 = p + n * 0.02 + rr * t2;
            float lamp2;
            tunnelRoom(p2, beats, lamp2);
            light += lampColour(t + t2) * lamp2 * 0.85 * exp(-t2 * 0.08);
        }

        col = wall + light;
        col *= 0.9 + 0.5 * drop;
        col *= exp(-t * 0.035);
    }

    colour = vec4(col / (1.0 + col), 1.0);
}
"""
    }
}
