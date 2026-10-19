package com.rpm.player.core.art

import kotlin.math.roundToInt

/**
 * The rules for cutting a photograph to a frame, without the pixels. Matches the iPhone's `ArtFrame`
 * (RPMArt/ArtFrame.swift). [topWindow] gives the cut for a picture filled from the top. [coversFace] says whether
 * that cut hides the face (outside the window, or under the status bar). [toSource] maps a window measured on the
 * small copy used to find faces back to the picture's own pixels.
 */
object ArtFrame {
    /** Where the eyes are down a face box, as a share of its height. */
    private const val EYES_DOWN = 0.4

    /** The window with the frame's shape that a picture is filled from. A picture wider than the frame keeps its
     * whole height and loses the same amount from each side. A taller one keeps its whole width and loses the
     * bottom. */
    fun topWindow(width: Int, height: Int, aspect: Double): CropRect {
        val w = maxOf(1, width)
        val h = maxOf(1, height)
        if (aspect <= 0) return CropRect(0, 0, w, h)
        if (w.toDouble() / h > aspect) {
            val across = (h * aspect).roundToInt().coerceIn(1, w)
            return CropRect((w - across) / 2, 0, across, h)
        }
        val down = (w / aspect).roundToInt().coerceIn(1, h)
        return CropRect(0, 0, w, down)
    }

    /**
     * Whether a picture filled from the top for [scene] puts its main face (the largest) where it cannot be seen:
     * outside the window, or with its eyes under the status bar. A picture with no face covers nothing.
     */
    fun coversFace(faces: List<FaceBox>, width: Int, height: Int, scene: ArtScene): Boolean {
        if (!scene.isLaidOut) return false
        val face = faces.maxByOrNull { it.width * it.height } ?: return false
        val window = topWindow(width, height, scene.aspect)
        val across = (face.x + face.width / 2.0 - window.x) / maxOf(1, window.width)
        val down = (face.y + face.height * EYES_DOWN - window.y) / maxOf(1, window.height)
        if (across !in 0.0..1.0 || down !in 0.0..1.0) return true
        return down < scene.clearTop
    }

    /** Converts [crop], measured on a copy [workWidth] wide, to pixels of the [sourceWidth] by [sourceHeight] picture. */
    fun toSource(crop: CropRect, workWidth: Int, sourceWidth: Int, sourceHeight: Int): CropRect {
        val scale = sourceWidth.toDouble() / maxOf(1, workWidth)
        val x = (crop.x * scale).roundToInt().coerceIn(0, maxOf(0, sourceWidth - 1))
        val y = (crop.y * scale).roundToInt().coerceIn(0, maxOf(0, sourceHeight - 1))
        val w = (crop.width * scale).roundToInt().coerceIn(1, sourceWidth - x)
        val h = (crop.height * scale).roundToInt().coerceIn(1, sourceHeight - y)
        return CropRect(x, y, w, h)
    }
}
