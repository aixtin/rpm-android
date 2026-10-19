package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iOS app's ArtistSongListingTests. */
class ArtistSongListingTest {

    private val record = CloudAlbum(key = "folder:artist/album", album = "Album", artist = "Artist", tracks = emptyList())
    private var made = 0

    private fun song(title: String, year: Int = 0) =
        CloudTrack(rpmId = "$title#${made++}", title = title, artist = "Artist", tags = CloudTrackTags(year = year)) to record

    private fun songs(vararg titles: String) = titles.map { song(it) }

    /** Songs whose titles run Z, Y, X..., so a grouped listing has to put them in order. */
    private fun many(count: Int) = (0 until count).map { song("${'Z' - it % 26} song $it") }

    private fun byLetter(songs: List<Pair<CloudTrack, CloudAlbum>>) = ArtistSongListing(songs, ArtistTrackGrouping.Letter, "No year")
    private fun byYear(songs: List<Pair<CloudTrack, CloudAlbum>>) = ArtistSongListing(songs, ArtistTrackGrouping.Year, "No year")
    private fun headings(listing: ArtistSongListing) = listing.groups.map { it.heading }
    private fun under(listing: ArtistSongListing, heading: String) =
        listing.groups.firstOrNull { it.heading == heading }?.songs?.map { it.first.title.orEmpty() }.orEmpty()

    /** The titles in a group that are not [many]'s filler. */
    private fun named(listing: ArtistSongListing, heading: String) = under(listing, heading).filter { " song " !in it }

    @Test fun twentySongsAreOneGroupWithNoHeadingInTheOrderGiven() {
        val given = many(20)
        val listing = byLetter(given)
        assertEquals(given, listing.songs)
        assertEquals(listOf<String?>(null), headings(listing))
        assertFalse(listing.isGrouped)
    }

    @Test fun twentyOneSongsAreGroupedByLetterInAlphabetOrder() {
        val listing = byLetter(many(21))
        assertTrue(listing.isGrouped)
        val letters = headings(listing).filterNotNull()
        assertEquals(listing.groups.size, letters.size)
        assertEquals(letters.sorted(), letters)
        assertEquals(21, listing.songs.size)
    }

    @Test fun aSongThatStartsWithADigitOrSymbolGoesUnderHashAfterZ() {
        val listing = byLetter(songs("99 Luftballons", "...Baby One More Time", "Zombie") + many(20))
        assertEquals("#", listing.groups.last().heading)
        assertEquals(listOf("99 Luftballons"), listing.groups.last().songs.map { it.first.title })
        // Leading punctuation is skipped, so "...Baby" is a B.
        assertTrue("...Baby One More Time" in under(listing, "B"))
    }

    @Test fun anAccentedTitleGoesUnderItsBaseLetter() {
        val listing = byLetter(songs("Éclair", "Ölmusik") + many(20))
        assertTrue("Éclair" in under(listing, "E"))
        assertTrue("Ölmusik" in under(listing, "O"))
    }

    @Test fun songsInAGroupAreSortedByTitleIgnoringCaseAndPunctuation() {
        val listing = byLetter(songs("angie", "(I Can't Get No) Satisfaction", "Imagine", "Anarchy", "Idioteque") + many(20))
        assertEquals(listOf("Anarchy", "angie"), named(listing, "A"))
        assertEquals(listOf("(I Can't Get No) Satisfaction", "Idioteque", "Imagine"), named(listing, "I"))
    }

    @Test fun twoSongsWithTheSameTitleKeepTheOrderTheyWereGivenIn() {
        val first = song("Hurt")
        val second = song("Hurt")
        val listing = byLetter(listOf(first) + many(20) + listOf(second))
        assertEquals(listOf(first, second), listing.groups.first { it.heading == "H" }.songs.filter { it.first.title == "Hurt" })
    }

    @Test fun aListingNarrowedBelowTheThresholdIsOneListInTheOrderGiven() {
        val given = many(30).take(5)
        assertEquals(given, byLetter(given).songs)
        assertFalse(byLetter(given).isGrouped)
    }

    @Test fun anEmptyListingHasNoGroups() {
        assertTrue(byLetter(emptyList()).groups.isEmpty())
        assertTrue(byYear(emptyList()).songs.isEmpty())
    }

    private fun dated() = listOf(
        song("Beta", 2001), song("Alpha", 2001), song("Old", 1999), song("Undated"), song("Zed", 2010), song("Aye", 2010),
    ) + many(20)

    @Test fun byYearTheNewestYearComesFirstAndSongsWithNoYearLast() =
        assertEquals(listOf("2010", "2001", "1999", "No year"), headings(byYear(dated())))

    @Test fun byYearAGroupKeepsTheOrderTheSongsWereGivenIn() {
        val listing = byYear(dated())
        // Record order, not title order: Zed before Aye, Beta before Alpha.
        assertEquals(listOf("Zed", "Aye"), under(listing, "2010"))
        assertEquals(listOf("Beta", "Alpha"), under(listing, "2001"))
        assertEquals("Undated", under(listing, "No year").first())
    }

    @Test fun aShortListingShowsNoHeadingsWhateverTheGrouping() {
        assertEquals(listOf<String?>(null), headings(byYear(many(5))))
        assertEquals(listOf<String?>("No year"), headings(byYear(many(25))))
    }
}
