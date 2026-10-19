package com.rpm.player.core.art

import kotlin.math.roundToInt

/** A window into a picture, in its pixels, from the top left. */
data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** The window chosen, and whether it has the frame's shape (close enough) or is narrower (a pillarbox). */
data class CropChoice(val rect: CropRect, val fillsTarget: Boolean)

/** A face found in a picture, in its pixels, from the top left. */
data class FaceBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    companion object {
        /** Converts a box from a face detector that gives fractions of the picture, counted from the bottom left. */
        fun fromNormalizedBottomLeft(x: Double, y: Double, width: Double, height: Double, pixelWidth: Int, pixelHeight: Int) = FaceBox(
            (x * pixelWidth).roundToInt(),
            ((1 - y - height) * pixelHeight).roundToInt(),
            (width * pixelWidth).roundToInt(),
            (height * pixelHeight).roundToInt(),
        )
    }
}

/**
 * Where to cut a photograph for a frame. It follows the iPhone's `SaliencyCrop` (RPMArt/SaliencyCrop.swift) rule
 * for rule. The rules, in order:
 * 1. Faces: each face is grown to a head and shoulders. The crop is the smallest window with the frame's shape that
 *    shows everybody, with the faces placed where the frame is clear and the eyes high.
 * 2. No face: the subject that the saliency map shows, framed on its height.
 * 3. Neither: the widest window, centred on the attention that the map has, with no zoom.
 *
 * The map is one number per pixel of the picture as measured ([width] by [height]), row by row.
 */
object SaliencyCrop {
    const val MIN_SCALE = 0.45
    private const val SUBJECT_THRESHOLD = 0.5
    private const val MIN_SUBJECT_FRACTION = 0.005
    private const val MAX_SUBJECT_FRACTION = 0.85
    private const val PADDING = 0.15
    private const val SUBJECT_PILLARBOX = 1.6
    private const val FACE_DROP = 0.02
    private const val HORIZONTAL_ROOM = 0.85

    // A head has a known shape around a face box, so the frame around it can be calculated.
    private const val HAIR_ABOVE = 0.45
    private const val SHOULDERS_BELOW = 0.8
    private const val BESIDE_FACE = 0.6
    private const val MIN_PILLAR_ASPECT = 2.8
    private const val FILL_TOLERANCE = 0.12
    private const val EYES_IN_FACE = 0.42
    private const val EYES_IN_BAND = 0.38

    fun choose(saliency: DoubleArray, width: Int, height: Int, targetAspect: Double, faces: List<FaceBox>, scene: ArtScene): CropChoice {
        if (width <= 0 || height <= 0 || targetAspect <= 0 || saliency.size < width * height) {
            return CropChoice(CropRect(0, 0, maxOf(width, 0), maxOf(height, 0)), fillsTarget = true)
        }
        val focusAcross = scene.focusAcross
        // 0.5 is the exact centre. A frame with text along the bottom asks for less, which moves the subject up.
        val downShift = 0.5 - scene.focusDown
        // The largest window with the frame's shape that fits inside the photograph: the frame with no zoom.
        val (maxW, maxH) = fitInside(width, height, targetAspect)

        val subject = findSubject(saliency, width, height) ?: run {
            // Nothing to frame: use the widest window, centred on the attention there is, with no zoom.
            val (cx, cy) = centroid(saliency, width, height)
            return CropChoice(place(cx, cy, maxW, maxH, width, height), fillsTarget = true)
        }
        // The height that a picture with the frame's own shape gets: the height that is available.
        val band = (maxW / targetAspect).coerceIn(maxH * MIN_SCALE, maxH.toDouble()).roundToInt()

        if (faces.isNotEmpty()) {
            val faceTop = faces.minOf { it.y - it.height * HAIR_ABOVE }
            val faceBottom = faces.maxOf { it.y + it.height * (1 + SHOULDERS_BELOW) }
            val faceLeft = faces.minOf { it.x - it.width * BESIDE_FACE }
            val faceRight = faces.maxOf { it.x + it.width * (1 + BESIDE_FACE) }
            val contentH = maxOf(1.0, faceBottom - faceTop)
            val contentW = maxOf(1.0, faceRight - faceLeft)
            // The frame's shape comes from growing whichever side is short, never from cropping the other.
            var frameH = maxOf(contentH, contentW / targetAspect)
            var frameW = frameH * targetAspect
            // Fit the frame back inside the photograph. If the width runs out first, the frame keeps the height that
            // the content needs, down to the pillarbox limit. This is the pillarbox case.
            if (frameW > width) {
                frameW = width.toDouble()
                frameH = minOf(minOf(height.toDouble(), contentH), frameW / MIN_PILLAR_ASPECT)
            }
            if (frameH > height) {
                frameH = height.toDouble()
                frameW = minOf(width.toDouble(), frameH * targetAspect)
            }
            val fills = frameW >= frameH * targetAspect * (1 - FILL_TOLERANCE)
            // Across: put the faces where the frame is clear. Only a frame with the right shape has room to slide.
            val faceX = (faceLeft + faceRight) / 2
            val acrossAt = if (fills) faceX + (0.5 - focusAcross) * frameW else faceX
            // Down: keep the eyes in view. The hair and chin can be cut, the eyes cannot.
            val eyesAt = faces.sumOf { it.y + it.height * EYES_IN_FACE } / faces.size
            val downAt = (if (frameH >= contentH) (faceTop + faceBottom) / 2 else eyesAt - frameH * EYES_IN_BAND + frameH / 2) +
                ride(downShift * frameH, frameH, contentH)
            return CropChoice(place(acrossAt, downAt, frameW.roundToInt(), frameH.roundToInt(), width, height), fills)
        }

        // No face, but a subject much taller than the band: use a pillarbox, not a band across a very large head.
        if (subject.height > band * SUBJECT_PILLARBOX) {
            val tallH = minOf(height.toDouble(), maxW / MIN_PILLAR_ASPECT)
            val tallW = minOf(width.toDouble(), tallH * targetAspect)
            val acrossSubject = subject.x + subject.width / 2.0
            val downSubject = subject.y + tallH / 2 + tallH * FACE_DROP + ride(downShift * tallH, tallH, subject.height.toDouble())
            return CropChoice(
                place(acrossSubject, downSubject, tallW.roundToInt(), tallH.roundToInt(), width, height),
                fillsTarget = tallW >= tallH * targetAspect * (1 - FILL_TOLERANCE),
            )
        }

        // Frame on the subject's height, and take the width from the frame's shape. Framed on its width, a standing
        // figure would show only a slice of face.
        var frameH = subject.height * (1 + 2 * PADDING)
        var frameW = frameH * targetAspect
        if (frameW > width) {
            frameW = width.toDouble()
            frameH = frameW / targetAspect
        }
        if (frameH > height) {
            frameH = height.toDouble()
            frameW = frameH * targetAspect
        }
        // Leave some room across, so the subject can be placed and not only included.
        frameW = minOf(frameW, maxW * HORIZONTAL_ROOM)
        frameH = frameW / targetAspect
        val w = frameW.coerceIn(maxW * MIN_SCALE, maxW.toDouble()).roundToInt()
        val h = frameH.coerceIn(maxH * MIN_SCALE, maxH.toDouble()).roundToInt()
        val focusX = subject.x + subject.width / 2.0
        val subjectX = focusX + (0.5 - focusAcross) * w
        val subjectY = bandCentre(subject, h) + ride(downShift * h, h.toDouble(), subject.height.toDouble())
        return CropChoice(place(subjectX, subjectY, w, h, width, height), fillsTarget = true)
    }

    /**
     * A map for a picture whose attention was not measured (this device has no saliency model). The faces' heads are
     * bright and the rest is dim, so the faces decide the crop. A flat map would not work: it has no subject, and
     * [choose] only looks at faces when it has a subject.
     */
    fun facesOnly(faces: List<FaceBox>, width: Int, height: Int): DoubleArray {
        val map = DoubleArray(maxOf(0, width * height)) { 0.05 }
        for (face in faces) {
            val top = (face.y - face.height * HAIR_ABOVE).toInt().coerceIn(0, height)
            val bottom = (face.y + face.height * (1 + SHOULDERS_BELOW)).toInt().coerceIn(0, height)
            val left = face.x.coerceIn(0, width)
            val right = (face.x + face.width).coerceIn(0, width)
            for (y in top until bottom) for (x in left until right) map[y * width + x] = 1.0
        }
        return map
    }

    private fun ride(wanted: Double, frameHeight: Double, contentHeight: Double): Double {
        val slack = maxOf(0.0, (frameHeight - contentHeight) / 2)
        return wanted.coerceIn(-slack, slack)
    }

    private fun bandCentre(subject: CropRect, band: Int): Double =
        if (subject.height <= band) subject.y + subject.height / 2.0 else subject.y + band / 2.0 + band * FACE_DROP

    private fun place(centreX: Double, centreY: Double, w: Int, h: Int, width: Int, height: Int): CropRect {
        val cw = w.coerceIn(1, width)
        val ch = h.coerceIn(1, height)
        val x = (centreX - cw / 2.0).coerceIn(0.0, (width - cw).toDouble()).roundToInt()
        val y = (centreY - ch / 2.0).coerceIn(0.0, (height - ch).toDouble()).roundToInt()
        return CropRect(x, y, cw, ch)
    }

    private fun findSubject(map: DoubleArray, width: Int, height: Int): CropRect? {
        var peak = 0.0
        for (i in 0 until width * height) if (map[i] > peak) peak = map[i]
        if (peak <= 0) return null
        val threshold = peak * SUBJECT_THRESHOLD
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var count = 0
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (map[row + x] < threshold) continue
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX || maxY < minY) return null
        val area = count.toDouble() / (width * height)
        // A tiny spot, or the whole picture: either way there is nothing to frame.
        if (area < MIN_SUBJECT_FRACTION || area > MAX_SUBJECT_FRACTION) return null
        return CropRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun centroid(map: DoubleArray, width: Int, height: Int): Pair<Double, Double> {
        var total = 0.0
        var sumX = 0.0
        var sumY = 0.0
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val v = map[row + x]
                if (v <= 0) continue
                total += v
                sumX += v * x
                sumY += v * y
            }
        }
        return if (total <= 0) width / 2.0 to height / 2.0 else sumX / total to sumY / total
    }

    private fun fitInside(width: Int, height: Int, aspect: Double): Pair<Int, Int> {
        var w = width
        var h = (width / aspect).roundToInt()
        if (h > height) {
            h = height
            w = (height * aspect).roundToInt()
        }
        return w.coerceIn(1, width) to h.coerceIn(1, height)
    }
}
