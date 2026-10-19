package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordFactsTest {

    private fun track(year: Int = 0, ext: String? = null, bits: Int = 0, rate: Int = 0, kbps: Int = 0) =
        CloudTrack(rpmId = "t", tags = CloudTrackTags(year = year, extension = ext, bitsPerSample = bits, sampleRate = rate, bitrateKbps = kbps))

    @Test
    fun theYearMostTracksGive() {
        assertEquals(2013, RecordFacts.year(listOf(track(2013), track(2013), track(2014), track(0))))
    }

    @Test
    fun noYearWhenNoTrackGivesOne() {
        assertNull(RecordFacts.year(listOf(track(0))))
    }

    @Test
    fun aLosslessRecordShowsBitDepthAndRate() {
        assertEquals(RecordFormat("FLAC", "24/96"), RecordFacts.format(listOf(track(ext = ".flac", bits = 24, rate = 96000))))
        assertEquals(RecordFormat("FLAC", "16/44.1"), RecordFacts.format(listOf(track(ext = "flac", bits = 16, rate = 44100))))
    }

    @Test
    fun aLossyRecordShowsItsBitrate() {
        assertEquals(RecordFormat("MP3", "320 kbps"), RecordFacts.format(listOf(track(ext = ".mp3", kbps = 320))))
    }

    @Test
    fun theCommonestFormatWins() {
        val tracks = listOf(track(ext = ".flac", bits = 16, rate = 44100), track(ext = ".flac", bits = 16, rate = 44100), track(ext = ".mp3", kbps = 256))
        assertEquals(RecordFormat("FLAC", "16/44.1"), RecordFacts.format(tracks))
    }

    @Test
    fun noFormatWithoutAnExtension() {
        assertNull(RecordFacts.format(listOf(track(bits = 16, rate = 44100))))
        assertEquals(RecordFormat("WAV", null), RecordFacts.format(listOf(track(ext = ".wav"))))
    }
}
