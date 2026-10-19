package com.rpm.player.core.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTest {

    private fun station(uuid: String, name: String, stream: String, country: String = "NL", bitrate: Int = 128, reachable: Boolean = true) =
        RadioStation(uuid = uuid, name = name, streamUrl = stream, countryCode = country, bitrateKbps = bitrate, reachable = reachable)

    @Test
    fun `clean takes off the strapline and the codec words`() {
        assertEquals("Radio 538", RadioNames.clean("Radio 538 - Hitzone #MP3 128k"))
        assertEquals("SomaFM Groove Salad", RadioNames.clean("SomaFM Groove Salad (128k AAC)"))
        assertEquals("Jazz FM", RadioNames.clean("Jazz FM 320 kbps"))
    }

    @Test
    fun `clean keeps a name that is only codec words`() {
        assertEquals("MP3 128k", RadioNames.clean("MP3 128k"))
    }

    @Test
    fun `fold drops filler words and the country after the first word`() {
        assertEquals("npo2", RadioNames.fold("NPO Radio 2", "NL"))
        assertEquals("qmusic", RadioNames.fold("Qmusic Nederland", "NL"))
        assertEquals(RadioNames.fold("Radio Veronica", "NL"), RadioNames.fold("Veronica (AAC 64k)", "NL"))
    }

    @Test
    fun `one per station joins entries with one name or one stream, and keeps the best stream`() {
        val low = station("a", "NPO Radio 2", "https://icecast.omroep.nl/radio2-sb-mp3", bitrate = 96)
        val high = station("b", "NPO Radio 2 (MP3 192k)", "https://icecast.omroep.nl/radio2-bb-mp3", bitrate = 192)
        val sameStream = station("c", "Radio Twee", "https://www.icecast.omroep.nl/radio2-sb-mp3/", bitrate = 64)
        val other = station("d", "Qmusic", "https://stream.qmusic.nl/qmusic/mp3")

        val kept = RadioResults.onePerStation(listOf(low, other, high, sameStream))

        assertEquals(listOf("b", "d"), kept.map { it.uuid })
        assertEquals(setOf("a", "c"), kept[0].alsoFiledAs.toSet())
    }

    @Test
    fun `one per station prefers a reachable stream to a faster one`() {
        val dead = station("a", "Sky Radio", "https://a.example/sky", bitrate = 320, reachable = false)
        val live = station("b", "Sky Radio", "https://b.example/sky", bitrate = 64)
        assertEquals("b", RadioResults.onePerStation(listOf(dead, live)).single().uuid)
    }

    @Test
    fun `the same name in two countries is two stations`() {
        val nl = station("a", "Radio 1", "https://a.example/1", country = "NL")
        val be = station("b", "Radio 1", "https://b.example/1", country = "BE")
        assertEquals(2, RadioResults.onePerStation(listOf(nl, be)).size)
    }

    @Test
    fun `a station is hidden only when it and every entry folded into it are hidden`() {
        val folded = station("a", "X", "https://x.example").copy(alsoFiledAs = listOf("b"))
        assertFalse(StationRules(setOf("a"), "", emptyMap(), StationCards.NONE).hides(folded))
        assertTrue(StationRules(setOf("a", "b"), "", emptyMap(), StationCards.NONE).hides(folded))
    }

    @Test
    fun `split is on the earliest spaced separator, so a hyphen inside a name survives`() {
        assertEquals("Jay-Z" to "Empire State of Mind", Icy.split("Jay-Z - Empire State of Mind"))
        assertEquals("A" to "B - C", Icy.split("A - B - C"))
        assertEquals(null to "- Live from Paris", Icy.split("- Live from Paris"))
        assertEquals(null to "Just a title", Icy.split("Just a title"))
    }

    @Test
    fun `said takes off the station's own name and reads ads and their end`() {
        assertEquals(Icy.Said.Song(null, "Dit is Jorien!"), Icy.said("NPO Radio 2 - Dit is Jorien!", "NPO Radio 2"))
        assertNull(Icy.said("NPO Radio 2", "NPO Radio 2"))
        assertEquals(Icy.Said.AdBreak, Icy.said("Ad Break", "Qmusic"))
        assertEquals(Icy.Said.AdBreak, Icy.said("Werbung", "Qmusic"))
        assertNull(Icy.said("spot block end", "Qmusic"))
        assertNull(Icy.said("  ", "Qmusic"))
    }

    @Test
    fun `said reads the Triton and listed field shapes`() {
        assertEquals(
            Icy.Said.Song("Daft Punk", "One More Time"),
            Icy.said("Daft Punk - text=\"One More Time\" song_spot=\"M\"", "Some FM"),
        )
        // T is the station talking about itself: its text alone, not a song.
        assertEquals(Icy.Said.Song(null, "Weather and traffic"), Icy.said("Some FM - text=\"Weather and traffic\" song_spot=\"T\"", "Other"))
        assertEquals(
            Icy.Said.Song("Adele", "Hello"),
            Icy.said("title=\"Hello\",artist=\"Adele\",url=\"song_spot=\\\"M\\\"\"", "Some FM"),
        )
    }
}
