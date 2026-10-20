package com.rpm.player.tv.ui

import com.rpm.player.core.library.listName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.TrackList

/**
 * The playlists screen: the lists the computer generated, then the person's own. Each list is a card that
 * opens its list page, laid out like the album wall. The desktop has no screen like this; its drawer uses
 * the same order: generated lists first (the computer sends only those long enough to show, `LeastToShow`),
 * then made lists by name with their folder under them.
 */
@Composable
fun PlaylistsScreen(collection: Collection, images: ImageLoader, onOpenList: (TrackList) -> Unit) {
    val generated = remember(collection) {
        collection.lists.filter { it.generated }
    }
    val own = remember(collection) { collection.lists.filter { !it.generated && it.tracks.isNotEmpty() } }
    val first = remember { FocusRequester() }
    val firstList = generated.firstOrNull() ?: own.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        if (firstList == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Message(stringResource(R.string.Viz_BrowseNoPlaylists)) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(WALL_COLUMNS),
                contentPadding = PaddingValues(start = 34.px, end = 50.px, top = 64.px, bottom = 28.px),
                horizontalArrangement = Arrangement.spacedBy(22.px),
                verticalArrangement = Arrangement.spacedBy(22.px),
                modifier = Modifier.fillMaxSize(),
            ) {
                section(R.string.Home_MadeForYou, generated, collection, images, onOpenList, firstList, first)
                section(R.string.Playlists_Title, own, collection, images, onOpenList, firstList, first)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.section(
    heading: Int,
    lists: List<TrackList>,
    collection: Collection,
    images: ImageLoader,
    onOpenList: (TrackList) -> Unit,
    firstList: TrackList,
    first: FocusRequester,
) {
    if (lists.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }, key = "heading:$heading") {
        Box(Modifier.padding(top = 8.px)) { Heading(stringResource(heading)) }
    }
    items(lists, key = { it.id }) { list ->
        WallCard(
            art = { ListPicture(list, collection, images, drift = false, keepFrame = false, modifier = Modifier.fillMaxSize()) },
            title = listName(list.name),
            line = list.folder.joinToString(" / ").ifBlank { null } ?: stringResource(R.string.Home_Songs, list.tracks.size.toString()),
            explicit = list.tracks.any { it.tags?.advisory == EXPLICIT },
            onClick = { onOpenList(list) },
            modifier = if (list === firstList) Modifier.focusRequester(first) else Modifier,
        )
    }
}
