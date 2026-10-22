package com.rpm.player.phone.stage

import android.opengl.GLES30

/**
 * The spectrum, mirrored into four: the iPhone's `ButterflyView` and its `rpmButterfly` shader. Bass is in the middle
 * and treble runs out to the edges. Each band's level draws a dark line with light opening away from it, so loud
 * bands glow wide and quiet ones close to a seam.
 *
 * This look has no camera, no room and no beat, so it has no finishing pass; the desktop draws it the same way. The
 * spectrum row goes to the GPU as a 512-texel byte texture, read with `texelFetch` and blended between two bins in the
 * shader, as the iPhone's shader blends its float array.
 */
class ButterflyLook : StageRenderer {
    /** A third: the picture is soft gradients, and the stretch to the screen adds nothing a viewer can see. */
    override val scale = 0.34f

    private val bands = MarchBands()
    private val bytes = bytes(ShaderAudio.WIDTH)
    private var drawing = 0
    private var row = 0
    private var empty = 0
    private var sizeAt = 0

    override fun created() {
        drawing = program(FULL_FRAME, SHADER)
        row = byteTexture(ShaderAudio.WIDTH, 1)
        empty = emptyVertexArray()
        sizeAt = GLES30.glGetUniformLocation(drawing, "size")
    }

    override fun hear(frame: FloatArray, count: Int, now: Double, since: Double) {
        bands.listen(frame, count)
    }

    override fun draw(width: Int, height: Int, now: Double, since: Double) {
        uploadBytes(row, ShaderAudio.WIDTH, 1, bands.spectrum, bytes)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(drawing)
        GLES30.glUniform2f(sizeAt, width.toFloat(), height.toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, row)
        drawFullFrame(empty)
    }

    private companion object {
        const val SHADER = """#version 300 es
precision highp float;
uniform vec2 size;
uniform sampler2D spectrum;
out vec4 colour;

// The ramp is written in gamma-encoded light, so it is encoded before it is returned.
vec3 butterflyTint(float y) {
    vec3 middle = vec3(170.0, 170.0, 210.0) / 255.0;
    vec3 edge   = vec3( 51.0, 153.0, 255.0) / 255.0;
    return pow(mix(middle, edge, y), vec3(1.0 / 2.2));
}

void main() {
    // Folded twice: 0 at the centre of the frame and 1 at either edge, on both axes.
    vec2 uv = abs((gl_FragCoord.xy / size - 0.5) * 2.0);
    // Squared, so the width is spent on the bottom of the spectrum, where the music is.
    float band = uv.x * uv.x;
    float at = clamp(band, 0.0, 1.0) * 511.0;
    int lo = int(at);
    int hi = min(lo + 1, 511);
    float level = mix(texelFetch(spectrum, ivec2(lo, 0), 0).r, texelFetch(spectrum, ivec2(hi, 0), 0).r, at - float(lo));
    colour = vec4(abs(uv.y - level) * level * butterflyTint(uv.y), 1.0);
}
"""
    }
}
