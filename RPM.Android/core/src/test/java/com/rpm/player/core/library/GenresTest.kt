package com.rpm.player.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The tag cases are the phone's GenresTests, which follow `RPM.Core.Tests/GenresTests.cs` case for case:
 * every app files one record under one genre, so they are held to one set of answers. */
class GenresTest {
    private val matches = listOf(
        "Rock" to "Rock",
        "rock" to "Rock",
        "Hip Hop" to "Hip-Hop / Rap",
        "hip-hop" to "Hip-Hop / Rap",
        "Rap" to "Rap",
        "R&B" to "R&B",
        "RnB" to "R&B",
        "Funk / Soul" to "Funk",
        "Soul" to "Soul",
        "Electronic" to "Electronic / Dance",
        "House" to "House",
        "Deep House" to "House",
        "Techno" to "Techno",
        "Indie Rock" to "Indie",
        "Game" to "Video Game",
        "Neo-Soul" to "Soul",
        "neo soul" to "Soul",
        "singer-songwriter, lo-fi, folk, bedroom pop, indie pop, indie rock" to "Indie",
        "singer-songwriter, pop, pop rock, baroque pop, soft rock, rock" to "Pop",
        "Stage & Screen" to "Soundtrack",
        "Folk, World, & Country" to "Folk",
        "Gospel" to "Religious",
        "Religious" to "Religious",
        "Score" to "Soundtrack",
        "International" to "World",
        "Video Game Soundtrack" to "Video Game",
        "Video Game" to "Video Game",
        "R&B / Soul" to "R&B",
        "World / International" to "World",
        "Gospel / Religious" to "Religious",
        "Soundtrack / Score" to "Soundtrack",
        "Post-Punk" to "Punk",
        "Hard Rock" to "Rock",
        "Synth-pop" to "Pop",
        "Death Metal" to "Metal",
        "Contemporary Jazz" to "Jazz",
        "electro house" to "House",
        "trap latino" to "Latin",
        "pop rap" to "Rap",
        "jazz rap" to "Rap",
        "ambient techno" to "Techno",
        "electro-disco" to "Disco",
        "pop metal" to "Metal",
        "reggaeton" to "Latin",
        "Rock | Pop | Folk Rock | Art Rock" to "Rock",
        "Progressive House, Trance, Speed Garage" to "House",
        "cloud rap, hip hop, trap" to "Rap",
        "Punk Rock" to "Punk",
        "Lovers Rock" to "Reggae",
        "Christian Rock" to "Religious",
        "Gospel Soul" to "Religious",
        "Gospel Blues" to "Religious",
        "Latin Rock" to "Latin",
        "Latin Pop" to "Latin",
        "Country Pop" to "Country",
        "Folk Pop" to "Folk",
        "Jazz Pop" to "Jazz",
        "Dream Pop" to "Indie",
        "Bedroom Pop" to "Indie",
        "Jangle Pop" to "Indie",
        "Chamber Pop" to "Indie",
        "Post-Punk Revival" to "Indie",
        "Power Pop" to "Rock",
        "Dance-Pop" to "Pop",
        "Trap Soul" to "R&B",
        "G-Funk" to "Hip-Hop / Rap",
        "Witch House" to "Electronic / Dance",
        "Future Funk" to "Electronic / Dance",
        "Ambient Classical" to "Neo-Classical",
        "Video Game Score" to "Video Game",
        "Desert Blues" to "World",
        "Ethio-Jazz" to "World",
        "Progressive Punk Rock" to "Punk",
        "Experimental Hip Hop" to "Hip-Hop / Rap",
        "Experimental Rock" to "Rock",
        "Cloud Rap" to "Rap",
    )

    @Test fun aTagMatchesTheDesktopsGenre() {
        for ((tag, genre) in matches) assertEquals(tag, genre, Genres.match(tag))
    }

    @Test fun anythingElseMatchesNothing() {
        for (tag in listOf("R", "B", "Non-Music", "Shellac", "Test Pressing", "")) assertNull(tag, Genres.match(tag))
    }

    @Test fun theUnknownShelfMatchesNothing() {
        assertNull(Genres.match("Unknown"))
        assertNull(Genres.match("Other"))
        assertNull(Genres.match("Other / Unknown"))
        assertNull(Genres.match(null))
    }

    @Test fun aGenreIsSpokenByItsFirstWord() {
        assertEquals("hip-hop", Genres.spoken("Hip-Hop / Rap"))
        assertEquals("electronic", Genres.spoken("Electronic / Dance"))
        assertEquals("R&B", Genres.spoken("R&B"))
    }
}
