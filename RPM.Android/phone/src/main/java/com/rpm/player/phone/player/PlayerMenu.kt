package com.rpm.player.phone.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.rpm.player.core.R
import com.rpm.player.core.playback.SleepTimer
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.GlassCard
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.system
import kotlin.math.roundToInt

/** One row of the player's menu: what it says, its glyph, and what it does. */
internal class MenuItem(val label: String, val glyph: String, val opens: Boolean = false, val onPick: () -> Unit)

/** Font Awesome glyphs for the menu, standing in for the SF Symbols the iPhone's menu shows. */
internal object MenuGlyph {
    /** `text.badge.plus`. */
    const val file = ""

    /** `square.stack`. */
    const val album = Glyph.record

    /** `music.mic`. */
    const val artist = ""

    /** `moon.zzz`. */
    const val sleep = ""

    /** The way into a submenu. */
    const val into = Glyph.into
}

/**
 * The player's menu: add to playlist, the record's page, the artist's page, and the sleep timer, as the iPhone's
 * `Menu` under the transport. It opens over the sheet from the key that opened it, as a system menu does, on the same
 * glass as the app's other menus. A tap anywhere else closes it.
 *
 * The sleep timer's options are a menu inside it, as on iPhone: the item opens them in place, with a way back.
 *
 * @param anchor the bounds of the key that opened it, in the sheet's own pixels.
 */
@Composable
internal fun PlayerMenu(
    backdrop: Backdrop,
    anchor: Rect,
    items: List<MenuItem>,
    sleepOptions: List<MenuItem>?,
    onClose: () -> Unit,
) {
    var inSleep by remember { mutableStateOf(false) }
    BackHandler { if (inSleep) inSleep = false else onClose() }
    val density = LocalDensity.current
    val gutter = with(density) { 16.dp.roundToPx() }
    val gap = with(density) { 8.dp.roundToPx() }
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        Layout(
            content = {
                GlassCard(backdrop, Modifier.width(250.dp), radius = 14.dp) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        if (inSleep && sleepOptions != null) {
                            Line(MenuItem(stringResource(R.string.Rpm_Sleep_Title), Glyph.back) { inSleep = false }, header = true)
                            for (item in sleepOptions) Line(item)
                        } else {
                            for (item in items) Line(item)
                            if (sleepOptions != null) {
                                Line(MenuItem(stringResource(R.string.Rpm_Sleep_Title), MenuGlyph.sleep, opens = true) { inSleep = true })
                            }
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val card = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth - 2 * gutter, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Above the key, centred on it, and kept a gutter in from the sides of the screen.
                val x = (anchor.center.x - card.width / 2f).roundToInt().coerceIn(gutter, constraints.maxWidth - gutter - card.width)
                val y = (anchor.top - gap - card.height).roundToInt().coerceAtLeast(gutter)
                card.place(x, y)
            }
        }
    }
}

/** One row: its label on the left and its glyph on the right, as a system menu lays them out. */
@Composable
private fun Line(item: MenuItem, header: Boolean = false) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (pressed) Color.White.copy(alpha = 0x14 / 255f) else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = item.onPick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (header) {
            BasicText(item.glyph, Modifier.padding(end = 10.dp), style = icon(13f).copy(color = Color.White))
            BasicText(item.label, Modifier.weight(1f), maxLines = 1, style = system(15f, FontWeight.SemiBold).copy(color = Color.White))
            return@Row
        }
        BasicText(item.label, Modifier.weight(1f), maxLines = 1, style = system(15f).copy(color = Color.White))
        BasicText(if (item.opens) MenuGlyph.into else item.glyph, style = icon(13f).copy(color = Color.White))
    }
}

/** The sleep timer's options while it is off: the durations, and the end of the song where there is a song to end. */
@Composable
internal fun sleepChoices(onStation: Boolean, onSet: (SleepTimer.Choice) -> Unit): List<MenuItem> {
    val options = SleepTimer.DURATIONS_MS.map { wait -> MenuItem(SleepWords.after(wait), "") { onSet(SleepTimer.Choice.After(wait)) } }
    return if (onStation) options else options + MenuItem(stringResource(R.string.Rpm_Sleep_EndOfSong), "") { onSet(SleepTimer.Choice.EndOfTrack) }
}
