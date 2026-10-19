package com.rpm.player.phone.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.Cover
import com.rpm.player.phone.design.BackKey
import com.rpm.player.phone.design.GlassButton
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.Weight
import com.rpm.player.phone.design.backdropSource
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.dissolvesAtTop
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.kicker
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.design.scrolled
import com.rpm.player.phone.ui.ArtGround
import kotlin.math.roundToInt

/** The row at the top of a page with no header, where its back key floats (`RPMLayout.backRow` on iPhone). */
internal val BACK_ROW = 44.dp

/** The share of the page's width that the cover takes. This keeps the title and the keys above the fold. */
internal const val COVER_FRACTION = 0.62f

/**
 * One record. It matches the iPhone's `AlbumPage` (RPMUI/AlbumPage.swift). The page uses its own background:
 * the cover, blurred until it is not recognisable. So each record's page has its own colours. The header holds
 * the cover, the title, the artist, how much record there is, and the keys. Under it are the songs. The song
 * that is playing is marked in the margin.
 *
 * Not yet implemented: the favourite (heart) key, the download key, the article, the rating, the reviews,
 * similar albums, the artist page, a classical record's works, and a song's menu.
 *
 * @param playing the id of the track playing, which the mark in the margin follows.
 * @param onPlay plays the record from the song at that index.
 */
@Composable
fun AlbumPage(
    album: CloudAlbum,
    cover: Cover?,
    images: ImageLoader,
    playing: String?,
    bottomRoom: Dp,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val page = rememberBackdrop()
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues(density).calculateTopPadding() + BACK_ROW
    val lines = remember(album) { AlbumLines.of(album.tracks) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val edge = (maxWidth.value * COVER_FRACTION).coerceIn(160f, 360f).roundToInt().dp
        Box(Modifier.fillMaxSize().backdropSource(page)) {
            ArtGround(cover, images)
            // The list scrolls up behind the status bar. Content that scrolls up there fades into the background.
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().dissolvesAtTop(top) { list.scrolled() }) {
                item(key = "header") { Header(album, cover, images, edge, top, onPlay, onShuffle) }
                items(lines, key = { it.key }) { line ->
                    when (line) {
                        is AlbumLine.Disc -> DiscHeading(line.number)
                        is AlbumLine.Song -> SongRow(line.track, playing = line.track.rpmId == playing) { onPlay(line.index) }
                    }
                }
                item(key = "foot") { Spacer(Modifier.height(40.dp + bottomRoom)) }
            }
        }
        // The back key floats over the page. It does not scroll with the page. It sits in the row kept free under the
        // status bar.
        Box(Modifier.padding(top = top - BACK_ROW).height(BACK_ROW).padding(start = 20.dp), contentAlignment = Alignment.Center) {
            BackKey(page, onBack)
        }
    }
}

@Composable
private fun Header(album: CloudAlbum, cover: Cover?, images: ImageLoader, edge: Dp, top: Dp, onPlay: (Int) -> Unit, onShuffle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(top))
        SleeveSquare(cover, edge, images, radius = 4.dp, glyphSize = 52f, bordered = false)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(album.album, maxLines = 3, style = displayBold(24f).copy(color = Color.White, textAlign = TextAlign.Center))
            BasicText(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(16f).copy(color = Ink.white(0xD9)))
        }
        // The song count and the running time, shown as a chip.
        BasicText(
            count(album),
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Ink.white(0x1F))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            style = display(12f).copy(color = Ink.white(0xE6)),
        )
        // The play key, and beside it a shuffle key for the same record.
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(onClick = { onPlay(0) }, modifier = Modifier.weight(1f), weight = Weight.Primary, height = 50.dp, radius = 14.dp) { style ->
                BasicText(Glyph.play, style = icon(15f).copy(color = style.color))
                Spacer(Modifier.width(10.dp))
                BasicText(stringResource(R.string.AlbumGrid_PlayAll), style = display(17f).copy(color = style.color))
            }
            GlassButton(onClick = onShuffle, modifier = Modifier.width(56.dp), stretches = false, height = 50.dp, radius = 14.dp, inset = 0.dp) { style ->
                BasicText(Glyph.shuffle, style = icon(17f).copy(color = style.color))
            }
        }
    }
}

@Composable
private fun count(album: CloudAlbum): String {
    val songs = if (album.tracks.size == 1) stringResource(R.string.Rpm_Album_TracksOne) else stringResource(R.string.Rpm_Album_Tracks, album.tracks.size)
    val seconds = album.tracks.sumOf { it.tags?.durationSeconds ?: 0.0 }.toInt()
    if (seconds <= 0) return songs
    val hours = seconds / 3600
    val runtime = if (hours >= 1) stringResource(R.string.Stats_HoursMinutes, hours, (seconds % 3600) / 60) else stringResource(R.string.Stats_Minutes, seconds / 60)
    return "$songs · $runtime"
}

/** The heading for one disc of a set. A single-disc record never shows one. */
@Composable
private fun DiscHeading(number: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(stringResource(R.string.AlbumEdit_DiscN, number), style = kicker())
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.white(0x1F)))
    }
}

/**
 * One song: the playing mark in the margin, the track number in a fixed column, the title over the track's
 * own artist, and the length. The artist line is on every row, so all rows have the same height. The row is
 * lit while a finger is on it.
 */
@Composable
private fun SongRow(track: CloudTrack, playing: Boolean, onClick: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(if (pressed) Ink.white(0x24) else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(percent = 50)).background(if (playing) Color.White else Color.Transparent))
        BasicText(
            track.tags?.trackNumber?.takeIf { it > 0 }?.toString().orEmpty(),
            Modifier.width(22.dp),
            style = display(14f).copy(color = Ink.white(0x66)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(track.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(16f).copy(color = Color.White))
            track.artist?.takeIf { it.isNotEmpty() }?.let {
                BasicText(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(13f).copy(color = Ink.white(0x77)))
            }
        }
        BasicText(length(track.tags?.durationSeconds ?: 0.0), style = display(13f).copy(color = Ink.white(0x66)))
    }
}

/** A song's length as minutes and seconds. It is blank when the catalogue gives no length. */
internal fun length(seconds: Double): String {
    val whole = seconds.toInt()
    return if (whole <= 0) "" else "%d:%02d".format(whole / 60, whole % 60)
}

/** One line of a record's listing. */
sealed interface AlbumLine {
    val key: String

    data class Disc(val number: Int) : AlbumLine {
        override val key get() = "disc:$number"
    }

    /** [index] is the song's place in the record's own order. Playing from this row starts at that place. */
    data class Song(val track: CloudTrack, val index: Int) : AlbumLine {
        override val key get() = "song:${track.rpmId}:$index"
    }
}

/**
 * The lines of a record that is not classical, as on iPhone (`AlbumLines`). The songs are in order. A heading
 * is added at the start of each disc of a set.
 */
object AlbumLines {
    fun of(tracks: List<CloudTrack>): List<AlbumLine> {
        val discs = tracks.map { it.tags?.discNumber ?: 0 }.filter { it > 0 }.toSet()
        val set = discs.size > 1
        val lines = ArrayList<AlbumLine>(tracks.size + discs.size)
        var disc: Int? = null
        tracks.forEachIndexed { index, track ->
            val number = track.tags?.discNumber ?: 0
            if (set && number > 0 && number != disc) {
                lines += AlbumLine.Disc(number)
                disc = number
            }
            lines += AlbumLine.Song(track, index)
        }
        return lines
    }
}
