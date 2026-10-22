package com.rpm.player.phone.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.rpm.player.core.playback.NowPlaying
import com.rpm.player.phone.design.Ink
import kotlinx.coroutines.delay

/** How long a wait lasts before the ring shows: long enough that a normal start never flickers. */
private const val PATIENCE_MS = 500L

/** A safety limit, not a timeout: the ring stops showing, and nothing is cancelled. */
private const val CEILING_MS = 30_000L

/** Whether the player waits: it was asked to play and no sound is moving yet, the iPhone's `Player.isBusy`. */
internal val NowPlaying.isBusy: Boolean get() = playing && !advancing && !failed

/**
 * A play key that shows a ring in place of its glyph while the player waits, but only once the wait has lasted long
 * enough to notice: the iPhone's `BusyKey` (RPMUI/BusyKey.swift). The glyph is made transparent, not removed, so the
 * transport does not move under the thumb, and the key stays pressable under the ring.
 */
@Composable
internal fun BusyKey(busy: Boolean, ring: Dp, key: @Composable () -> Unit) {
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (!busy) {
            showing = false
            return@LaunchedEffect
        }
        delay(PATIENCE_MS)
        showing = true
        delay(CEILING_MS - PATIENCE_MS)
        showing = false
    }
    val shown by animateFloatAsState(if (showing) 1f else 0f, tween(150), label = "busy")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { alpha = 1f - shown }) { key() }
        if (showing || shown > 0f) Box(Modifier.graphicsLayer { alpha = shown }) { Spinner(ring, Ink.white(0xE6)) }
    }
}
