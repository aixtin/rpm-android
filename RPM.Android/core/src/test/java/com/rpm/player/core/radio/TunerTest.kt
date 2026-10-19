package com.rpm.player.core.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerTest {

    private fun station(
        uuid: String,
        name: String,
        codec: String? = "MP3",
        tags: List<String> = emptyList(),
        votes: Int = 0,
        bitrate: Int = 128,
        homepage: String? = null,
    ) = RadioStation(uuid = uuid, name = name, streamUrl = "https://$uuid.example/stream", codec = codec, tags = tags, votes = votes, bitrateKbps = bitrate, homepage = homepage)

    private val settings = TunerSettings(text = "", country = "NL", sieve = StationSieve.OFF, order = StationOrder.Listed, favoritesOnly = false)

    @Test
    fun `call signs follow the desktop's rules`() {
        assertEquals("NPO1", StationSign.of("NPO Radio 1"))
        assertEquals("538", StationSign.of("Radio 538"))
        assertEquals("BWS", StationSign.of("BBC World Service"))
        assertEquals("ACR", StationSign.of("Arrow Classic Rock"))
        assertEquals("JOE", StationSign.of("Joe"))
        assertEquals("INTE", StationSign.of("Intense Radio"))
        assertEquals("", StationSign.of("  "))
    }

    @Test
    fun `the format filter folds the codec's other spellings`() {
        val sieve = StationSieve.OFF.copy(format = "AAC+")
        assertTrue(sieve.keeps(station("a", "A", codec = "AACP")))
        assertTrue(sieve.keeps(station("b", "B", codec = "HE-AAC")))
        assertFalse(sieve.keeps(station("c", "C", codec = "AAC")))
        assertFalse(sieve.keeps(station("d", "D", codec = null)))
    }

    @Test
    fun `the genre filter maps tags through the table, so reggaeton is Latin and not Reggae`() {
        val reggaeton = station("a", "A", tags = listOf("reggaeton"))
        assertTrue(StationSieve.OFF.copy(genre = "Latin").keeps(reggaeton))
        assertFalse(StationSieve.OFF.copy(genre = "Reggae").keeps(reggaeton))
    }

    @Test
    fun `an ad-free tag settles it before a broadcaster's name`() {
        assertTrue(RadioAds.carriesAds(emptyList(), "NPO Radio 2", null))
        assertTrue(RadioAds.carriesAds(emptyList(), "Some station", "https://www.wdr.de/radio"))
        assertFalse(RadioAds.carriesAds(listOf("non-commercial"), "NPO Radio 2", null))
        assertFalse(RadioAds.carriesAds(listOf("jazz"), "Jazz Club", null))
    }

    @Test
    fun `every order ends in the name`() {
        val a = station("1", "Beta", votes = 5)
        val b = station("2", "alpha", votes = 5)
        val c = station("3", "Gamma", votes = 9)
        assertEquals(listOf("3", "2", "1"), StationOrder.Votes.sort(listOf(a, b, c)).map { it.uuid })
        assertEquals(listOf("2", "1", "3"), StationOrder.NameUp.sort(listOf(a, b, c)).map { it.uuid })
        assertEquals(listOf("1", "2", "3"), StationOrder.Listed.sort(listOf(a, b, c)).map { it.uuid })
    }

    @Test
    fun `the list says why it is empty`() {
        val rock = station("a", "Rock FM", tags = listOf("rock"))
        val listed = TunerAnswer.Listed(listOf(rock))

        assertEquals(TunerList.Empty.NoFavorites, Tuner.listOf(settings.copy(favoritesOnly = true), listed, emptyList(), StationRules.NONE).empty)
        assertEquals(TunerList.Empty.NoFilterMatch, Tuner.listOf(settings.copy(sieve = StationSieve.OFF.copy(genre = "Jazz")), listed, emptyList(), StationRules.NONE).empty)
        assertEquals(TunerList.Empty.NoResults, Tuner.listOf(settings, TunerAnswer.Listed(emptyList()), emptyList(), StationRules.NONE).empty)
        assertNull(Tuner.listOf(settings, listed, emptyList(), StationRules.NONE).empty)
    }

    @Test
    fun `nothing is said while asking, and a failed directory is said even beside saved stations`() {
        val asking = Tuner.listOf(settings, TunerAnswer.Asking, emptyList(), StationRules.NONE)
        assertTrue(asking.asking)
        assertNull(asking.empty)

        val saved = station("a", "Saved")
        val down = Tuner.listOf(settings.copy(favoritesOnly = true), TunerAnswer.DirectoryDown, listOf(saved), StationRules.NONE)
        assertTrue(down.directoryDown)
        assertEquals(listOf("a"), down.stations.map { it.uuid })
    }

    @Test
    fun `hidden stations leave the list before the filters are counted`() {
        val hidden = station("a", "Hidden", tags = listOf("rock"))
        val list = Tuner.listOf(settings, TunerAnswer.Listed(listOf(hidden)), emptyList(), StationRules(setOf("a"), "", emptyMap(), StationCards.NONE))
        assertEquals(TunerList.Empty.NoResults, list.empty)
    }

    @Test
    fun `the country menu keeps the 80 largest, one row per code, by name`() {
        val many = (0 until 100).map { RadioCountry("Country $it", "C${'A' + it % 26}", stationCount = it) } +
            RadioCountry("The Netherlands", "NL", 50) + RadioCountry("Netherlands", "NL", 900)
        val picked = Tuner.forPicker(many)
        assertTrue(picked.size <= 80)
        assertEquals(1, picked.count { it.code == "NL" })
        assertEquals(950, picked.single { it.code == "NL" }.stationCount)
    }
}
