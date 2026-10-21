package com.rpm.player.phone.stage

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The audio the visualisers read: a 512-wide spectrum row and a 512-wide waveform row of the music. The iPhone's
 * `ShaderAudio` (RPMStages/ShaderAudio.swift), figure for figure: ShaderToy's audio texture, which is the shape every
 * look was written against.
 *
 * **Loudness, not magnitude.** A decibel window is mapped onto 0 to 1, so a quiet bin sits near the floor and a
 * full-scale tone near the top. Raw magnitude read as a level puts everything in the bottom tenth and nothing moves.
 *
 * Not thread-safe: the stage's GL thread owns every look and everything a look listens with.
 */
class ShaderAudio(private val frameSize: Int = 2048) {
    private val window = FloatArray(frameSize) { (0.5 * (1 - cos(2 * PI * it / (frameSize - 1)))).toFloat() }
    private val re = DoubleArray(frameSize)
    private val im = DoubleArray(frameSize)

    /** The smoothed spectrum, 0 to 1, held between frames. */
    val spectrum = FloatArray(WIDTH)

    /** The waveform, -1 to 1 across the row. */
    val wave = FloatArray(WIDTH)

    init {
        require(frameSize >= WIDTH * 2 && frameSize and (frameSize - 1) == 0) { "a frame is a power of two and at least ${WIDTH * 2} samples" }
    }

    /**
     * Reads a frame of played samples; [count] is 0 for silence. Silence is a resting picture rather than a blank one:
     * the spectrum eases down from wherever it was and the waveform lies flat.
     */
    fun listen(frame: FloatArray, count: Int) {
        readTheSpectrum(frame, count)
        readTheWave(frame, count)
    }

    private fun readTheSpectrum(frame: FloatArray, count: Int) {
        // No samples means no transform: the resting picture is the held spectrum easing down.
        if (count == 0) {
            for (x in 0 until WIDTH) spectrum[x] *= SMOOTHING
            return
        }
        val take = min(count, frameSize)
        for (i in 0 until take) {
            re[i] = (frame[i] * window[i]).toDouble()
            im[i] = 0.0
        }
        for (i in take until frameSize) {
            re[i] = 0.0
            im[i] = 0.0
        }
        StageFft.transform(re, im)
        val n = frameSize.toDouble()
        // Linear bins, one texel each. The first 512 of the n/2 bins reach about 11 kHz at 44.1 kHz.
        for (x in 0 until WIDTH) {
            val power = (re[x] * re[x] + im[x] * im[x]) / (n * n)
            val db = 10 * log10(power + 1e-12)
            val level = ((db - DB_FLOOR) / (DB_CEILING - DB_FLOOR)).coerceIn(0.0, 1.0).toFloat()
            // Peak fast, fall slow.
            spectrum[x] = if (level > spectrum[x]) level else spectrum[x] * SMOOTHING + level * (1 - SMOOTHING)
        }
    }

    private fun readTheWave(frame: FloatArray, count: Int) {
        val take = min(count, frameSize)
        for (x in 0 until WIDTH) wave[x] = if (take > 0) frame[x * take / WIDTH].coerceIn(-1f, 1f) else 0f
    }

    companion object {
        /** Texels across each row. ShaderToy's audio texture is 512 wide. */
        const val WIDTH = 512

        private const val DB_FLOOR = -84.0
        private const val DB_CEILING = -20.0

        /** How much of the previous frame carries over, so the bars settle instead of strobing. */
        private const val SMOOTHING = 0.5f
    }
}

/**
 * Bass, middle and air, for the marched visualisers: the iPhone's `MarchBands`. Read off the spectrum row and
 * enveloped fast up and slow down, so a hit arrives on the frame it happens and leaves over the next few.
 */
class MarchBands(frameSize: Int = 2048) {
    private val audio = ShaderAudio(frameSize)

    var bass = 0f
        private set
    var middle = 0f
        private set
    var air = 0f
        private set

    /** The spectrum row itself, for the butterfly, which draws the reading rather than being lit by it. */
    val spectrum: FloatArray get() = audio.spectrum

    fun listen(frame: FloatArray, count: Int) {
        audio.listen(frame, count)
        val heard = audio.spectrum
        fun band(from: Int, to: Int): Float {
            var sum = 0f
            for (x in from until to) sum += heard[x]
            return sum / (to - from)
        }
        bass = max(band(0, 24), bass * 0.86f)
        middle = max(band(24, 140), middle * 0.90f)
        air = max(band(140, 400), air * 0.92f)
    }
}

/** An in-place radix-2 Cooley-Tukey FFT, as the iPhone's `Spectrum.transform`. The length must be a power of two. */
internal object StageFft {
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val r = re[i]; re[i] = re[j]; re[j] = r
                val m = im[i]; im[i] = im[j]; im[j] = m
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2 * PI / length
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until length / 2) {
                    val a = i + k
                    val b = a + length / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val next = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = next
                }
                i += length
            }
            length = length shl 1
        }
    }
}

/**
 * A random source that can be replayed: SplitMix64, as the iPhone's `Roll`. Sand is thrown on the plate once and
 * never again, so the plate should be thrown the same way every time the app starts.
 */
class Roll(seed: Long) {
    private var state = seed

    fun next(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** A value from 0 up to, not including, 1. */
    fun unit(): Double = (next() ushr 11) * (1.0 / 9_007_199_254_740_992.0)

    /** A value from -0.5 to 0.5: a throw with no direction of its own. */
    fun nudge(): Float = (unit() - 0.5).toFloat()
}
