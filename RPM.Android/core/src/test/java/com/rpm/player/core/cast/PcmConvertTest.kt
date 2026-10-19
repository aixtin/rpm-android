package com.rpm.player.core.cast

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmConvertTest {
    private fun shorts(out: ByteArray, frames: Int): List<Int> {
        val view = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        return List(frames * 2) { view.getShort(it * 2).toInt() }
    }

    private fun pcm16(vararg samples: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort(it.toShort()) }
        buffer.flip()
        return buffer
    }

    @Test
    fun `16-bit stereo goes through as it is`() {
        val convert = PcmConvert(44_100, 2, PcmEncoding.PCM_16BIT)
        val input = pcm16(1000, -1000, 32767, -32768)
        val out = ByteArray(8)
        assertEquals(2, convert.convert(input, 2, out, 0))
        assertEquals(listOf(1000, -1000, 32767, -32768), shorts(out, 2))
        assertEquals(0, input.remaining())
        assertEquals(44_100, convert.outRate)
    }

    @Test
    fun `only whole frames are read, and only as many as asked`() {
        val convert = PcmConvert(48_000, 2, PcmEncoding.PCM_16BIT)
        val input = pcm16(1, 2, 3, 4, 5, 6, 7)
        val out = ByteArray(64)
        assertEquals(2, convert.convert(input, 2, out, 0))
        assertEquals(6, input.remaining())
        assertEquals(1, convert.convert(input, 10, out, 0))
        assertEquals(2, input.remaining())
    }

    @Test
    fun `mono is doubled`() {
        val out = ByteArray(8)
        PcmConvert(44_100, 1, PcmEncoding.PCM_16BIT).convert(pcm16(500, -500), 2, out, 0)
        assertEquals(listOf(500, 500, -500, -500), shorts(out, 2))
    }

    @Test
    fun `float past full scale is clamped rather than wrapped`() {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(1.5f).putFloat(-2f).putFloat(0.5f).putFloat(0f)
        buffer.flip()
        val out = ByteArray(8)
        PcmConvert(48_000, 2, PcmEncoding.PCM_FLOAT).convert(buffer, 2, out, 0)
        assertEquals(listOf(32767, -32768, 16384, 0), shorts(out, 2))
    }

    @Test
    fun `24-bit little-endian and 32-bit big-endian are read`() {
        val b24 = ByteBuffer.wrap(byteArrayOf(0x00, 0x00, 0x40, 0x00, 0x00, 0xC0.toByte()))
        val out = ByteArray(4)
        PcmConvert(96_000 / 2, 2, PcmEncoding.PCM_24BIT).convert(b24, 1, out, 0)
        assertEquals(listOf(16384, -16384), shorts(out, 1))

        val b32 = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(Int.MIN_VALUE / 4).putInt(Int.MAX_VALUE / 2)
        b32.flip()
        PcmConvert(44_100, 2, PcmEncoding.PCM_32BIT_BIG_ENDIAN).convert(b32, 1, out, 0)
        assertEquals(listOf(-8192, 16384), shorts(out, 1))
    }

    @Test
    fun `a rate over 48 kHz is brought down by a whole factor where one divides it`() {
        assertEquals(1, PcmConvert.factorFor(44_100))
        assertEquals(1, PcmConvert.factorFor(48_000))
        assertEquals(2, PcmConvert.factorFor(88_200))
        assertEquals(2, PcmConvert.factorFor(96_000))
        assertEquals(4, PcmConvert.factorFor(176_400))
        assertEquals(4, PcmConvert.factorFor(192_000))
        assertEquals(8, PcmConvert.factorFor(384_000))
        // A prime: nothing divides it.
        assertEquals(1, PcmConvert.factorFor(48_017))
        assertEquals(48_000, PcmConvert(96_000, 2, PcmEncoding.PCM_16BIT).outRate)
    }

    @Test
    fun `decimation averages each run, carried across buffers`() {
        val convert = PcmConvert(96_000, 2, PcmEncoding.PCM_16BIT)
        val out = ByteArray(16)
        // Three frames in: one out, and one carried.
        assertEquals(1, convert.convert(pcm16(100, 0, 300, 0, 1000, -1000), 3, out, 0))
        assertEquals(listOf(200, 0), shorts(out, 1))
        // How many more input frames the next output frame needs, given the carry.
        assertEquals(1, convert.inputFramesFor(1))
        assertEquals(3, convert.inputFramesFor(2))
        assertEquals(1, convert.convert(pcm16(3000, -3000), 1, out, 0))
        assertEquals(listOf(2000, -2000), shorts(out, 1))
        // A new stream drops the carry.
        convert.convert(pcm16(7, 7), 1, out, 0)
        convert.reset()
        assertEquals(2, convert.inputFramesFor(1))
    }

    @Test
    fun `5_1 is folded to stereo without the LFE`() {
        val out = ByteArray(4)
        // FL FR FC LFE BL BR
        PcmConvert(48_000, 6, PcmEncoding.PCM_16BIT).convert(pcm16(16384, 0, 0, 32767, 0, 0), 1, out, 0)
        val gain = 1f / (1f + 2 * 0.70710677f)
        assertEquals(listOf(Math.round(0.5f * gain * 32768f), 0), shorts(out, 1))
    }

    @Test
    fun `an encoding that is not PCM is not read`() {
        val convert = PcmConvert(48_000, 2, 5 /* C.ENCODING_AC3 */)
        assertEquals(0, convert.convert(pcm16(1, 2), 1, ByteArray(4), 0))
    }
}
