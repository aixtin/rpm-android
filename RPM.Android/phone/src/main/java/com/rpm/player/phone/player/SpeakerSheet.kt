package com.rpm.player.phone.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpm.player.core.R
import com.rpm.player.core.cast.CastDevice
import com.rpm.player.core.cast.CastState
import com.rpm.player.core.cast.Casting
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.GlassCard
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.kicker
import kotlinx.coroutines.delay

/** How long a search for speakers is shown as running, as the iPhone's `Speakers.look` searches for four seconds. */
private const val LOOK_MS = 4_000L

/**
 * The speakers on the network the player can play on, and the result of the last try: the model of the speaker picker,
 * the iPhone's `Speakers` (RPMAudio/Speakers.swift), over `:core`'s [Casting]. **Nothing is searched for until the
 * picker opens** ([Looking]); browsing takes a few seconds and is not needed before that.
 */
@Stable
class Speakers(val casting: Casting) {
    /** Whether the search is still in its first seconds, the time the iPhone spends on one search. */
    var looking by mutableStateOf(false)
        internal set

    /** When the last search began, so a search started again restarts the wait. */
    internal var lookedAt by mutableLongStateOf(0L)

    /** What went wrong with the last attempt, for the picker to show. */
    var trouble by mutableStateOf<String?>(null)
        internal set

    /** When the person last picked a speaker. A notice from before it is about an earlier attempt. */
    internal var pickedAt = 0L

    fun cast(device: CastDevice) {
        trouble = null
        pickedAt = SystemClock.uptimeMillis()
        casting.castTo(device)
    }

    /** Moves the sound back to this phone. */
    fun stop() {
        casting.stop()
        trouble = null
    }

    fun again() {
        casting.discovery.again()
        lookedAt = SystemClock.uptimeMillis()
    }
}

/**
 * Keeps [speakers] in step with what the casting says went wrong: a notice after the last pick is the trouble shown in
 * the picker. Called once, where the speakers are made.
 */
@Composable
fun FollowCastTrouble(speakers: Speakers) {
    val notice by speakers.casting.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val said = notice ?: return@LaunchedEffect
        if (said.serial >= speakers.pickedAt && speakers.pickedAt > 0) speakers.trouble = said.text
    }
}

/**
 * Where the sound goes. It is the iPhone's `SpeakerSheet` (RPMUI/SpeakerSheet.swift): a glass card over a dimmed screen,
 * the handset first, then whatever answered, with the one playing ticked. **A speaker in a stereo pair still answers and
 * then refuses**, so the refusal says to choose the group, which is what works.
 *
 * The search runs while the sheet is open, and stops with it.
 */
@Composable
fun SpeakerSheet(speakers: Speakers, backdrop: Backdrop, onClose: () -> Unit) {
    val casting = speakers.casting
    val state by casting.state.collectAsStateWithLifecycle()
    val found by casting.discovery.found.collectAsStateWithLifecycle()
    val playingOn = (state as? CastState.On)?.device ?: (state as? CastState.Connecting)?.device
    BackHandler(onBack = onClose)

    // The search begins as the sheet opens, not as the app starts.
    DisposableEffect(casting) {
        casting.discovery.look()
        speakers.lookedAt = SystemClock.uptimeMillis()
        onDispose { casting.discovery.stopLooking() }
    }
    LaunchedEffect(speakers.lookedAt) {
        speakers.looking = true
        delay(LOOK_MS)
        speakers.looking = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0x99 / 255f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(backdrop, Modifier.width(320.dp).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(stringResource(R.string.Rpm_Cast_Title), Modifier.weight(1f), style = displayBold(15f).copy(color = Color.White))
                    if (speakers.looking) {
                        Spinner(14.dp, Ink.white(0x88))
                    } else {
                        val again = stringResource(R.string.Rpm_Cast_LookAgain)
                        BasicText(
                            Glyph.refresh,
                            Modifier
                                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { speakers.again() }
                                .semantics { contentDescription = again },
                            style = icon(13f).copy(color = Ink.white(0x88)),
                        )
                    }
                }
                Divider()
                if (found.isEmpty() && !speakers.looking) {
                    BasicText(
                        stringResource(R.string.Rpm_Cast_NoneNearby),
                        Modifier.fillMaxWidth().padding(18.dp),
                        style = kicker().copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    )
                } else {
                    // The handset is a row like any other and always there: it is how somebody takes the sound back.
                    LazyColumn(Modifier.heightIn(max = 300.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                        item(key = "handset") {
                            Line(stringResource(R.string.Rpm_Cast_ThisAndroidPhone), null, Glyph.phone, on = playingOn == null) {
                                speakers.stop()
                                onClose()
                            }
                        }
                        items(found, key = { it.id }) { device ->
                            // Shown only where it says something: a group is the one that works when the speaker beside
                            // it refuses.
                            Line(
                                device.name,
                                if (device.isGroup) stringResource(R.string.Rpm_Cast_Group) else null,
                                Glyph.speaker,
                                on = playingOn?.id == device.id,
                            ) {
                                speakers.cast(device)
                                onClose()
                            }
                        }
                    }
                }
                speakers.trouble?.let { said ->
                    Divider()
                    BasicText(said, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), style = display(12f).copy(color = Ink.red))
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.white(0x1A)))
}

/** One place the sound can go. The glyph is white while chosen, and the tick at the end shows which one is. */
@Composable
private fun Line(name: String, said: String?, glyph: String, on: Boolean, onClick: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (pressed) Ink.white(0x14) else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            BasicText(glyph, style = icon(15f).copy(color = if (on) Color.White else Ink.white(0xB3)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(name, maxLines = 1, style = display(15f).copy(color = Color.White))
            if (said != null) BasicText(said, maxLines = 1, style = display(12f).copy(color = Ink.white(0x88)))
        }
        if (on) BasicText(Glyph.check, style = icon(14f).copy(color = Color.White))
    }
}

/**
 * The cast mark: a screen with arcs out of its corner, Google's Material icon (Apache 2.0), as the iPhone's
 * `CastMark` draws it. **A speaker glyph would be wrong here**: a speaker shows where sound comes from, and this key
 * shows where it goes. The screen is filled while a room is listening ([live]).
 */
@Composable
fun CastMark(live: Boolean, color: Color, edge: Dp) {
    val path = remember(live) { PathParser().parsePathString(if (live) CAST_CONNECTED else CAST).toPath() }
    Canvas(Modifier.size(edge)) {
        val scale = size.minDimension / 24f
        val fitted = Path().apply {
            addPath(path)
            transform(Matrix().apply { scale(scale, scale) })
        }
        drawPath(fitted, color)
    }
}

private const val CAST =
    "M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z" +
        "M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7z" +
        "m0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z"

private const val CAST_CONNECTED =
    "M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7z" +
        "M19 7H5v1.63c3.96 1.28 7.09 4.41 8.37 8.37H19V7z" +
        "M1 10v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z" +
        "m20-7H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"

/**
 * The system's activity ring, as iOS draws its `ProgressView`: eight spokes, the brightest one going round. It turns
 * in the draw phase, so it recomposes nothing while it spins.
 */
@Composable
internal fun Spinner(edge: Dp, color: Color) {
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        0f, 8f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "spokes",
    )
    Canvas(Modifier.size(edge)) {
        val step = turn.toInt()
        val r = size.minDimension / 2
        val stroke = r * 0.22f
        for (i in 0 until 8) {
            val age = (step - i + 8) % 8
            rotate(i * 45f) {
                drawLine(
                    color.copy(alpha = color.alpha * (1f - age / 9f)),
                    Offset(center.x, center.y - r * 0.45f),
                    Offset(center.x, center.y - r + stroke / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
