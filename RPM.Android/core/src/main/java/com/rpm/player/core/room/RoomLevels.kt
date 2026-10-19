package com.rpm.player.core.room

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * What the room hears: the three numbers it is lit by (the low end, the middle and the air, each an
 * envelope from 0 to 1 with its own attack, hold and release), and the 128-band run the spectrum is
 * drawn from.
 *
 * Same as the iOS app's `RoomBands` (RPMStages), with the same numbers. The bass and the middle are
 * read from the 2048-sample window, the air from the 1024 one. The run folds the 8192, 2048 and 1024
 * windows into log-spaced bands. The run, and the 8192-sample transform behind it, is worked out only
 * on a frame that asks for it ([update]'s `withRun`), because only the player's spread view draws it.
 * Not thread-safe: the GL thread owns one and calls [update] once a frame.
 */
class RoomLevels(rate: Int) {
    val rate: Int = rate

    private val low = AnalysisWindow(rate, frame = 8192, bins = 160)
    private val mid = AnalysisWindow(rate, frame = 2048, bins = 256)
    private val high = AnalysisWindow(rate, frame = 1024, bins = 384)

    var bass = 0f
        private set
    var middle = 0f
        private set
    var air = 0f
        private set

    /** The run, 0 to 1 per band, log spaced and ready to draw. It eases down while it is not worked out. */
    val run = FloatArray(BANDS)

    private val raw = FloatArray(BANDS)
    private val curve = FloatArray(BANDS)
    private val rangePeak = floatArrayOf(0.30f, 0.20f, 0.12f)
    private val rangeGain = floatArrayOf(1f, 1f, 1f)

    private var bassHold = 0.0
    private var midHold = 0.0
    private var airHold = 0.0

    /**
     * One frame. [read] fills a window with the samples being heard and says whether it could.
     * When it cannot, or nothing is playing, the spectrum decays and the envelopes fall back.
     * With [withRun], the 128-band run is worked out too. Without it, the run eases down.
     */
    fun update(nowSeconds: Double, playing: Boolean, withRun: Boolean, read: (FloatArray) -> Boolean) {
        if (withRun) low.update(playing, read)
        mid.update(playing, read)
        high.update(playing, read)

        val spectrum = mid.level
        var loud = 0f
        for (at in BASS_FROM until BASS_UPTO) loud += spectrum[at]
        loud = minOf(1f, loud / (BASS_UPTO - BASS_FROM) * 1.35f)
        if (loud > bass) {
            bass += (loud - bass) * 0.42f
            bassHold = nowSeconds + 0.10
        } else if (nowSeconds >= bassHold) {
            bass += (loud - bass) * 0.055f
        }

        var centre = 0f
        for (at in MID_FROM until MID_UPTO) centre += spectrum[at]
        centre = minOf(1f, centre / (MID_UPTO - MID_FROM) * 1.9f)
        if (centre > middle) {
            middle += (centre - middle) * 0.50f
            midHold = nowSeconds + 0.06
        } else if (nowSeconds >= midHold) {
            middle += (centre - middle) * 0.11f
        }

        val top = high.level
        var bright = 0f
        for (at in AIR_FROM until AIR_UPTO) bright += top[at]
        bright = minOf(1f, bright / (AIR_UPTO - AIR_FROM) * 3.0f)
        if (bright > air) {
            air += (bright - air) * 0.62f
            airHold = nowSeconds + 0.03
        } else if (nowSeconds >= airHold) {
            air += (bright - air) * 0.16f
        }

        if (withRun) {
            foldTheRun()
            measureTheRanges()
            drawTheRun()
        } else {
            for (band in 0 until BANDS) run[band] *= 0.80f
        }
    }

    /** Log-spaced bands, each range laid out across the width it was given, then a light three-tap smoothing. */
    private fun foldTheRun() {
        for (range in 0 until RANGES) {
            val from = RANGE_BAND[range]
            val upto = RANGE_BAND[range + 1]
            val lowHz = RANGE_HZ[range]
            val span = RANGE_HZ[range + 1] / lowHz
            val count = (upto - from).toDouble()
            for (band in from until upto) {
                val at = (band - from).toDouble()
                raw[band] = fold(lowHz * Math.pow(span, at / count), lowHz * Math.pow(span, (at + 1) / count))
            }
        }
        for (band in 0 until BANDS) {
            val before = raw[maxOf(0, band - 1)]
            val after = raw[minOf(BANDS - 1, band + 1)]
            curve[band] = before * 0.25f + raw[band] * 0.50f + after * 0.25f
        }
    }

    /** Each range against its own recent peak, which rises quickly and falls over seconds. */
    private fun measureTheRanges() {
        for (range in 0 until RANGES) {
            var peak = 0f
            for (at in RANGE_BAND[range] until RANGE_BAND[range + 1]) peak = maxOf(peak, curve[at])
            rangePeak[range] += (peak - rangePeak[range]) * (if (peak > rangePeak[range]) 0.40f else 0.014f)
            val gain = RANGE_REFERENCE / Math.pow(maxOf(rangePeak[range], RANGE_FLOOR).toDouble(), RANGE_ADAPT.toDouble()).toFloat()
            rangeGain[range] = gain.coerceIn(1f, 24f)
        }
    }

    /** One pass down the run: gain eased between the ranges, the drive's well, the lean to the rim,
     * a knee that cannot clip, and a quick rise and fall on the shape. */
    private fun drawTheRun() {
        for (band in 0 until BANDS) {
            val along = band.toFloat() / (BANDS - 1)
            val dip = 1 - 4 * along * (1 - along)
            val drive = DRIVE_MIDDLE + ((if (along < 0.5f) DRIVE_LOW else DRIVE_HIGH) - DRIVE_MIDDLE) * dip
            val x = Math.pow((curve[band] * gain(band)).toDouble(), 1.15).toFloat() * drive * lean(along)
            val v = x / (1 + x)
            run[band] += (v - run[band]) * (if (v > run[band]) 0.94f else 0.80f)
        }
    }

    private fun gain(band: Int): Float {
        val b = band.toFloat()
        if (b <= RANGE_CENTRE[0]) return rangeGain[0]
        if (b >= RANGE_CENTRE[RANGES - 1]) return rangeGain[RANGES - 1]
        for (range in 0 until RANGES - 1) {
            if (b <= RANGE_CENTRE[range + 1]) {
                var t = (b - RANGE_CENTRE[range]) / (RANGE_CENTRE[range + 1] - RANGE_CENTRE[range])
                t = t * t * (3 - 2 * t)
                return rangeGain[range] + (rangeGain[range + 1] - rangeGain[range]) * t
            }
        }
        return rangeGain[RANGES - 1]
    }

    /** The level across a span of frequency, from the window that resolves it. The crossovers are the
     * range edges. For a span narrower than a bin, it reads between two bins and does not round to one. */
    private fun fold(lowHz: Double, highHz: Double): Float {
        val window = if (highHz <= RANGE_HZ[1]) low else if (highHz <= RANGE_HZ[2]) mid else high
        val level = window.level
        val last = level.size - 1
        val from = lowHz / window.binHz
        val upto = highHz / window.binHz
        if (upto - from < 1) {
            val at = (from + upto) * 0.5
            val first = at.toInt().coerceIn(0, last - 1)
            val over = (at - first).toFloat()
            return level[first] * (1 - over) + level[first + 1] * over
        }
        val first = from.toInt().coerceIn(0, last)
        val end = upto.toInt().coerceIn(first + 1, last + 1)
        var sum = 0f
        for (at in first until end) sum += level[at]
        return sum / (end - first)
    }

    companion object {
        /** Columns in the run, one per bar. */
        const val BANDS = 128

        private const val BASS_FROM = 1
        private const val BASS_UPTO = 12
        private const val MID_FROM = 20
        private const val MID_UPTO = 90
        private const val AIR_FROM = 116
        private const val AIR_UPTO = 325

        private const val RANGES = 3
        private val RANGE_HZ = doubleArrayOf(20.0, 430.0, 5000.0, 16000.0)
        private val RANGE_WIDTH = floatArrayOf(0.45f, 0.35f, 0.20f)
        private const val DRIVE_LOW = 0.80f
        private const val DRIVE_HIGH = 1.00f
        private const val DRIVE_MIDDLE = 0.16f
        private const val LEAN_RIM = 1.25f
        private const val RANGE_REFERENCE = 6f
        private const val RANGE_ADAPT = 0.40f
        private const val RANGE_FLOOR = 0.02f

        private val RANGE_BAND: IntArray = IntArray(RANGES + 1).also { edges ->
            var over = 0f
            for (range in 0 until RANGES) {
                over += RANGE_WIDTH[range]
                edges[range + 1] = minOf(BANDS, maxOf(edges[range] + 1, Math.round(over * BANDS)))
            }
            edges[RANGES] = BANDS
        }
        private val RANGE_CENTRE: FloatArray = FloatArray(RANGES) { (RANGE_BAND[it] + RANGE_BAND[it + 1] - 1) * 0.5f }

        /** Flat until the top range begins, then a smoothstep climb to [LEAN_RIM] at the rim. */
        private fun lean(along: Float): Float {
            val start = RANGE_BAND[RANGES - 1].toFloat() / (BANDS - 1)
            if (along <= start) return 1f
            var t = (along - start) / maxOf(1e-4f, 1 - start)
            t = t * t * (3 - 2 * t)
            return 1 + (LEAN_RIM - 1) * t
        }
    }
}

/** One analysis window: a Hann-shaped FFT of the newest samples, as a level from 0 to 1 per bin,
 * on a decibel scale from -84 to -20 dB. The iOS app's `AnalysisWindow`, without the delay line. */
internal class AnalysisWindow(rate: Int, private val frame: Int, private val bins: Int) {
    private val wave = FloatArray(frame)
    private val shape = FloatArray(frame) { (0.5 * (1 - cos(2 * PI * it / (frame - 1)))).toFloat() }
    private val re = DoubleArray(frame)
    private val im = DoubleArray(frame)

    val level = FloatArray(bins)
    val binHz: Double = rate.toDouble() / frame

    fun update(playing: Boolean, read: (FloatArray) -> Boolean) {
        if (!playing || !read(wave)) {
            for (at in 0 until bins) level[at] *= SMOOTHING
            return
        }
        for (at in 0 until frame) {
            re[at] = (wave[at] * shape[at]).toDouble()
            im[at] = 0.0
        }
        Fft.transform(re, im)
        val bandwidth = frame.toDouble() / REFERENCE
        for (at in 0 until bins) {
            val power = (re[at] * re[at] + im[at] * im[at]) / (frame.toDouble() * frame) * bandwidth
            val db = 10 * log10(power + 1e-12)
            val heard = ((db - FLOOR_DB) / (CEILING_DB - FLOOR_DB)).coerceIn(0.0, 1.0).toFloat()
            level[at] = if (heard > level[at]) heard else level[at] * SMOOTHING + heard * (1 - SMOOTHING)
        }
    }

    private companion object {
        const val FLOOR_DB = -84.0
        const val CEILING_DB = -20.0
        const val REFERENCE = 2048
        const val SMOOTHING = 0.5f
    }
}

/** An in-place radix-2 FFT, as the iOS app's `Spectrum.transform`. The length must be a power of two. */
internal object Fft {
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n >= 2 && n and (n - 1) == 0 && im.size == n)

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
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
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
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
