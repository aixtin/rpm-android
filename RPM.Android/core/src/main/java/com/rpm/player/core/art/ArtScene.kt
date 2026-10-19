package com.rpm.player.core.art

/**
 * The frame a photograph is cut for, and where in it the subject should stand. Matches the iPhone's `ArtScene`
 * (RPMArt/ArtScene.swift). It holds the frame's size in points, the band down its left that nothing covers (the
 * record and the text are on the right and along the bottom), and the strip across the top that the status bar
 * covers. All bands are fractions of the frame.
 */
class ArtScene private constructor(
    val width: Double,
    val height: Double,
    val clearLeft: Double,
    val clearRight: Double,
    val clearTop: Double,
    private val safeLeftFloor: Double,
    private val safeRightCap: Double,
    private val focusAcrossFloor: Double,
    private val focusAcrossCeiling: Double,
    /** Where the subject should sit down the frame: 0.5 is the middle, less is higher. */
    val focusDown: Double,
) {
    val aspect: Double get() = width / maxOf(1.0, height)

    /** Whether the frame has a size. A frame with no size is not cut for. */
    val isLaidOut: Boolean get() = width > 0 && height > 0

    /** The face's limits across the frame. Inside them the face is visible. */
    val faceHardLeft: Double get() = maxOf(clearLeft, safeLeftFloor)
    val faceHardRight: Double get() = maxOf(faceHardLeft + 0.02, minOf(clearRight, safeRightCap))

    private val safeCentre: Double get() = (faceHardLeft + faceHardRight) / 2
    private val safeHalf: Double get() = maxOf(0.01, (faceHardRight - faceHardLeft) / 2)

    /** Where across the frame the subject goes: a little right of the middle of the clear band. */
    val focusAcross: Double get() = (safeCentre + safeHalf * 0.15).coerceIn(focusAcrossFloor, focusAcrossCeiling)

    val faceIdealLeft: Double get() = safeCentre - safeHalf * 0.31
    val faceIdealRight: Double get() = safeCentre + safeHalf * 0.31

    /** How well a face [across] the frame sits: 1 in the ideal band, falling to 0 at the hard limits. */
    fun facePosition(across: Double): Double = when {
        across <= faceHardLeft || across >= faceHardRight -> 0.0
        across < faceIdealLeft -> (across - faceHardLeft) / maxOf(1e-6, faceIdealLeft - faceHardLeft)
        across > faceIdealRight -> (faceHardRight - across) / maxOf(1e-6, faceHardRight - faceIdealRight)
        else -> 1.0
    }

    override fun equals(other: Any?): Boolean = other is ArtScene && width == other.width && height == other.height &&
        clearLeft == other.clearLeft && clearRight == other.clearRight && clearTop == other.clearTop && focusDown == other.focusDown

    override fun hashCode(): Int = listOf(width, height, clearLeft, clearRight, clearTop, focusDown).hashCode()

    companion object {
        /**
         * A frame whose left is clear. What covers the frame is right of [clearRight] and below [clearBottom], and the
         * status bar covers the top [clearTop]. Each value is clamped to a sensible range, so even an impossible
         * frame has its bands in a valid order.
         */
        fun forClearLeft(width: Double, height: Double, clearTop: Double, clearRight: Double, clearBottom: Double): ArtScene {
            val right = clearRight.coerceIn(0.2, 1.0)
            val bottom = clearBottom.coerceIn(0.2, 1.0)
            val top = clearTop.coerceIn(0.0, 0.5)
            return ArtScene(
                width, height,
                clearLeft = 0.0, clearRight = right, clearTop = top,
                safeLeftFloor = 0.04, safeRightCap = right,
                focusAcrossFloor = 0.05, focusAcrossCeiling = 0.95,
                // A little above the middle of the clear band. Eyes look best high in a frame, and this leaves
                // room above the face box for the hair that the crop adds.
                focusDown = (bottom * 0.45).coerceIn(0.15, 0.5),
            )
        }
    }
}
