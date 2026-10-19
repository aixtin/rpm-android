package com.rpm.player.core.room

import androidx.media3.common.C
import com.rpm.player.core.playback.AudioTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

class RoomTest {

    @Test fun theFftPutsASineInItsOwnBin() {
        val n = 1024
        val re = DoubleArray(n) { sin(2 * PI * 10 * it / n) }
        val im = DoubleArray(n)
        Fft.transform(re, im)
        val loudest = (0 until n / 2).maxBy { hypot(re[it], im[it]) }
        assertEquals(10, loudest)
    }

    @Test fun aSleeveWithNoColourGivesTheAppsOwnRoom() {
        assertEquals(RoomPalette.FALLBACK, RoomPalette.from(null, null, null))
    }

    @Test fun aThirdColourBecomesTheAccentWhereTintAndGlowAreOneHue() {
        val gold = Rgb.of(200, 160, 40)
        val darkerGold = Rgb.of(150, 120, 30)
        val red = Rgb.of(200, 30, 30)
        val room = RoomPalette.from(tint = gold, glow = darkerGold, shade = red)
        assertEquals(RoomPalette.vivid(red, saturate = 1.5f, lift = 0.90f), room.glow)
        // Spent on the glow, so the shade falls back to grey and does not repeat it.
        assertEquals(Rgb(0.70f, 0.70f, 0.70f), room.shade)
    }

    @Test fun theDominantColourIgnoresNearBlackAndNearWhite() {
        val pixels = ArrayList<Byte>()
        fun add(r: Int, g: Int, b: Int, times: Int) = repeat(times) { pixels += listOf(r.toByte(), g.toByte(), b.toByte(), -1) }
        add(0, 0, 0, 50)
        add(250, 250, 250, 50)
        add(30, 90, 200, 10)
        val dominant = CoverColor.dominant(pixels.toByteArray())!!
        assertEquals(Rgb.of(30, 90, 200), dominant)
    }

    private fun ramp(count: Int, first: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.nativeOrder())
        repeat(count) { buffer.putShort((first + it).toShort()) }
        buffer.flip()
        return buffer
    }

    /** A clock that stands still, so a playing tap's heard position is the one last noted. */
    private val stopped: () -> Long = { 0L }

    /** Centred on the heard sample, so the Hann-shaped analysis weighs most what is heard now. */
    @Test fun theTapReadsTheSamplesCentredWhereTheListenerIs() {
        val tap = AudioTap(stopped)
        tap.configure(1000)
        tap.write(ramp(1000, 0), presentationTimeUs = 0, encoding = C.ENCODING_PCM_16BIT, channels = 1)
        tap.setPlaying(true)
        tap.noteHeard(500_000)
        val window = FloatArray(100)
        assertEquals(1000, tap.read(window))
        assertEquals(450f / 32768f, window.first(), 1e-6f)
        assertEquals(549f / 32768f, window.last(), 1e-6f)
    }

    @Test fun aPausedTapHasNothingToReadSoTheBarsFall() {
        val tap = AudioTap(stopped)
        tap.configure(1000)
        tap.write(ramp(1000, 0), presentationTimeUs = 0, encoding = C.ENCODING_PCM_16BIT, channels = 1)
        tap.setPlaying(true)
        tap.noteHeard(500_000)
        assertEquals(1000, tap.read(FloatArray(100)))
        tap.setPlaying(false)
        assertEquals(0, tap.read(FloatArray(100)))
        // Played again, it reads from where it stopped.
        tap.setPlaying(true)
        assertEquals(1000, tap.read(FloatArray(100)))
    }

    @Test fun aFlushLeavesNothingToReadUntilNewAudioArrives() {
        val tap = AudioTap(stopped)
        tap.configure(1000)
        tap.write(ramp(1000, 0), presentationTimeUs = 0, encoding = C.ENCODING_PCM_16BIT, channels = 1)
        tap.noteHeard(500_000)
        tap.flush()
        assertEquals(0, tap.read(FloatArray(100)))
    }

    @Test fun aBufferThatDoesNotFollowOnStartsTheRingAgain() {
        val tap = AudioTap(stopped)
        tap.configure(1000)
        tap.write(ramp(1000, 0), presentationTimeUs = 0, encoding = C.ENCODING_PCM_16BIT, channels = 1)
        // Five seconds later with no flush: the old samples must not be read as if they led up to the new ones.
        tap.write(ramp(1000, 5000), presentationTimeUs = 5_000_000, encoding = C.ENCODING_PCM_16BIT, channels = 1)
        tap.setPlaying(true)
        tap.noteHeard(5_200_000)
        val window = FloatArray(100)
        assertEquals(1000, tap.read(window))
        assertEquals(5150f / 32768f, window.first(), 1e-6f)
        // ...and a position before the new start has nothing behind it.
        tap.noteHeard(4_900_000)
        assertTrue(tap.read(window) == 0)
    }
}
