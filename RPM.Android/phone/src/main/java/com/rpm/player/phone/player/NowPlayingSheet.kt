package com.rpm.player.phone.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.playback.NowPlaying
import com.rpm.player.core.playback.rememberPosition
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.library.SleeveSquare
import com.rpm.player.phone.ui.ArtGround
import com.rpm.player.phone.ui.RecordDisc
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The stiffness of a spring with no bounce and a response of 0.35 s. It matches SwiftUI's `spring(duration: 0.35)`. */
private const val SHEET_STIFFNESS = 322f

/** The size of the cover, and the scale it shrinks to while the record is paused. */
private val SLEEVE = 260.dp
private const val PAUSED_SCALE = 0.9f

/** How far a flick across the sleeve has to go to change the track. */
private val FLICK = 80.dp

/**
 * How long the play key must be held before letting go stops playback instead of pausing it. This is the system's
 * long-press time.
 */
private const val HOLD_FOR_STOP_MS = 500L

/** How far a finger may wander and still be on the play key. */
private val HOLD_SLIP = 40.dp

/**
 * The player. It is raised over everything, including the tabs and the mini player. It matches the iPhone's
 * `NowPlayingSheet` (RPMUI/NowPlayingSheet.swift). The cover is large, and it shrinks a little while the record
 * is paused. Under it are the title and the artist, the seek bar, and the transport controls, with play in the
 * centre.
 *
 * The sheet moves with the finger on the grip. It goes down when it is let go far enough, or when the grip is
 * tapped. One value, `offset`, moves the sheet both ways. This matches the iPhone's `ScreenPull`. The top
 * corners are square while the sheet touches the top of the screen. They become round once it moves away from the top.
 *
 * Not yet implemented: the room behind the cover and the spectrum beside it, the heart key, the speaker key,
 * and the row of lyrics, queue and menu under the transport.
 *
 * @param onAlbum opens the playing record's page. It is null where the library has no page for the record.
 * @param onClosed called once the sheet is down, so the caller can remove it.
 */
@Composable
fun NowPlayingSheet(now: NowPlaying, player: Player, images: ImageLoader, onAlbum: (() -> Unit)?, onClosed: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = with(density) { maxHeight.toPx() }
        val offset = remember { Animatable(height) }
        val sheetSpring = spring<Float>(dampingRatio = 1f, stiffness = SHEET_STIFFNESS)
        LaunchedEffect(Unit) { offset.animateTo(0f, sheetSpring) }

        fun putAway(then: () -> Unit = {}) {
            scope.launch {
                offset.animateTo(height, sheetSpring)
                onClosed()
                then()
            }
        }
        BackHandler { putAway() }

        val rounding = with(density) { (offset.value / 40.dp.toPx()).coerceIn(0f, 1f) * 44.dp.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(topStart = rounding, topEnd = rounding)
                }
                // Every touch on the player stops at the player: one between the controls must not reach the
                // page underneath.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            ArtGround(now.cover, images)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Grip(
                    onDrag = { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) } },
                    onLetGo = { velocity ->
                        if (offset.value > height / 4 || velocity > 1500f) putAway() else scope.launch { offset.animateTo(0f, sheetSpring) }
                    },
                    onTap = { putAway() },
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Sleeve(now, player, images)
                }
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Words(now, onAlbum = onAlbum?.let { open -> { putAway(open) } })
                    Needle(now, player)
                    Transport(now, player)
                }
            }
        }
    }
}

/**
 * The strip at the top that carries the sheet. Pull it down to put the player away, or tap it. The whole strip
 * takes the touch, not only the bar drawn in it.
 */
@Composable
private fun Grip(onDrag: (Float) -> Unit, onLetGo: (Float) -> Unit, onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .draggable(rememberDraggableState(onDrag), Orientation.Vertical, onDragStopped = { onLetGo(it) })
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        // The grabber bar, 36 by 5 dp, in a pale grey. It is drawn here because this is not a system sheet.
        Box(Modifier.size(36.dp, 5.dp).clip(RoundedCornerShape(percent = 50)).background(Color(0x4DEBEBF5)))
    }
}

/**
 * The cover: square and centred. It is full size while the record plays, and a tenth smaller while it is paused.
 * Flick it across for the next track or the previous one. With no cover, the record itself is drawn, and it does not
 * turn.
 */
@Composable
private fun Sleeve(now: NowPlaying, player: Player, images: ImageLoader) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scale by animateFloatAsState(if (now.playing) 1f else PAUSED_SCALE, spring(dampingRatio = 1f, stiffness = SHEET_STIFFNESS), label = "sleeve")
    val flick = remember { Animatable(0f) }
    val onStation = now.station != null

    Box(
        Modifier
            .size(SLEEVE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = flick.value
            }
            .pointerInput(onStation) {
                if (onStation) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val far = abs(flick.value) > with(density) { FLICK.toPx() }
                        val left = flick.value < 0
                        scope.launch { flick.animateTo(0f, spring(dampingRatio = 1f, stiffness = 439f)) }
                        if (far) if (left) player.seekToNext() else player.seekToPrevious()
                    },
                    onDragCancel = { scope.launch { flick.animateTo(0f) } },
                ) { _, dx -> scope.launch { flick.snapTo(flick.value + dx) } }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (now.cover == null && now.station == null) {
            RecordDisc(SLEEVE, spinning = false)
        } else {
            SleeveSquare(now.cover, SLEEVE, images, radius = 14.dp, glyphSize = 52f)
        }
    }
}

/**
 * The title over the artist. A tap on either line opens the record's page. Both lines scroll when they do not
 * fit, as on the desktop.
 */
@Composable
private fun Words(now: NowPlaying, onAlbum: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = onAlbum != null) { onAlbum?.invoke() },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(now.title, Modifier.basicMarquee(iterations = Int.MAX_VALUE), maxLines = 1, style = displayBold(22f).copy(color = Color.White))
        }
        Box(Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(now.artist, Modifier.basicMarquee(iterations = Int.MAX_VALUE), maxLines = 1, style = display(15f).copy(color = Ink.white(0xB3)))
        }
    }
}

/**
 * The seek bar and its clocks. It matches the iPhone's `PlayerNeedle`. While the bar is dragged, it follows the
 * finger, not the sound. The player seeks once, where the finger lifts. A station has no bar. It shows only "On air".
 */
@Composable
private fun Needle(now: NowPlaying, player: Player) {
    val position by rememberPosition(now)
    var scrubbing by remember { mutableStateOf<Long?>(null) }
    val shown = scrubbing ?: position
    val clocks = display(11f).copy(color = Ink.white(0x88))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (now.station != null) {
            Spacer(Modifier.height(26.dp))
            BasicText(stringResource(R.string.Radio_OnAir), Modifier.fillMaxWidth(), style = clocks.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            return@Column
        }
        BoxWithConstraints(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(now.durationMs) {
                    if (now.durationMs <= 0) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun at(x: Float) = ((x / size.width).coerceIn(0f, 1f) * now.durationMs).toLong()
                        scrubbing = at(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            scrubbing = at(change.position.x)
                            change.consume()
                        } while (true)
                        scrubbing?.let(player::seekTo)
                        scrubbing = null
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val through = if (now.durationMs > 0) (shown.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
            val played = maxWidth * through
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(percent = 50)).background(Ink.white(0x2E)))
            Box(Modifier.width(played).height(3.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White))
            Box(Modifier.offset(x = (played - 1.dp).coerceAtLeast(0.dp)).size(2.dp, 14.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White))
        }
        Row(Modifier.fillMaxWidth()) {
            BasicText(clock(shown), style = clocks)
            Spacer(Modifier.weight(1f))
            if (now.durationMs > 0) BasicText("-" + clock(now.durationMs - shown), style = clocks)
        }
    }
}

private fun clock(ms: Long): String {
    val whole = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(whole / 60, whole % 60)
}

/**
 * Play is in the centre, where a thumb finds it without looking. Previous and next are on either side. Stop is
 * a long press on the play key. A tap pauses or plays. A long hold stops, which empties the queue and takes the
 * player down with it.
 */
@Composable
private fun Transport(now: NowPlaying, player: Player) {
    val onStation = now.station != null
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Key(Glyph.backward, 22f, stringResource(R.string.Transport_Previous), enabled = !onStation) { player.seekToPrevious() }
        PlayKey(now, player)
        Key(Glyph.forward, 22f, stringResource(R.string.Transport_Next), enabled = !onStation && player.hasNextMediaItem()) { player.seekToNext() }
    }
}

@Composable
private fun Key(glyph: String, size: Float, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = icon(size).copy(color = Ink.white(0xB8)))
    }
}

/** A press on the play key, from the touch down to the lift. The lift reads this and nothing else. */
private enum class Press { Brief, Held, Away }

@Composable
private fun PlayKey(now: NowPlaying, player: Player) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var press by remember { mutableStateOf<Press?>(null) }
    val glyph = when {
        press == Press.Held -> Glyph.stop
        now.playing -> Glyph.pause
        else -> Glyph.play
    }
    val label = stringResource(R.string.Transport_PlayPause)
    Box(
        Modifier
            .size(68.dp)
            .semantics { contentDescription = label }
            .pointerInput(player) {
                val slip = with(density) { HOLD_SLIP.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    press = Press.Brief
                    val watch: Job = scope.launch {
                        delay(HOLD_FOR_STOP_MS)
                        if (press == Press.Brief) press = Press.Held
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val moved = change.position - down.position
                        if (hypot(moved.x, moved.y) > slip) {
                            watch.cancel()
                            press = Press.Away
                        }
                    }
                    watch.cancel()
                    when (press) {
                        Press.Brief -> toggle(player)
                        Press.Held -> stop(player)
                        Press.Away, null -> Unit
                    }
                    press = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = icon(26f).copy(color = Color.White))
    }
}

/**
 * Play or pause. From a stopped player, or at the end of a queue, playback starts from the start, as a media key does.
 */
@OptIn(UnstableApi::class)
private fun toggle(player: Player) {
    Util.handlePlayPauseButtonAction(player)
}

/**
 * Puts the record away. The queue is emptied, so nothing is left playing. The mini player at the foot goes, and
 * the player goes with it. The playback service then forgets the saved queue, as the iPhone's stop does.
 */
private fun stop(player: Player) {
    player.pause()
    player.clearMediaItems()
}
