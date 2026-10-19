package com.rpm.player.phone.shell

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.playback.NowPlaying
import com.rpm.player.core.playback.rememberPosition
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.chromeGlass
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.library.SleeveSquare

/**
 * The height of the strip. The row of controls is 40 dp high and starts 9 dp from the top. The rest is the gap above
 * the progress bar.
 */
private val STRIP = 62.dp
private val ROW = 40.dp
private val ABOVE_ROW = 9.dp
private val BAR = 2.dp

/**
 * What is playing, on a glass capsule above the tabs. The iPhone app has the same `MiniPlayer`
 * (RPMUI/MiniPlayer.swift). It shows the cover, the title above the artist, and the previous, play and next
 * keys. A line along the bottom grows as the track plays. A tap anywhere that is not a key opens the full player.
 *
 * Stop is not shown here. On a strip this size, a stop key would be hit by accident. On a station, the
 * previous and next keys are disabled, because a station has no queue.
 *
 * Not yet done: the station's logo, and the bar in the accent colour of the cover, as the iPhone app draws it.
 */
@Composable
fun MiniPlayer(now: NowPlaying, player: Player, images: ImageLoader, backdrop: Backdrop, onOpen: () -> Unit) {
    val capsule = RoundedCornerShape(percent = 50)
    val onStation = now.station != null
    Column(
        Modifier
            .fillMaxWidth()
            .height(STRIP)
            .chromeGlass(backdrop, capsule)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
            .padding(top = ABOVE_ROW),
    ) {
        Row(
            // 15 dp on the left, so the cover's corners sit inside the curve of the capsule. The keys' icons are
            // narrower than their frames, so 9 dp on the right is enough.
            Modifier.height(ROW).padding(start = 15.dp, end = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SleeveSquare(now.cover, ROW, images, radius = 8.dp, glyphSize = 16f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                // Each line scrolls once if it does not fit. The iPhone app does the same with its `OnceTicker`.
                BasicText(now.title, Modifier.basicMarquee(iterations = 1), maxLines = 1, style = display(14f).copy(color = Color.White))
                BasicText(now.artist, Modifier.basicMarquee(iterations = 1), maxLines = 1, style = display(11f).copy(color = Ink.white(0x88)))
            }
            Row {
                Key(Glyph.backward, stringResource(R.string.Transport_Previous), enabled = !onStation) { player.seekToPrevious() }
                Key(
                    if (now.playing) Glyph.pause else Glyph.play,
                    stringResource(if (now.playing) R.string.CloudChip_Pause else R.string.AlbumMenu_Play),
                    enabled = true,
                ) { toggle(player) }
                Key(Glyph.forward, stringResource(R.string.Transport_Next), enabled = !onStation && player.hasNextMediaItem()) { player.seekToNext() }
            }
        }
        Spacer(Modifier.height(STRIP - ABOVE_ROW - ROW - BAR))
        if (!onStation) Progress(now)
    }
}

/**
 * Play or pause. At the end of a queue, or on a stopped player, playback starts from the start, as a media key does.
 */
@OptIn(UnstableApi::class)
private fun toggle(player: Player) {
    Util.handlePlayPauseButtonAction(player)
}

@Composable
private fun Key(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = icon(15f).copy(color = Ink.white(0xB8)))
    }
}

/**
 * The progress bar on the bottom border of the strip. Only the part played is drawn. The bar fades in from
 * nothing over its first 16 dp, so its end joins the border. It is a little short of solid, so the border
 * shows through it. The bar is white here. The iPhone app draws it in the accent colour of the cover, which
 * Android does not do yet.
 */
@Composable
private fun Progress(now: NowPlaying) {
    val position by rememberPosition(now)
    val through = if (now.durationMs > 0) (position.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
    Canvas(Modifier.fillMaxWidth().height(BAR).padding(horizontal = STRIP / 2)) {
        val played = size.width * through
        if (played <= 0f) return@Canvas
        val fade = 16.dp.toPx()
        drawRoundRect(
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0f),
                (fade / played).coerceAtMost(0.5f) to Color.White.copy(alpha = 0.92f),
                1f to Color.White.copy(alpha = 0.92f),
                startX = 0f,
                endX = played,
            ),
            size = Size(played, size.height),
            cornerRadius = CornerRadius(size.height / 2),
        )
    }
}
