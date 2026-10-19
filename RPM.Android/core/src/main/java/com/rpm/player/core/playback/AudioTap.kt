package com.rpm.player.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The audio being heard, as mono samples, for the room to measure.
 *
 * Filled from the audio sink on ExoPlayer's playback thread ([TapSink]) and read on the GL thread.
 * Each buffer is stored with its presentation time, and the sink notes the position ExoPlayer is
 * hearing, so a read takes the samples that end where the listener is, not where the decoder is,
 * which is up to three quarters of a second ahead. [nanos] is the clock the heard position is carried
 * forward on between the sink's reports (`System::nanoTime` in the app).
 */
class AudioTap(private val nanos: () -> Long) {
    private val lock = Any()
    private val ring = FloatArray(RING)

    // Everything below is guarded by [lock].
    private var rate = 0
    /** Samples written since the last flush; the newest is at `(written - 1) % RING`. */
    private var written = 0L
    /** Presentation time of sample 0 after the last flush, or unset before the first buffer. */
    private var baseUs = C.TIME_UNSET
    private var heardUs = C.TIME_UNSET
    private var heardAtNanos = 0L
    private var playing = false

    fun configure(sampleRate: Int) = synchronized(lock) {
        if (sampleRate != rate) {
            rate = sampleRate
            clearLocked()
        }
    }

    fun flush() = synchronized(lock) { clearLocked() }

    fun setPlaying(on: Boolean) = synchronized(lock) {
        // Re-noted at the moment of the change, so the extrapolation in [read] neither runs on while
        // paused nor jumps when play resumes.
        if (heardUs != C.TIME_UNSET) heardUs = heardNowLocked(nanos())
        heardAtNanos = nanos()
        playing = on
    }

    fun noteHeard(positionUs: Long) = synchronized(lock) {
        heardUs = positionUs
        heardAtNanos = nanos()
    }

    /** Copies the PCM between the buffer's position and limit, mixed to mono. The buffer's own
     * position is not moved. Encodings other than 16-bit and float are skipped. */
    fun write(buffer: ByteBuffer, presentationTimeUs: Long, encoding: Int, channels: Int) {
        if (channels <= 0) return
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
        val frames = view.remaining() / (bytesPerSample * channels)
        if (frames == 0) return

        synchronized(lock) {
            if (rate <= 0) return
            val expectedUs = if (baseUs == C.TIME_UNSET) C.TIME_UNSET else baseUs + written * 1_000_000L / rate
            // A buffer that does not follow on from the last one is a new start (a gap the sink was not
            // told about as a flush). Ring and clock start again from it.
            if (expectedUs == C.TIME_UNSET || kotlin.math.abs(presentationTimeUs - expectedUs) > DISCONTINUITY_US) {
                clearLocked()
                baseUs = presentationTimeUs
            }
            repeat(frames) {
                var sum = 0f
                repeat(channels) {
                    sum += if (bytesPerSample == 2) view.short / 32768f else view.float
                }
                ring[(written % RING).toInt()] = sum / channels
                written++
            }
        }
    }

    /**
     * Fills [into] with the `into.size` samples centred on the position being heard, and returns the
     * sample rate they are at; or returns 0 and fills nothing when there is nothing
     * to read (paused, before the first buffer, after a flush, or when the window has left the ring).
     *
     * Paused answers nothing, not the window it stopped on. That window is the same on every frame, so
     * the spectrum would stay in the shape it had at the pause. Answered nothing, the room's analysis eases the
     * bars down, as the desktop's does when it is told nothing is playing (PegasusStage, RoomBands.Update).
     */
    fun read(into: FloatArray): Int = synchronized(lock) {
        if (!playing || rate <= 0 || baseUs == C.TIME_UNSET || heardUs == C.TIME_UNSET) return 0
        val heard = heardNowLocked(nanos())
        // Centred, not ending, on the heard sample: the analysis is Hann-shaped, so a window that ends there weighs
        // most what was heard half a window ago: 23 ms for the bars' 2048 samples and 93 ms for the spectrum's 8192
        // at 44.1 kHz, and the room would lag the music by that. The decoder runs ahead, so the samples
        // after the heard one are already here; where they are not, the newest window is taken.
        val end = minOf(written, (heard - baseUs) * rate / 1_000_000L + into.size / 2)
        val start = end - into.size
        if (start < 0 || start < written - RING) return 0
        for (i in into.indices) into[i] = ring[((start + i) % RING).toInt()]
        rate
    }

    private fun heardNowLocked(nowNanos: Long): Long =
        if (playing) heardUs + (nowNanos - heardAtNanos) / 1000 else heardUs

    private fun clearLocked() {
        written = 0
        baseUs = C.TIME_UNSET
        heardUs = C.TIME_UNSET
    }

    private companion object {
        /** About 1.5 seconds at 44.1 kHz: the decoder's lead plus the longest analysis window. */
        const val RING = 1 shl 16
        const val DISCONTINUITY_US = 50_000L
    }
}

/**
 * ExoPlayer's audio sink with [AudioTap] reading from it. Every call stays on the playback thread, as
 * the sink requires; only the tap's ring is shared.
 */
@OptIn(UnstableApi::class)
class TapSink(sink: AudioSink, private val tap: AudioTap) : ForwardingAudioSink(sink) {
    private var encoding = C.ENCODING_INVALID
    private var channels = 0
    private var lastBuffer: ByteBuffer? = null
    private var lastPresentationUs = C.TIME_UNSET

    override fun configure(config: AudioSink.AudioSinkConfig) {
        encoding = config.format.pcmEncoding
        channels = config.format.channelCount
        tap.configure(config.format.sampleRate)
        super.configure(config)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // A buffer the sink could not take whole is offered again, the same object with the same
        // time. Copied the first time only.
        if (buffer !== lastBuffer || presentationTimeUs != lastPresentationUs) {
            tap.write(buffer, presentationTimeUs, encoding, channels)
            lastBuffer = buffer
            lastPresentationUs = presentationTimeUs
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        super.getCurrentPositionUs(sourceEnded).also {
            if (it != AudioSink.CURRENT_POSITION_NOT_SET) tap.noteHeard(it)
        }

    override fun play() {
        tap.setPlaying(true)
        super.play()
    }

    override fun pause() {
        tap.setPlaying(false)
        super.pause()
    }

    override fun flush() {
        tap.flush()
        lastBuffer = null
        super.flush()
    }

    override fun reset() {
        tap.flush()
        lastBuffer = null
        super.reset()
    }
}
