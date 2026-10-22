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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.library.Cover
import com.rpm.player.core.library.ListArt
import com.rpm.player.core.library.MoodLine
import com.rpm.player.core.library.PlaylistFeatures
import com.rpm.player.core.library.TrackList
import com.rpm.player.core.library.moodSentence
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
import com.rpm.player.phone.design.pt
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.design.scrolled
import com.rpm.player.phone.design.system
import com.rpm.player.phone.ui.ArtGround
import kotlin.math.roundToInt

/**
 * One list whose order this phone does not own. That covers a stored or generated list, and the marked songs.
 * It matches the iPhone's `PlaylistPage` (RPMUI/PlaylistPage.swift). The layout is the same as the album
 * page's. The list's picture is large and centred. Below it are the name, how much is in the list, who is on
 * it, and the keys. All of these scroll away with the songs. The back key floats over the page. The queue has
 * its own page ([QueuePage]).
 *
 * The page's background is the list's own picture, blurred. Where there is no picture (the marked songs), the
 * app's background is used. If the cloud has no picture for a stored list, the square shows the cover of the
 * record the list opens on. The iPhone app uses that cover for the square only, not for the background.
 *
 * Not yet implemented: a generated list's room and frame, the key for another selection of a generated list,
 * adding songs, and rearranging a stored list. A song's long press opens the song menu ([LocalSongMenu]).
 *
 * @param name the name shown for the list.
 * @param missing how many songs the list names that the library cannot find.
 * @param blurb a mood list's sentence, or null.
 * @param picture the list's own picture, or null if no picture exists for it. The square then shows the cover of the
 * record the list opens on.
 * @param coverOf the cover of the record that a song is on. It is used for the song's row.
 * @param playing the id of the track playing, which the mark in the margin follows.
 * @param onPlay plays the list from the song at that index: a tap on a row.
 * @param onPlayAll the Play key: the whole list from the top. See [AlbumPage] for why a key and a row differ.
 */
@Composable
fun PlaylistPage(
    name: String,
    tracks: List<CloudTrack>,
    missing: Int,
    blurb: MoodLine?,
    picture: ListArt?,
    coverOf: (CloudTrack) -> Cover?,
    images: ImageLoader,
    playing: String?,
    bottomRoom: Dp,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onBack: () -> Unit,
    onPlayAll: () -> Unit = { onPlay(0) },
) {
    BackHandler(onBack = onBack)
    val page = rememberBackdrop()
    val rows = rememberLazyListState()
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues(density).calculateTopPadding() + BACK_ROW

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val edge = (maxWidth.value * COVER_FRACTION).coerceIn(160f, 360f).roundToInt().dp
        Box(Modifier.fillMaxSize().backdropSource(page)) {
            ArtGround(picture, images)
            LazyColumn(state = rows, modifier = Modifier.fillMaxSize().dissolvesAtTop(top) { rows.scrolled() }) {
                item(key = "header") {
                    val empty = tracks.isEmpty()
                    val square = picture ?: tracks.firstOrNull()?.let(coverOf)
                    ListHead(name, square, holding(tracks.size, seconds(tracks), missing), about(tracks, blurb), if (blurb == null) 2 else 3, images, edge, top) {
                        GlassButton(onClick = onPlayAll, modifier = Modifier.weight(1f), weight = Weight.Primary, enabled = !empty, height = 50.dp, radius = 14.dp) { style ->
                            BasicText(Glyph.play, style = icon(15f).copy(color = style.color))
                            Spacer(Modifier.width(10.dp))
                            BasicText(stringResource(R.string.Rpm_Playlist_PlayList), style = display(17f).copy(color = style.color))
                        }
                        ListKey(Glyph.shuffle, enabled = !empty, onClick = onShuffle)
                    }
                }
                if (tracks.isEmpty()) {
                    item(key = "nothing") { Nothing(missing) }
                } else {
                    // A list can hold the same song twice, so each row's key includes its place in the list.
                    itemsIndexed(tracks, key = { at, track -> "${track.rpmId}:$at" }) { at, track ->
                        SongSwipe(track) { ListSongRow(track, coverOf(track), images, playing = track.rpmId == playing) { onPlay(at) } }
                    }
                }
                item(key = "foot") { Spacer(Modifier.height(40.dp + bottomRoom)) }
            }
        }
        Box(Modifier.padding(top = top - BACK_ROW).height(BACK_ROW).padding(start = 20.dp), contentAlignment = Alignment.Center) {
            BackKey(page, onBack)
        }
    }
}

/**
 * The top part of a list's page. It has the same layout as the album page: the picture large and centred,
 * the name, how much is in the list as a chip, a line about the list, and the keys.
 *
 * @param about the line under the chip, or null for none. [aboutLines] is the most lines it may take.
 */
@Composable
internal fun ListHead(
    name: String,
    picture: Any?,
    holding: String,
    about: String?,
    aboutLines: Int,
    images: ImageLoader,
    edge: Dp,
    top: Dp,
    keys: @Composable RowScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(top))
        PlaylistSleeve(picture, Glyph.record, edge, corner = 4.dp, mark = 52f, images)
        BasicText(name, Modifier.padding(top = 10.dp), maxLines = 3, style = displayBold(24f).copy(color = Color.White, textAlign = TextAlign.Center))
        BasicText(
            holding,
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Ink.white(0x1F))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            style = display(12f).copy(color = Ink.white(0xE6)),
        )
        // What the list is and who is on it. The desktop shows the same line under a list's name.
        about?.let { about ->
            BasicText(
                about,
                Modifier.fillMaxWidth().padding(top = 4.dp),
                maxLines = aboutLines,
                overflow = TextOverflow.Ellipsis,
                style = display(14f).copy(color = Ink.white(0xB3), lineHeight = pt(14f * 1.2f + 7f)),
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = keys)
    }
}

/** A key in a list's head: an icon on the ghost face, 56 dp wide (`PlaylistPage.key` on iPhone). */
@Composable
internal fun ListKey(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    GlassButton(onClick = onClick, modifier = Modifier.width(56.dp), enabled = enabled, stretches = false, height = 50.dp, radius = 14.dp, inset = 0.dp) { style ->
        BasicText(glyph, style = icon(17f).copy(color = style.color))
    }
}

/**
 * A mood's sentence, then the artists that most of the songs are by, for example "Featuring Blur, Pulp and
 * 4 more". Null if there is neither.
 */
@Composable
private fun about(tracks: List<CloudTrack>, mood: MoodLine?): String? {
    val blurb = mood?.let { moodSentence(it) }
    val featured = remember(tracks) { PlaylistFeatures.of(tracks.map { it.artist }, PlaylistFeatures.NAMED) }
    if (featured.named.isEmpty()) return blurb
    val names = featured.named.joinToString(", ")
    val featuring = if (featured.more > 0) {
        stringResource(R.string.Playlists_FeaturingMore, names, featured.more)
    } else {
        stringResource(R.string.Playlists_Featuring, names)
    }
    return if (blurb == null) featuring else "$blurb $featuring"
}

/**
 * One song: the playing mark in the margin, the cover of its record, the title (with an E after it for
 * explicit lyrics), the artist and record below it, and the length. The row is lit while a finger is on it.
 */
@Composable
internal fun ListSongRow(track: CloudTrack, cover: Cover?, images: ImageLoader, playing: Boolean, onClick: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(if (pressed) Ink.white(0x24) else Color.Transparent)
            .songPress(track, presses, onClick)
            .padding(start = 8.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(percent = 50)).background(if (playing) Color.White else Color.Transparent))
        PlaylistSleeve(cover, Glyph.record, edge = 40.dp, corner = 6.dp, mark = 16f, images)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(track.title.orEmpty(), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(16f).copy(color = Color.White))
                if (track.tags?.advisory == EXPLICIT) ExplicitMark()
            }
            said(track).takeIf { it.isNotEmpty() }?.let {
                BasicText(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(13f).copy(color = Ink.white(0x77)))
            }
        }
        BasicText(length(track.tags?.durationSeconds ?: 0.0), style = display(13f).copy(color = Ink.white(0x66)))
    }
}

/**
 * The tag value for explicit lyrics (`ContentAdvisory.Explicit` on the desktop). It is the only value that shows the E.
 */
internal const val EXPLICIT = 1

/**
 * The E mark for explicit lyrics: a small light square with the letter cut out. The letter uses the background colour,
 * so it looks cut out.
 */
@Composable
internal fun ExplicitMark() {
    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Ink.white(0xAA)), contentAlignment = Alignment.Center) {
        BasicText("E", style = system(9f, FontWeight.Bold).copy(color = Ink.ground))
    }
}

/** The artist and the record, for example "Talk Talk · Spirit of Eden". Each part is shown only if the song has it. */
private fun said(track: CloudTrack): String =
    listOfNotNull(track.artist?.takeIf { it.isNotEmpty() }, track.album?.takeIf { it.isNotEmpty() }).joinToString(" · ")

/** An empty list. Either it names songs that have not arrived yet, or it has nothing in it. */
@Composable
private fun Nothing(missing: Int) {
    BasicText(
        if (missing > 0) waitingOn(missing) else stringResource(R.string.Rpm_Playlist_EmptyPlain),
        Modifier.fillMaxWidth().padding(40.dp),
        style = display(15f).copy(color = Ink.white(0xCC), textAlign = TextAlign.Center),
    )
}
