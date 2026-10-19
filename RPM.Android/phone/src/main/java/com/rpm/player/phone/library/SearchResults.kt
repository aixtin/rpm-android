package com.rpm.player.phone.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.SongSearch
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.dissolvesAtTop
import com.rpm.player.phone.design.kicker
import com.rpm.player.phone.design.scrolled

/** What one search answered, and the words it is the answer to. */
internal class SearchAnswer(val asked: String, val records: List<CloudAlbum>, val songs: List<SongSearch.SongHit>) {
    companion object {
        val NONE = SearchAnswer("", emptyList(), emptyList())
    }
}

/**
 * The results of a library search. It matches the iPhone's `LibraryPage.results`. The records that match the
 * words come first, then the songs that match. Each group has its own heading. A record is opened and a song
 * is played, so one list could not show which action a row does.
 *
 * "No search results" is shown only when the answer is for the words now in the box. While the typing is
 * still settling, that message would be wrong for the next keystroke. Dragging the list hides the keyboard.
 */
@Composable
internal fun SearchResults(
    asked: String,
    answer: SearchAnswer,
    collection: Collection,
    images: ImageLoader,
    playing: String?,
    bottomRoom: Dp,
    onOpen: (CloudAlbum) -> Unit,
    onPlay: (SongSearch.SongHit) -> Unit,
) {
    if (answer.records.isEmpty() && answer.songs.isEmpty()) {
        if (answer.asked == asked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(stringResource(R.string.Library_NoSearchResults), style = display(14f).copy(color = Ink.white(0x88)))
            }
        }
        return
    }
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) focus.clearFocus() }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().dissolvesAtTop(30.dp) { list.scrolled() },
        contentPadding = PaddingValues(bottom = FOOT + bottomRoom),
    ) {
        if (answer.records.isNotEmpty()) {
            item(key = "records") { Heading(stringResource(R.string.Viz_BrowseAlbums)) }
            items(answer.records, key = { "record:" + it.key }) { album ->
                Line(album, collection, images, album.album, album.artist, mark = false, onClick = { onOpen(album) }) {
                    BasicText(if (album.tracks.size == 1) stringResource(R.string.Rpm_Album_TracksOne) else stringResource(R.string.Rpm_Album_Tracks, album.tracks.size), maxLines = 1, style = display(12f).copy(color = Ink.white(0x66)))
                }
            }
        }
        if (answer.songs.isNotEmpty()) {
            item(key = "songs") { Heading(stringResource(R.string.Album_Tracks)) }
            items(answer.songs, key = { "song:" + it.track.rpmId }) { hit ->
                Line(hit.album, collection, images, hit.track.title.orEmpty(), hit.track.artist.orEmpty(), mark = hit.track.rpmId == playing, onClick = { onPlay(hit) })
            }
        }
        if (answer.songs.size >= SongSearch.CEILING) {
            item(key = "ceiling") {
                BasicText(
                    stringResource(R.string.Rpm_Library_SearchCeiling, SongSearch.CEILING),
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    style = display(12f).copy(color = Ink.white(0x77), textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/** A heading for one group of results. It is in upper case, so it does not look like a row. */
@Composable
private fun Heading(said: String) {
    BasicText(said.uppercase(), Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp), style = kicker())
}

/**
 * One row: the playing mark in the margin (empty on a record's row, so both kinds of row start their covers on
 * the same line), a 40 dp cover, two text lines, and whatever goes at the end.
 */
@Composable
private fun Line(
    album: CloudAlbum,
    collection: Collection,
    images: ImageLoader,
    title: String,
    line: String,
    mark: Boolean,
    onClick: () -> Unit,
    end: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(50)).background(if (mark) Color.White else Color.Transparent))
        Box(Modifier.padding(start = 9.dp)) {
            SleeveSquare(collection.cover(album), 40.dp, images, radius = 6.dp, glyphSize = 15f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(15f).copy(color = Color.White))
            BasicText(line, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(12f).copy(color = Ink.white(0x88)))
        }
        end()
    }
}
