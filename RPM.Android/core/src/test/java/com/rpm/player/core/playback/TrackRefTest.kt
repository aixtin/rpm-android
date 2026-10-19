package com.rpm.player.core.playback

import com.rpm.player.core.cloud.CloudMoodList
import com.rpm.player.core.cloud.CloudMoodLists
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.Shelf
import com.rpm.player.core.library.Lists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackRefTest {
    private val id = "3f2b6c1e-0000-4000-8000-00000000abcd"
    private val owner = "eu-west-1:6d0f0000-1111-2222-3333-444455556666"

    @Test
    fun `an own track and a borrowed one round trip through the media id`() {
        val own = TrackRef(Shelf.Own, id)
        val borrowed = TrackRef(Shelf.Borrowed(owner), id)
        assertEquals("own:$id", own.mediaId)
        assertEquals(own, TrackRef.parse(own.mediaId))
        assertEquals(borrowed, TrackRef.parse(borrowed.mediaId))
    }

    @Test
    fun `the same track on two shelves is two media ids`() {
        assertNotEquals(TrackRef(Shelf.Own, id).mediaId, TrackRef(Shelf.Borrowed(owner), id).mediaId)
    }

    @Test
    fun `a station, a bare id or a broken id is not a track`() {
        assertNull(TrackRef.parse("station:abc"))
        assertNull(TrackRef.parse(id))
        assertNull(TrackRef.parse("own:"))
        assertNull(TrackRef.parse("shared:$owner:"))
        assertNull(TrackRef.parse("shared::$id"))
        assertNull(TrackRef.parse(""))
    }

    @Test
    fun `a shelf key names its owner`() {
        assertEquals("own", Shelf.Own.key)
        assertEquals("shared:$owner", Shelf.Borrowed(owner).key)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a borrowed shelf must name its owner`() {
        Shelf.Borrowed(" ")
    }

    @Test
    fun `a borrowed collection leaves out the owner's Your mix and listening lists, and keeps the rest`() {
        val tracks = (1..3).map { CloudTrack(rpmId = "t$it", title = "t$it", artist = "A", album = "B", relativePath = "A/B/t$it.flac", addedAt = 0) }
        val byId = tracks.associateBy { it.rpmId }
        val cut = CloudMoodLists(lists = listOf("RightNow", "OnRepeat", "LateNight", "Calm").map { CloudMoodList(kind = it, trackIds = byId.keys.toList()) })
        assertEquals(listOf("RightNow", "OnRepeat", "LateNight", "Calm"), Lists.generated(cut, byId, Shelf.Own).map { it.kind })
        assertEquals(listOf("Calm"), Lists.generated(cut, byId, Shelf.Borrowed(owner)).map { it.kind })
    }
}
