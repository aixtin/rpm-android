package com.rpm.player.phone.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptic feedback for gestures: the iPhone's `Haptics` (RPMUI/Haptics.swift), with its three kinds. When a gesture
 * changes something under a finger, the finger covers where the change would show, and **a tap on the hand is the one
 * channel that reaches the finger itself**.
 *
 * The system's touch-feedback setting is honoured, as every Android key's own click is.
 */
@Stable
class Haptics internal constructor(private val feedback: HapticFeedback) {
    /** Something has been picked up or opened under the finger: a light tap (`Haptics.pick`). */
    fun pick() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** Something moved one place, or crossed the point where letting go acts: a detent (`Haptics.tick`). */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** The action is done: a firm tap (`Haptics.commit`). */
    fun commit() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
}

/** The haptics of the view this is drawn in. */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
