package com.rpm.player.core.room

/**
 * The room's GLSL, for OpenGL ES 3.00.
 *
 * [ROOM] is `RoomSource.Glsl` from RPM.Core/Viz/RoomSource.cs, the text the desktop compiles and the
 * phone translates, with its long comments cut to short pointers and one change of place. Where each
 * lobe is and how large it is depends only on the time and the three levels, not on the pixel, so it
 * is worked out once per frame on the CPU by [RoomOrbits] and handed in as `uLobes1..3` (x, y, radius).
 * The formulas are RoomSource's, moved, not changed. On the Amiko's Mali-G31 the per-pixel version
 * spent about fifty sines and cosines per pixel on numbers that were the same for every pixel.
 *
 * Change the room in RoomSource first, then copy the change here and into [RoomOrbits], so the three
 * apps keep drawing one picture. The constants are RoomSource's: six, five and four lobes,
 * AIR_RATE 1.3, AIR_SOFT 1.08.
 */
internal object RoomShaders {

    /** A triangle that covers the screen, from the vertex id alone, so no vertex buffer is needed. */
    const val VERTEX = """#version 300 es
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""

    private const val ROOM = """
const int RPM_LOBES = 6;
const int RPM_LOBES2 = 5;
const int RPM_LOBES3 = 4;
const float AIR_RATE = 1.3;
const float AIR_SOFT = 1.08;
const float AIR_PEAK = 1.0 / AIR_SOFT;

// Each lobe's centre (x, y) and radius (z) this frame, from RoomOrbits.
uniform vec3 uLobes1[RPM_LOBES];
uniform vec3 uLobes2[RPM_LOBES2];
uniform vec3 uLobes3[RPM_LOBES3];

// A lobe with no edge: it never reaches zero, which is what lets the lobes melt into one another.
float rpmLobe(vec2 p, vec2 c, float r, float soft)
{
    vec2 d = p - c;
    float r2 = r * r;
    return r2 / (dot(d, d) + r2 * soft);
}

vec3 rpmRoom(vec2 p, float t, float bass, float mid, float air,
             vec3 ground, vec3 tint, vec3 glow, vec3 shade)
{
    // The third family, drawn first, behind the other two, in the sleeve's third colour alone.
    float t3 = t * AIR_RATE;

    vec2 w3 = p;
    w3 += 0.11 * vec2(sin(p.y * 3.2 + t3 * 0.29), cos(p.x * 2.9 - t3 * 0.25));
    w3 += (0.05 + air * 0.11) * vec2(sin(w3.y * 5.1 - t3 * 0.21), cos(w3.x * 4.6 + t3 * 0.23));

    float field3 = 0.0;
    for (int i = 0; i < RPM_LOBES3; i++)
    {
        field3 += rpmLobe(w3, uLobes3[i].xy, uLobes3[i].z, AIR_SOFT);
    }

    vec3 back = mix(ground, shade, smoothstep(0.17 * AIR_PEAK, 1.22 * AIR_PEAK, field3) * 0.70);
    back += shade * smoothstep(0.65 * AIR_PEAK, 1.73 * AIR_PEAK, field3) * (0.16 + air * 0.62);

    // The figures: the plane warped twice, the second warp pushed by the bass.
    vec2 w = p;
    w += 0.20 * vec2(sin(p.y * 2.1 + t * 0.19), cos(p.x * 1.8 - t * 0.15));
    w += (0.10 + bass * 0.14) * vec2(sin(w.y * 3.9 - t * 0.11), cos(w.x * 3.3 + t * 0.13));

    float field = 0.0;
    float weight = 0.0;
    vec3 wash = vec3(0.0);
    for (int i = 0; i < RPM_LOBES; i++)
    {
        float fi = float(i);
        float g = rpmLobe(w, uLobes1[i].xy, uLobes1[i].z, 0.42);
        field += g;
        // Weighted by the square, so the nearest lobe owns its neighbourhood and colours stay apart.
        float wg = g * g;
        weight += wg;
        wash += mix(tint, glow, fract(fi * 0.41 + 0.15)) * wg;
    }
    wash /= max(weight, 1e-4);

    vec3 col = mix(back, wash, smoothstep(0.30, 1.75, field));
    col += wash * smoothstep(1.60, 3.40, field) * (0.12 + bass * 0.44);

    // The second family: smaller, quicker, in the counter-colour, moved by the middle of the music.
    vec2 w2 = p;
    w2 += 0.15 * vec2(sin(p.y * 2.6 - t * 0.27), cos(p.x * 2.3 + t * 0.23));
    w2 += (0.07 + mid * 0.13) * vec2(sin(w2.y * 4.4 + t * 0.19), cos(w2.x * 3.9 - t * 0.21));

    float field2 = 0.0;
    float weight2 = 0.0;
    vec3 wash2 = vec3(0.0);
    for (int i = 0; i < RPM_LOBES2; i++)
    {
        float fi = float(i);
        float g = rpmLobe(w2, uLobes2[i].xy, uLobes2[i].z, 0.88);
        float wg = g * sqrt(g);
        field2 += g;
        weight2 += wg;
        wash2 += mix(shade, glow, fract(fi * 0.53 + 0.40)) * wg;
    }
    wash2 /= max(weight2, 1e-4);

    col = mix(col, wash2, smoothstep(0.20, 1.90, field2) * 0.78);
    col += wash2 * smoothstep(1.30, 3.50, field2) * (0.14 + bass * 0.40);

    return col;
}
"""

    /** The room at the offscreen size. `p` is the plane centred and scaled by the height, as in the
     * desktop's PegasusStage. The whole room is dimmed to 0.62, and the left of it more while the
     * player's spread view draws there (`uBare` 0), lifting as `uBare` eases to 1. The vignette is the
     * desktop's too, drawn here at the small size because it is as smooth as the room. */
    val ROOM_FRAGMENT = """#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform float uTime;
uniform float uBass;
uniform float uMid;
uniform float uAir;
uniform vec3 uGround;
uniform vec3 uTint;
uniform vec3 uGlow;
uniform vec3 uShade;
uniform float uBare;
out vec4 fragColor;
$ROOM
void main() {
    vec2 p = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;
    vec3 col = rpmRoom(p, uTime, uBass, uMid, uAir, uGround, uTint, uGlow, uShade);
    col *= 0.62;
    vec2 uv = gl_FragCoord.xy / uResolution;
    col *= mix(0.40 + 0.60 * smoothstep(0.02, 0.44, uv.x), 1.0, uBare);
    float vig = pow(16.0 * uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y), 0.20);
    col *= 0.42 + 0.58 * vig;
    fragColor = vec4(max(col, vec3(0.0)), 1.0);
}
"""

    /**
     * The TV's other rooms ([RoomStyle]), each a `styleRoom(p)` in place of `rpmRoom`, with the same
     * uniforms, the same plane, the same dimming to 0.62, the same left-hand dimming under the spread
     * view and the same vignette as [ROOM_FRAGMENT], so the present pass, the spectrum and the stills
     * take them as they take the original. Their figures come from [RoomFigures] as vec4 arrays.
     *
     * The cost class is the original's: one pass at the eighth size, no texture reads, no second
     * buffer. The original spends twelve sines and cosines on its warps and fifteen lobes per pixel;
     * Yang spends no trigonometry and twenty-one lobes, Lava one sine and ten lobes, RPaMp nine sines,
     * nine exponentials and seven lobes.
     */
    private fun styleFragment(body: String) = """#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform float uTime;
uniform float uBass;
uniform float uMid;
uniform float uAir;
uniform vec3 uGround;
uniform vec3 uTint;
uniform vec3 uGlow;
uniform vec3 uShade;
uniform float uBare;
out vec4 fragColor;

// A lobe with no edge, as the original's: it never reaches zero, so lobes melt into one another.
float rpmLobe(vec2 p, vec2 c, float r, float soft)
{
    vec2 d = p - c;
    float r2 = r * r;
    return r2 / (dot(d, d) + r2 * soft);
}
$body
void main() {
    vec2 p = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;
    vec3 col = styleRoom(p);
    col *= 0.62;
    vec2 uv = gl_FragCoord.xy / uResolution;
    col *= mix(0.40 + 0.60 * smoothstep(0.02, 0.44, uv.x), 1.0, uBare);
    float vig = pow(16.0 * uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y), 0.20);
    col *= 0.42 + 0.58 * vig;
    fragColor = vec4(max(col, vec3(0.0)), 1.0);
}
"""

    /** Yang ([RoomFigures.yang]). The middle ball is the original's third family (its soft 1.08, its
     * ramp, lit by the middle where that is lit by the air); each pair is a family of its own, the big in
     * the tint and lit by the bass, the small in the glow and lit by the air, at the original's
     * thresholds. An echo's weight scales its share of the field, so the tail fades as it thins. */
    val YANG_FRAGMENT = styleFragment(
        """
const int YANG_LOBES = ${RoomFigures.YANG_LOBES};
uniform vec4 uBig[YANG_LOBES];
uniform vec4 uSmall[YANG_LOBES];
uniform vec4 uCentre;

vec3 styleRoom(vec2 p)
{
    float c = rpmLobe(p, uCentre.xy, uCentre.z, 1.08);
    vec3 col = mix(uGround, uShade, smoothstep(0.16, 1.13, c) * 0.70);
    col += uShade * smoothstep(0.60, 1.60, c) * (0.16 + uMid * 0.62);

    float big = 0.0;
    float small = 0.0;
    for (int i = 0; i < YANG_LOBES; i++)
    {
        big += rpmLobe(p, uBig[i].xy, uBig[i].z, 0.42) * uBig[i].w;
        small += rpmLobe(p, uSmall[i].xy, uSmall[i].z, 0.60) * uSmall[i].w;
    }
    col = mix(col, uTint, smoothstep(0.30, 1.75, big));
    col += uTint * smoothstep(1.60, 3.40, big) * (0.12 + uBass * 0.44);
    col = mix(col, uGlow, smoothstep(0.25, 1.60, small) * 0.85);
    col += uGlow * smoothstep(1.20, 3.00, small) * (0.14 + uAir * 0.40);
    return col;
}
""",
    )

    /** Lava ([RoomFigures.lava]). The background ball is drawn as Yang's middle one, lit by the middle.
     * The pools are lobes in one direction only, a band across the screen with a slow ripple, summed into
     * the blobs' field so a blob that reaches one melts into it. The blobs wear the original's first
     * family's colours, weighted by the square as it weights them, and their edge is narrower than the
     * original's (0.70 to 1.30 of the field, not 0.30 to 1.75), so they read as wax and not as haze. */
    val LAVA_FRAGMENT = styleFragment(
        """
const int LAVA_BLOBS = ${RoomFigures.LAVA_BLOBS};
uniform vec4 uBlobs[LAVA_BLOBS];
uniform vec4 uPools;
uniform vec4 uBack;

vec3 styleRoom(vec2 p)
{
    float b = rpmLobe(p, uBack.xy, uBack.z, 1.08);
    vec3 col = mix(uGround, uShade, smoothstep(0.16, 1.13, b) * 0.70);
    col += uShade * smoothstep(0.60, 1.60, b) * (0.16 + uMid * 0.62);

    float wave = sin(p.x * 3.1 + uBack.w);
    float db = p.y - (uPools.x + 0.025 * wave);
    float dt = p.y - (uPools.z - 0.020 * wave);
    float rb = uPools.y * uPools.y;
    float rt = uPools.w * uPools.w;
    float bottom = rb / (db * db + rb * 0.42);
    float top = rt / (dt * dt + rt * 0.42);
    float field = bottom + top;
    float weight = bottom * bottom + top * top;
    vec3 wash = uTint * (bottom * bottom) + uGlow * (top * top);
    for (int i = 0; i < LAVA_BLOBS; i++)
    {
        float g = rpmLobe(p, uBlobs[i].xy, uBlobs[i].z, 0.42);
        field += g;
        float wg = g * g;
        weight += wg;
        wash += mix(uTint, uGlow, uBlobs[i].w) * wg;
    }
    wash /= max(weight, 1e-4);
    col = mix(col, wash, smoothstep(0.70, 1.30, field));
    col += wash * smoothstep(1.60, 3.40, field) * (0.12 + uBass * 0.44);
    col += uGlow * smoothstep(2.40, 4.00, field) * uAir * 0.25;
    return col;
}
""",
    )

    /** RPaMp ([RoomFigures.strands]). Each column is a Gaussian across its swaying centre line, wider
     * towards the floor and fading out above its top, with a round head; the floor is an exponential
     * glow. As make_rpamp_ground.py does with its ribbons, they are summed and put through a soft knee
     * (1 - exp(-1.05 x field)), so two columns that cross make one brighter sheet and never white. The
     * colour is the columns' own, weighted by their share of the light, over the ground darkened
     * towards the top. */
    val RPAMP_FRAGMENT = styleFragment(
        """
const int STRANDS = ${RoomFigures.STRANDS};
uniform vec4 uStrandA[STRANDS];
uniform vec4 uStrandB[STRANDS];
uniform vec2 uHeads[STRANDS];
uniform vec4 uFloor;

vec3 strandColour(float m)
{
    return mix(mix(uTint, uGlow, clamp(m, 0.0, 1.0)), uShade, clamp(m - 1.0, 0.0, 1.0));
}

vec3 styleRoom(vec2 p)
{
    // 0 at the top of the screen, 1 at the floor.
    float down = 0.5 - p.y;
    float fl = exp(-(p.y + 0.5) / uFloor.x) * uFloor.y * (1.0 + 0.22 * sin(p.x * 4.3 + uFloor.z));
    float field = fl;
    vec3 wash = strandColour(0.5 + 0.5 * sin(p.x * 2.2 - uFloor.z * 0.5)) * fl;
    for (int i = 0; i < STRANDS; i++)
    {
        vec4 a = uStrandA[i];
        vec4 b = uStrandB[i];
        float x = a.x + b.x * sin(p.y * 7.0 - b.y);
        float d = (p.x - x) / (a.z * (0.7 + 0.6 * down));
        float body = exp(-d * d) * smoothstep(a.y + 0.06, a.y - 0.22, p.y);
        float head = rpmLobe(p, uHeads[i], a.z * 1.26, 1.0);
        float g = (body + 0.9 * head * head) * a.w;
        field += g;
        wash += strandColour(b.z) * g;
    }
    wash /= max(field, 1e-4);
    float lit = 1.0 - exp(-1.05 * field);
    vec3 dark = uGround * (0.35 + 0.65 * smoothstep(0.55, -0.45, p.y));
    vec3 col = mix(dark, wash, lit);
    col += wash * smoothstep(1.40, 3.00, field) * (0.10 + uBass * 0.35);
    return col;
}
""",
    )

    /** The offscreen room scaled to the screen, then the spectrum, then the grain, all at the screen's
     * own resolution: 128 bars across a third of the screen are a few pixels each, which the small room
     * would blur together, and grain drawn small and scaled up would be blur, not dither.
     *
     * The spectrum is PegasusStage's (PegasusStage.cs, "the spectrum"), with the Windows figures: it
     * starts just right of the sleeve (`SPEC_INSET`), runs to the edge, is mirrored about the middle
     * line, stands at most `BAR_REACH` of the height on either side, and fades out as `uBare` rises. The
     * room texture has been vignetted already, so the bars are vignetted here before they are mixed in.
     * While the queue drawer is open, the whole picture is drawn `uPush` of the width to the right, as
     * the desktop pushes it. The grain has the desktop's strength (1.6 / 255), from a tiled 64 x 64
     * noise texture. */
    val PRESENT_FRAGMENT = """#version 300 es
precision highp float;
uniform sampler2D uRoom;
uniform sampler2D uNoise;
uniform sampler2D uBands;
uniform vec2 uScreen;
uniform float uBare;
uniform float uPush;
uniform float uDim;
uniform float uBright;
uniform vec3 uTint;
uniform vec3 uGlow;
out vec4 fragColor;
const float COLS = ${RoomLevels.BANDS}.0;
const float SPEC_INSET = 0.04;
const float BAR_REACH = 0.24;
const float COVER_FRACTION = 0.38;
const float COVER_MAX_W = 0.34;
void main() {
    vec2 screen = gl_FragCoord.xy / uScreen;
    // The picture pushed right by the queue drawer: what is drawn here is what was uPush to the left.
    vec2 uv = vec2(screen.x - uPush, screen.y);
    // The room's own brightness (RoomTarget.brightness), before the spectrum is laid on it.
    vec3 col = texture(uRoom, uv).rgb * uBright;
    float side = min(uScreen.y * COVER_FRACTION, uScreen.x * COVER_MAX_W);
    float start = 0.5 + 0.5 * side / uScreen.x * (1.0 + SPEC_INSET);
    float sx = (uv.x - start) / max(1e-3, 1.0 - start);
    if (sx > 0.0 && uBare < 0.999) {
        float bar = sx * COLS;
        float inBar = fract(bar);
        float v = texture(uBands, vec2((floor(bar) + 0.5) / COLS, 0.5)).x;
        float halfH = 0.004 + v * BAR_REACH;
        float d = abs(uv.y - 0.5);
        float px = 1.2 / uScreen.y;
        float body = smoothstep(halfH + px, halfH - px, d);
        float slot = smoothstep(0.0, 0.30, inBar) * (1.0 - smoothstep(0.70, 1.0, inBar));
        float emerge = smoothstep(0.0, 0.11, sx);
        float ink = body * slot * emerge * (0.24 + v * 0.76) * (1.0 - uBare);
        vec3 tone = mix(mix(uTint, uGlow, sx), vec3(1.0), 0.62 + v * 0.38);
        float vig = 0.42 + 0.58 * pow(16.0 * max(uv.x, 0.0) * uv.y * (1.0 - uv.x) * (1.0 - uv.y), 0.20);
        col = mix(col, tone * vig, clamp(ink, 0.0, 1.0));
    }
    // Dimmed before the grain, so a dimmed room is dithered at its own level.
    col *= uDim;
    float grain = texture(uNoise, gl_FragCoord.xy / 64.0).r - 0.5;
    col += grain * (1.6 / 255.0);
    fragColor = vec4(max(col, vec3(0.0)), 1.0);
}
"""
}
