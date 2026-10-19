package com.rpm.player.core.cast

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The encodings that the sink can receive, as Media3's `C.ENCODING_*` numbers. This way the file needs no
 * Android classes and can be tested on the JVM. */
object PcmEncoding {
    const val PCM_8BIT = 3
    const val PCM_16BIT = 2
    const val PCM_16BIT_BIG_ENDIAN = 0x10000000
    const val PCM_24BIT = 21
    const val PCM_24BIT_BIG_ENDIAN = 0x50000000
    const val PCM_32BIT = 22
    const val PCM_32BIT_BIG_ENDIAN = 0x60000000
    const val PCM_FLOAT = 4

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        PCM_8BIT -> 1
        PCM_16BIT, PCM_16BIT_BIG_ENDIAN -> 2
        PCM_24BIT, PCM_24BIT_BIG_ENDIAN -> 3
        PCM_32BIT, PCM_32BIT_BIG_ENDIAN, PCM_FLOAT -> 4
        else -> 0
    }

    fun isReadable(encoding: Int): Boolean = bytesPerSample(encoding) > 0
}

/**
 * Converts any PCM that the decoder gives the sink to the format used in the WAV: 16-bit little-endian stereo at
 * [outRate]. Mono is duplicated to both channels. 5.1 and 7.1 are mixed down to stereo (front channels, centre at
 * -3 dB, surrounds at -3 dB, no LFE), assuming the WAVE channel order that Android's decoders use. Other layouts
 * keep their first two channels.
 *
 * A rate over 48 kHz is reduced by a whole factor ([factorFor]) by averaging each run of input frames, because
 * a receiver is not guaranteed to play more than 48 kHz. The running average is carried across buffers, so a
 * buffer boundary does not cause a click. A rate that no whole factor reduces to 48 kHz or less is not changed.
 *
 * Use one instance per stream, and call [reset] when a stream begins.
 */
class PcmConvert(val inRate: Int, val inChannels: Int, val encoding: Int) {
    val factor: Int = factorFor(inRate)
    val outRate: Int = inRate / factor

    private val inFrameBytes = PcmEncoding.bytesPerSample(encoding) * inChannels
    private var sumL = 0f
    private var sumR = 0f
    private var summed = 0

    /** How many input frames [convert] needs to fill [outFrames] output frames, counting the carried partial frame. */
    fun inputFramesFor(outFrames: Int): Int = maxOf(0, outFrames * factor - summed)

    /**
     * Converts up to [maxInFrames] whole input frames from [input] (its position to its limit) into [out] at
     * [outOffset]. Moves [input]'s position past what it read. Returns the number of output frames written.
     */
    fun convert(input: ByteBuffer, maxInFrames: Int, out: ByteArray, outOffset: Int): Int {
        if (inFrameBytes == 0) return 0
        val view = input.slice().order(if (bigEndian()) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val frames = minOf(maxInFrames, view.remaining() / inFrameBytes)
        var at = outOffset
        var written = 0
        repeat(frames) {
            var left = 0f
            var right = 0f
            when (inChannels) {
                1 -> {
                    left = sample(view)
                    right = left
                }
                6, 8 -> {
                    val fl = sample(view)
                    val fr = sample(view)
                    val c = sample(view)
                    sample(view) // LFE, left out of the stereo mix-down
                    val bl = sample(view)
                    val br = sample(view)
                    var sl = 0f
                    var sr = 0f
                    if (inChannels == 8) {
                        sl = sample(view)
                        sr = sample(view)
                    }
                    left = (fl + MINUS_3DB * c + MINUS_3DB * (bl + sl)) * FOLD_GAIN
                    right = (fr + MINUS_3DB * c + MINUS_3DB * (br + sr)) * FOLD_GAIN
                }
                else -> {
                    left = sample(view)
                    right = if (inChannels >= 2) sample(view) else left
                    repeat(inChannels - 2) { sample(view) }
                }
            }
            sumL += left
            sumR += right
            summed += 1
            if (summed == factor) {
                put(out, at, sumL / factor)
                put(out, at + 2, sumR / factor)
                at += OUT_FRAME_BYTES
                written += 1
                sumL = 0f
                sumR = 0f
                summed = 0
            }
        }
        input.position(input.position() + frames * inFrameBytes)
        return written
    }

    /** Drops the carried part of an output frame, so a new stream starts clean. */
    fun reset() {
        sumL = 0f
        sumR = 0f
        summed = 0
    }

    private fun bigEndian(): Boolean = encoding == PcmEncoding.PCM_16BIT_BIG_ENDIAN ||
        encoding == PcmEncoding.PCM_24BIT_BIG_ENDIAN || encoding == PcmEncoding.PCM_32BIT_BIG_ENDIAN

    /** One sample, as -1 to 1. */
    private fun sample(view: ByteBuffer): Float = when (encoding) {
        PcmEncoding.PCM_8BIT -> ((view.get().toInt() and 0xFF) - 128) / 128f
        PcmEncoding.PCM_16BIT, PcmEncoding.PCM_16BIT_BIG_ENDIAN -> view.short / 32768f
        PcmEncoding.PCM_24BIT -> {
            val b0 = view.get().toInt() and 0xFF
            val b1 = view.get().toInt() and 0xFF
            val b2 = view.get().toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
        }
        PcmEncoding.PCM_24BIT_BIG_ENDIAN -> {
            val b2 = view.get().toInt()
            val b1 = view.get().toInt() and 0xFF
            val b0 = view.get().toInt() and 0xFF
            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
        }
        PcmEncoding.PCM_32BIT, PcmEncoding.PCM_32BIT_BIG_ENDIAN -> view.int / 2147483648f
        PcmEncoding.PCM_FLOAT -> view.float
        else -> 0f
    }

    private fun put(out: ByteArray, at: Int, value: Float) {
        // Scale by the same 32768 that the 16-bit input was divided by, so 16-bit audio passes through unchanged.
        // Then clamp: a sample above full scale would wrap round when cut to 16 bits, which causes a loud click.
        val s = Math.round(value * 32768f).coerceIn(-32768, 32767)
        out[at] = s.toByte()
        out[at + 1] = (s shr 8).toByte()
    }

    companion object {
        const val OUT_CHANNELS = 2
        const val OUT_FRAME_BYTES = 4
        const val HIGHEST_RATE = 48_000

        private const val MINUS_3DB = 0.70710677f

        /** Stops a 5.1 mix-down from going above full scale when a front channel, the centre and a surround are all
         * at full scale. A 7.1 mix-down with every channel at full scale is clamped. */
        private const val FOLD_GAIN = 1f / (1f + 2 * MINUS_3DB)

        /** The smallest whole factor that brings [rate] to 48 kHz or less, or 1 if no factor divides it exactly. */
        fun factorFor(rate: Int): Int {
            if (rate <= HIGHEST_RATE) return 1
            var k = 2
            while (k <= 16) {
                if (rate % k == 0 && rate / k <= HIGHEST_RATE) return k
                k += 1
            }
            return 1
        }
    }
}
