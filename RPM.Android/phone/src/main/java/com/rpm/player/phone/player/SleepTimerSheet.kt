package com.rpm.player.phone.player

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpm.player.core.R
import com.rpm.player.core.playback.SleepClock
import com.rpm.player.core.playback.SleepTimer
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.GlassButton
import com.rpm.player.phone.design.GlassCard
import com.rpm.player.phone.design.GlassKey
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.kicker
import kotlinx.coroutines.delay
import java.util.Date

/**
 * The text for the sleep timer's durations and time left: the iPhone's `SleepWords` (RPMSharedUI/SleepWords.swift).
 */
internal object SleepWords {
    /** "5 min", "1 hour": a menu option. */
    @Composable
    fun after(ms: Long): String {
        val minutes = (ms / 60_000).toInt()
        return if (minutes == 60) stringResource(R.string.Rpm_Sleep_OneHour) else stringResource(R.string.Rpm_Sleep_Minutes, minutes)
    }

    /** "+5 min", "+1 hour": a key that adds to a running timer. */
    @Composable
    fun adding(ms: Long): String {
        val minutes = (ms / 60_000).toInt()
        return if (minutes == 60) stringResource(R.string.Rpm_Sleep_AddHour) else stringResource(R.string.Rpm_Sleep_AddMinutes, minutes)
    }

    /** "14:52", or "1:02:03" from an hour up. Rounded up, so the last second shows 0:01 while the music still plays. */
    fun clock(ms: Long): String {
        val whole = ((ms + 999) / 1000).toInt()
        val hours = whole / 3600
        val minutes = whole % 3600 / 60
        val seconds = whole % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}

/**
 * The sleep timer while it runs: the time left, keys that add time, and the way to turn it off. It is the iPhone's
 * `SleepTimerSheet` (RPMUI/SleepTimerSheet.swift): the same glass card over a dimmed screen as the speaker picker. It
 * opens from the player's menu once a timer runs; a timer is started from the menu itself.
 *
 * Only the clock and the line under the title follow the time, once a second, in a composable of their own.
 */
@Composable
fun SleepTimerSheet(sleep: SleepClock, onStation: Boolean, hasTrack: Boolean, backdrop: Backdrop, onClose: () -> Unit) {
    val timer by sleep.timer.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0x99 / 255f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(backdrop, Modifier.width(320.dp).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
            Column {
                Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        BasicText(stringResource(R.string.Rpm_Sleep_Title), maxLines = 1, style = displayBold(19f).copy(color = Color.White))
                        Ticking(sleep) { left -> BasicText(stops(timer, left), maxLines = 2, style = kicker()) }
                    }
                    val close = stringResource(R.string.Rpm_Sleep_Close)
                    GlassKey(backdrop, onClose, Modifier.semantics { contentDescription = close }) {
                        BasicText("", style = icon(15f).copy(color = Ink.white(0xB8)))
                    }
                }
                Divider()
                Ticking(sleep) { left ->
                    val said = left?.let(SleepWords::clock).orEmpty()
                    val label = left?.let { stringResource(R.string.Rpm_Sleep_Left, SleepWords.clock(it)) }.orEmpty()
                    BasicText(
                        said,
                        Modifier.fillMaxWidth().padding(vertical = 18.dp).semantics { contentDescription = label },
                        style = displayBold(44f).copy(color = Color.White, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                    )
                }
                Divider()
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(stringResource(R.string.Rpm_Sleep_AddTime), style = kicker())
                    for (pair in SleepTimer.DURATIONS_MS.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (wait in pair) {
                                GlassButton(onClick = { sleep.add(wait) }, modifier = Modifier.weight(1f), height = 42.dp, inset = 8.dp, size = 14f) { style ->
                                    BasicText(SleepWords.adding(wait), style = style)
                                }
                            }
                        }
                    }
                }
                Divider()
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Offered only where it means something: not when the timer already waits for the end of the song,
                    // and not on a station, which has no end.
                    if (!timer.endsWithTheTrack && !onStation && hasTrack) {
                        GlassButton(onClick = { sleep.set(SleepTimer.Choice.EndOfTrack) }, height = 42.dp, size = 14f) { style ->
                            BasicText(stringResource(R.string.Rpm_Sleep_EndOfSong), style = style)
                        }
                    }
                    GlassButton(onClick = { sleep.turnOff(); onClose() }, height = 42.dp, size = 14f) { style ->
                        BasicText(stringResource(R.string.Rpm_Sleep_TurnOff), style = style)
                    }
                }
            }
        }
    }
}

/** The time left on [sleep], read again each second for [content] alone. */
@Composable
private fun Ticking(sleep: SleepClock, content: @Composable (Long?) -> Unit) {
    var left by remember { mutableStateOf(sleep.leftMs()) }
    LaunchedEffect(sleep) {
        while (true) {
            left = sleep.leftMs()
            // Until the shown second changes: the clock is rounded up, so that is when the part past it runs out.
            val part = (left ?: 0L) % 1_000
            delay(if (part == 0L) 1_000 else part + 1)
        }
    }
    content(left)
}

/** When playback stops: a clock time, or the end of the song. */
@Composable
private fun stops(timer: SleepTimer, left: Long?): String {
    if (timer.endsWithTheTrack) return stringResource(R.string.Rpm_Sleep_StopsAtEndOfSong)
    left ?: return ""
    val context = LocalContext.current
    // The time of day to the minute, so it changes when the minute does and not every second.
    val at = DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + left))
    return stringResource(R.string.Rpm_Sleep_StopsAt, at)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.white(0x1A)))
}
