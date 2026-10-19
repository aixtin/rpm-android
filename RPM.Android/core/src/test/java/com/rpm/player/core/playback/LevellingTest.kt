package com.rpm.player.core.playback

import androidx.media3.common.C
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10

/**
 * Volume levelling: the gain maths, pinned to the desktop's own cases (RPM.Core.Tests/ReplayGainTests.cs),
 * which mode uses which reading (NormalizationTests.cs), when a queue is a record, and what the sink does
 * to samples.
 */
class LevellingTest {

    // ── the gain, as the desktop's ReplayGainTests ──

    @Test
    fun A_loud_master_is_turned_down_and_a_quiet_one_up() {
        assertEquals(-9.0, ReplayGain.gainDb(-9.0), 1e-6)
        assertEquals(5.0, ReplayGain.gainDb(-23.0), 1e-6)
        assertEquals(0.0, ReplayGain.gainDb(-18.0), 1e-6)
    }

    @Test
    fun An_unreadable_loudness_gives_no_gain() {
        assertEquals(0.0, ReplayGain.gainDb(Double.NEGATIVE_INFINITY), 1e-6)
        assertEquals(0.0, ReplayGain.of(Double.NEGATIVE_INFINITY, 0.5), 1e-6)
    }

    @Test
    fun A_track_with_headroom_to_spare_keeps_the_gain_it_asked_for() {
        assertEquals(6.0, ReplayGain.clamp(6.0, truePeak = 0.1), 1e-6)
    }

    @Test
    fun A_boost_is_cut_back_to_the_ceiling() {
        val gain = ReplayGain.clamp(6.0, truePeak = 1.0)
        assertEquals(ReplayGain.PEAK_CEILING_DB, gain, 1e-6)
        assertTrue(20 * log10(ReplayGain.linear(gain).toDouble()) <= ReplayGain.PEAK_CEILING_DB + 1e-6)
    }

    @Test
    fun An_attenuation_is_never_turned_into_a_boost() {
        val gain = ReplayGain.of(lufs = -6.0, truePeak = 1.0)
        assertEquals(-12.0, gain, 1e-6)
    }

    @Test
    fun A_wild_reading_cannot_move_a_track_further_than_the_limit() {
        assertEquals(ReplayGain.MAX_GAIN_DB, ReplayGain.of(-60.0, truePeak = 0.001), 1e-6)
        assertEquals(ReplayGain.MAX_GAIN_DB, ReplayGain.of(-90.0, truePeak = 0.001), 1e-6)
    }

    @Test
    fun A_missing_peak_does_not_stop_the_gain() {
        assertEquals(5.0, ReplayGain.clamp(5.0, truePeak = 0.0), 1e-6)
    }

    @Test
    fun No_gain_is_a_multiplier_of_one() {
        assertEquals(1.0f, ReplayGain.linear(0.0), 1e-6f)
        assertEquals(2.0f, ReplayGain.linear(6.0206), 1e-3f)
        assertEquals(0.5f, ReplayGain.linear(-6.0206), 1e-3f)
    }

    @Test
    fun Album_loudness_averages_the_energy_rather_than_the_decibels() {
        val album = ReplayGain.albumLufs(listOf(-10.0 to 200.0, -20.0 to 200.0))
        assertTrue("got $album", album > -13.5 && album < -12.5)
    }

    @Test
    fun A_short_track_counts_for_less_than_a_long_one() {
        val even = ReplayGain.albumLufs(listOf(-8.0 to 300.0, -20.0 to 300.0))
        val lopsided = ReplayGain.albumLufs(listOf(-8.0 to 300.0, -20.0 to 10.0))
        assertTrue(lopsided > even)
    }

    @Test
    fun An_album_with_nothing_measured_has_no_loudness() {
        assertTrue(ReplayGain.albumLufs(emptyList()) == Double.NEGATIVE_INFINITY)
    }

    @Test
    fun Unmeasured_tracks_do_not_drag_an_album_down() {
        val all = ReplayGain.albumLufs(listOf(-12.0 to 200.0, -12.0 to 200.0))
        val half = ReplayGain.albumLufs(listOf(-12.0 to 200.0, Double.NEGATIVE_INFINITY to 200.0))
        assertEquals(all, half, 1e-6)
    }

    // ── which reading, as the desktop's NormalizationTests ──

    private val loudTrack = Level(-8.0, null)
    private val quietAlbum = Level(-14.0, null)

    private fun stream(inAlbumOrder: Boolean = true, classical: Boolean = false, track: Level = loudTrack, album: Level = quietAlbum) =
        StreamLoudness(track, album, inAlbumOrder, classical)

    @Test
    fun Off_levels_nothing() {
        assertEquals(0.0, Normalization.gainDb(LevelMode.Off, includeClassical = true, stream()), 1e-9)
    }

    @Test
    fun Album_mode_in_album_order_uses_the_album() {
        assertEquals(-4.0, Normalization.gainDb(LevelMode.Album, includeClassical = false, stream()), 1e-9)
    }

    @Test
    fun Album_mode_out_of_order_uses_the_track() {
        assertEquals(-10.0, Normalization.gainDb(LevelMode.Album, includeClassical = false, stream(inAlbumOrder = false)), 1e-9)
    }

    @Test
    fun Album_mode_without_an_album_reading_falls_back_to_the_track() {
        assertEquals(-10.0, Normalization.gainDb(LevelMode.Album, includeClassical = false, stream(album = Level.UNKNOWN)), 1e-9)
    }

    @Test
    fun Track_mode_uses_the_track_even_on_a_record() {
        assertEquals(-10.0, Normalization.gainDb(LevelMode.Track, includeClassical = false, stream()), 1e-9)
    }

    @Test
    fun An_unmeasured_track_is_left_alone() {
        assertEquals(0.0, Normalization.gainDb(LevelMode.Track, includeClassical = false, stream(track = Level.UNKNOWN)), 1e-9)
    }

    @Test
    fun A_classical_record_is_left_alone_unless_classical_is_levelled_too() {
        assertEquals(0.0, Normalization.gainDb(LevelMode.Album, includeClassical = false, stream(classical = true)), 1e-9)
        assertEquals(-4.0, Normalization.gainDb(LevelMode.Album, includeClassical = true, stream(classical = true)), 1e-9)
    }

    // ── when a queue is a record ──

    private fun reading(album: String, lufs: Double?, seconds: Double = 200.0, peak: Double? = null, station: Boolean = false) =
        QueuedReading(album, "Artist", station, Level(lufs, peak), seconds)

    @Test
    fun One_album_in_its_tracks_is_a_record_with_the_mean_of_their_energy_and_the_loudest_peak() {
        val answer = QueueRecord.of(listOf(reading("A", -10.0, peak = 0.9), reading("A", -20.0, peak = 0.5)))
        assertTrue(answer.inAlbumOrder)
        assertEquals(ReplayGain.albumLufs(listOf(-10.0 to 200.0, -20.0 to 200.0)), answer.album.lufs!!, 1e-9)
        assertEquals(0.9, answer.album.truePeak!!, 1e-9)
    }

    @Test
    fun Tracks_from_two_albums_are_not_a_record() {
        val answer = QueueRecord.of(listOf(reading("A", -10.0), reading("B", -12.0)))
        assertFalse(answer.inAlbumOrder)
        assertFalse(answer.album.known)
    }

    @Test
    fun A_queue_with_a_station_is_not_a_record() {
        assertFalse(QueueRecord.of(listOf(reading("", null, station = true))).inAlbumOrder)
    }

    @Test
    fun A_record_nobody_measured_has_no_album_reading() {
        val answer = QueueRecord.of(listOf(reading("A", null), reading("A", null)))
        assertTrue(answer.inAlbumOrder)
        assertFalse(answer.album.known)
    }

    // ── what the sink does to samples ──

    private fun pcm16(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putShort(it) }; flip() }

    private fun shorts(buffer: ByteBuffer): ShortArray {
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        b.position(0)
        return ShortArray(b.remaining() / 2) { b.short }
    }

    @Test
    fun Sixteen_bit_samples_are_multiplied_and_held_to_full_scale() {
        val from = pcm16(1000, -1000, 30000, -30000)
        val to = ByteBuffer.allocate(8)
        val end = Pcm.level(from, to, C.ENCODING_PCM_16BIT, channels = 2, gain = 2f, target = 2f, step = 0.1f)
        assertEquals(2f, end, 0f)
        assertArrayEquals(shortArrayOf(2000, -2000, 32767, -32768), shorts(to))
        assertEquals("the source is not moved", 0, from.position())
    }

    @Test
    fun The_gain_moves_towards_its_target_a_step_a_frame() {
        val from = pcm16(1000, 1000, 1000, 1000, 1000, 1000)
        val to = ByteBuffer.allocate(12)
        val end = Pcm.level(from, to, C.ENCODING_PCM_16BIT, channels = 2, gain = 1f, target = 0.5f, step = 0.2f)
        assertEquals(0.5f, end, 1e-6f)
        // Frame by frame: 0.8, 0.6, 0.5, both channels of a frame alike.
        assertArrayEquals(shortArrayOf(800, 800, 600, 600, 500, 500), shorts(to))
    }

    @Test
    fun Float_samples_are_held_to_one() {
        val from = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply { putFloat(0.75f); putFloat(-0.25f); flip() }
        val to = ByteBuffer.allocate(8)
        Pcm.level(from, to, C.ENCODING_PCM_FLOAT, channels = 1, gain = 2f, target = 2f, step = 0.1f)
        val out = to.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1f, out.float, 0f)
        assertEquals(-0.5f, out.float, 1e-6f)
    }

    @Test
    fun Twenty_four_bit_samples_keep_their_sign() {
        val from = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        fun put24(v: Int) { from.put(v.toByte()); from.put((v shr 8).toByte()); from.put((v shr 16).toByte()) }
        put24(-100_000)
        put24(5_000_000)
        from.flip()
        val to = ByteBuffer.allocate(6)
        Pcm.level(from, to, C.ENCODING_PCM_24BIT, channels = 1, gain = 2f, target = 2f, step = 0.1f)
        val out = to.duplicate()
        fun get24(): Int { val b0 = out.get().toInt() and 0xFF; val b1 = out.get().toInt() and 0xFF; val b2 = out.get().toInt(); return (b2 shl 16) or (b1 shl 8) or b0 }
        assertEquals(-200_000, get24())
        assertEquals(8_388_607, get24())
    }

    @Test
    fun Only_pcm_is_levelled() {
        assertTrue(Pcm.levels(C.ENCODING_PCM_16BIT))
        assertTrue(Pcm.levels(C.ENCODING_PCM_FLOAT))
        assertFalse(Pcm.levels(C.ENCODING_AC3))
        assertFalse(Pcm.levels(C.ENCODING_INVALID))
    }
}
