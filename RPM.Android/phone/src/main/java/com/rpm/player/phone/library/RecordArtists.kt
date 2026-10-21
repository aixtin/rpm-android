package com.rpm.player.phone.library

import com.rpm.player.core.library.ArtistCredit
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.NameMatch
import com.rpm.player.core.library.Walls

/**
 * The artist pages a record's artist line opens: every act whose own records include it, in the order the record's
 * credit names them. It is the iPhone's `ReadArtists.artists(of:in:)`. The index over the artists wall is built
 * once per wall, so a record page asks it without a pass over every act.
 */
class RecordArtists(walls: Walls) {
    private val byOwnAlbum: Map<String, List<ArtistWallItem>> = HashMap<String, MutableList<ArtistWallItem>>().also { index ->
        for (act in walls.artists) for (key in act.own) index.getOrPut(key.lowercase()) { ArrayList() }.add(act)
    }

    fun of(album: CloudAlbum): List<ArtistWallItem> {
        val found = byOwnAlbum[album.key.lowercase()].orEmpty()
        if (found.size < 2) return found
        // The wall is in name order, the credit in the order the record names its acts. An act the credit does not
        // spell out goes last, in wall order.
        val parts = ArtistCredit.splitWide(album.artist)
        return found.withIndex()
            .sortedWith(compareBy({ (_, act) -> parts.indexOfFirst { NameMatch.same(it, act.name) }.let { if (it < 0) parts.size else it } }, { it.index }))
            .map { it.value }
    }
}
