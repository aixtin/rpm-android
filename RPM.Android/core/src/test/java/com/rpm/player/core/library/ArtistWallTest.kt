package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TV's port of the artists wall: the same cases that the desktop's ArtistWallTests and the phone's tests pin. */
class ArtistWallTest {

    private fun row(album: String, albumArtist: String?, artist: String?, tracks: Int = 1) = ArtistCreditRow(album, albumArtist, artist, tracks)

    private fun find(wall: List<ArtistWallItem>, name: String) = wall.single { it.name.equals(name, ignoreCase = true) }

    @Test fun anOrdinaryAlbumIsOneArtistWithOneRecord() {
        val wall = ArtistWall.build(listOf(row("ABBA/Arrival", "ABBA", "ABBA", 10)))
        val abba = wall.single()
        assertEquals("ABBA", abba.name)
        assertEquals(listOf("ABBA/Arrival"), abba.albums)
        assertTrue(abba.together.isEmpty())
        assertTrue(abba.appearances.isEmpty())
        assertEquals(10, abba.tracks)
    }

    @Test fun aGuestWithNoRecordOfTheirOwnGetsNoCardButIsOnTheRecord() {
        val wall = ArtistWall.build(
            listOf(
                row("CH/18 Months", "Calvin Harris", "Calvin Harris", 12),
                row("CH/18 Months", "Calvin Harris", "Calvin Harris feat. Ellie Goulding", 1),
            ),
        )
        assertEquals(listOf("Calvin Harris"), wall.map { it.name })
    }

    @Test fun aGuestWithARecordOfTheirOwnHasTheOtherRecordAsAnAppearance() {
        val wall = ArtistWall.build(
            listOf(
                row("CH/18 Months", "Calvin Harris", "Calvin Harris feat. Ellie Goulding", 1),
                row("EG/Lights", "Ellie Goulding", "Ellie Goulding", 10),
            ),
        )
        val ellie = find(wall, "Ellie Goulding")
        assertEquals(listOf("EG/Lights"), ellie.albums)
        assertEquals(listOf("CH/18 Months"), ellie.appearances)
    }

    @Test fun aBandFiledUnderItsNameIsNotSplit() {
        val wall = ArtistWall.build(listOf(row("EWF/Gratitude", "Earth, Wind & Fire", "Earth, Wind & Fire", 9)))
        assertEquals(listOf("Earth, Wind & Fire"), wall.map { it.name })
    }

    @Test fun aRecordTwoKnownActsMadeIsOnBothPagesAsTogether() {
        val wall = ArtistWall.build(
            listOf(
                row("KW/Graduation", "Kanye West", "Kanye West", 10),
                row("JZ/Blueprint", "Jay-Z", "Jay-Z", 10),
                row("Both/Watch the Throne", "Jay-Z & Kanye West", "Jay-Z & Kanye West", 12),
            ),
        )
        assertEquals(listOf("Both/Watch the Throne"), find(wall, "Kanye West").together)
        assertEquals(listOf("Both/Watch the Throne"), find(wall, "Jay-Z").together)
        assertFalse(wall.any { it.name == "Jay-Z & Kanye West" })
    }

    @Test fun everySpellingOfTheCompilationMarkerIsOneAct() {
        val wall = ArtistWall.build(
            listOf(
                row("VA/Now 1", "Various Artists", "Blur", 1),
                row("VA/Now 2", "Various", "Oasis", 1),
            ),
        )
        val compilations = find(wall, ArtistWall.COMPILATIONS)
        assertEquals(listOf("VA/Now 1", "VA/Now 2"), compilations.albums)
    }

    @Test fun nameOrderPutsTheHashBucketLast() {
        val wall = ArtistWall.build(
            listOf(row("2/a", "2Pac", "2Pac"), row("B/a", "Björk", "Björk"), row("A/a", "abba", "abba")),
        )
        assertEquals(listOf("abba", "Björk", "2Pac"), wall.map { it.name })
    }

    @Test fun anInitialFoldsItsAccentAndADigitFilesUnderTheHash() {
        assertEquals('S', ArtistWall.initial("Sigur Rós"))
        assertEquals('O', ArtistWall.initial("Ólafur Arnalds"))
        assertEquals('#', ArtistWall.initial("2Pac"))
    }

    @Test fun aCreditNamesAnActWholeOrInPart() {
        assertTrue(ArtistWall.names("Calvin Harris feat. Ellie Goulding", "Ellie Goulding"))
        assertTrue(ArtistWall.names("Jay-Z with Kanye West", "Kanye West"))
        assertFalse(ArtistWall.names(null, "Anybody"))
    }

    @Test fun rowsCountEachCreditOnAnAlbumOnce() {
        val album = CloudAlbum(
            "folder:a/b", "B", "A",
            listOf(
                CloudTrack(rpmId = "1", artist = "A"),
                CloudTrack(rpmId = "2", artist = "A"),
                CloudTrack(rpmId = "3", artist = "A feat. C"),
            ),
        )
        val rows = ArtistWall.rows(listOf(album))
        assertEquals(listOf(row("folder:a/b", "A", "A", 2), row("folder:a/b", "A", "A feat. C", 1)), rows)
    }

    @Test fun anAlbumNoTrackOfWhichIsCreditedIsStillFiledUnderItsArtist() {
        val album = CloudAlbum("folder:a/b", "B", "A", listOf(CloudTrack(rpmId = "1", tags = CloudTrackTags())))
        assertEquals(listOf(row("folder:a/b", "A", null, 1)), ArtistWall.rows(listOf(album)))
    }

    @Test fun theOrdersFallBackToTheName() {
        val a = ArtistWallItem("A", listOf("a1"), emptyList(), emptyList(), 3)
        val b = ArtistWallItem("B", listOf("b1", "b2"), emptyList(), emptyList(), 30)
        val c = ArtistWallItem("C", listOf("c1"), emptyList(), emptyList(), 3)
        val albums = mapOf(
            "a1" to CloudAlbum("a1", "", "A", listOf(CloudTrack(rpmId = "1", addedAt = 10))),
            "b1" to CloudAlbum("b1", "", "B", listOf(CloudTrack(rpmId = "2", addedAt = 5))),
            "b2" to CloudAlbum("b2", "", "B", listOf(CloudTrack(rpmId = "3"))),
            "c1" to CloudAlbum("c1", "", "C", listOf(CloudTrack(rpmId = "4"))),
        )
        assertEquals(listOf("B", "A", "C"), ArtistSort.by(listOf(c, a, b), ArtistOrdering.Albums, albums).map { it.name })
        assertEquals(listOf("B", "A", "C"), ArtistSort.by(listOf(c, a, b), ArtistOrdering.Tracks, albums).map { it.name })
        // Undated ones come last, not wherever a zero would fall.
        assertEquals(listOf("A", "B", "C"), ArtistSort.by(listOf(c, b, a), ArtistOrdering.Added, albums).map { it.name })
    }

    /**
     * A name with a comma in it, held by a collection that also has him as a guest (the desktop's
     * A_name_with_a_comma_in_it_survives_being_a_guest_on_somebody_elses_record). The guest credits must
     * not cut him in half. If they did, "Tyler" and "The Creator" would be two cards: both halves would go
     * into the names the collaboration test reads, and his own credit would then pass as two people.
     */
    @Test fun aNameWithACommaInItSurvivesBeingAGuestOnSomebodyElsesRecord() {
        val wall = ArtistWall.build(
            listOf(
                row("Tyler, The Creator/IGOR", "Tyler, The Creator", "Tyler, The Creator", 12),
                row("A\$AP Rocky/Testing", "A\$AP Rocky", "A\$AP Rocky", 15),
                row("A\$AP Rocky/Testing", "A\$AP Rocky", "A\$AP Rocky; Tyler, The Creator; Jozzy", 1),
                row("The Weeknd/Hurry Up Tomorrow", "The Weeknd", "The Weeknd", 22),
                row("The Weeknd/Hurry Up Tomorrow", "The Weeknd", "The Weeknd, Tyler, The Creator", 1),
            ),
        )

        assertEquals(listOf("A\$AP Rocky", "The Weeknd", "Tyler, The Creator"), wall.map { it.name }.sorted())
        val tyler = find(wall, "Tyler, The Creator")
        assertEquals(listOf("Tyler, The Creator/IGOR"), tyler.albums)
        assertTrue(tyler.together.isEmpty())   // a guest verse is not a record the two of them made
        assertEquals(listOf("A\$AP Rocky/Testing", "The Weeknd/Hurry Up Tomorrow"), tyler.appearances)
    }

    /** The same rejoin for a band: the guest credit is cut into three and put back together, because the
     * collection files a record under exactly that name. */
    @Test fun aBandNamedWithSeparatorsIsRejoinedInsideALongerCredit() {
        val wall = ArtistWall.build(
            listOf(
                row("Earth, Wind & Fire/I Am", "Earth, Wind & Fire", "Earth, Wind & Fire", 9),
                row("Diana Ross/Diana", "Diana Ross", "Diana Ross", 10),
                row("Diana Ross/Diana", "Diana Ross", "Diana Ross, Earth, Wind & Fire", 1),
            ),
        )

        assertEquals(listOf("Diana Ross", "Earth, Wind & Fire"), wall.map { it.name }.sorted())
        assertEquals(listOf("Diana Ross/Diana"), find(wall, "Earth, Wind & Fire").appearances)
    }

    /** Nothing is rejoined that the collection files nothing under: without his own record, the credit is
     * still cut where the separators are. */
    @Test fun aCreditTheCollectionFilesNothingUnderIsStillSplit() {
        val wall = ArtistWall.build(
            listOf(
                row("A\$AP Rocky/Testing", "A\$AP Rocky", "A\$AP Rocky", 15),
                row("A\$AP Rocky/Testing", "A\$AP Rocky", "A\$AP Rocky; Tyler, The Creator", 1),
            ),
        )

        assertEquals(listOf("A\$AP Rocky"), wall.map { it.name })
    }

    /** The grouped split is the flat split cut at "feat". */
    @Test fun theGroupedSplitIsTheFlatSplitCutAtTheGuestMarker() {
        assertEquals(listOf(listOf("Tyler", "The Creator"), listOf("Frank Ocean")), ArtistCredit.splitGuests("Tyler, The Creator feat. Frank Ocean"))
        assertEquals(ArtistCredit.split("Ann & Bob feat. Cal, Dee"), ArtistCredit.splitGuests("Ann & Bob feat. Cal, Dee").flatten())
    }
}
