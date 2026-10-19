package com.rpm.player.core.cast

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Where the sink sends audio: to the TV only ([wanted] is null), or to the feed of a casting session. The
 * controller writes it on the main thread. [CastingSink] reads it on ExoPlayer's playback thread at its next
 * call, and that is where the switch is made. The field is `@Volatile` because two threads use it.
 */
class CastRoute {
    @Volatile var wanted: CastFeed? = null

    /** Called on the sink's thread when the decoder gives audio that cannot go in a WAV (Dolby passed
     * through to an amplifier). The controller sets it. */
    @Volatile var cannotCast: (() -> Unit)? = null
}

/**
 * ExoPlayer's audio sink. It sends audio either to the TV's own output or to a Cast device. Casting changes
 * only the destination, and nothing above the sink changes. The queue advances because ExoPlayer advances it,
 * and the position that ExoPlayer shows is the **room's**, because this sink reports it. This is the same
 * design as the phone (`CastSink`, `RoomFeed`): one source, one sink, and the sink is swapped.
 *
 * While casting:
 * - [handleBuffer] converts the decoder's PCM to 16-bit stereo ([PcmConvert]) and writes into the feed's ring
 *   as much as [CastFeed.take] allows. It returns false for the rest, and ExoPlayer offers that data again.
 *   This back pressure sets the pace: the decoder runs at most [CastFeed.LEAD_S] seconds ahead of what the room
 *   reports.
 * - [getCurrentPositionUs] is the stream's first presentation time plus what the room has played.
 * - [flush] (a seek or a skip) cuts the stream. The next buffer begins a new stream, which the controller LOADs.
 * - A change of output rate waits until the room has played what it holds, then begins a new stream.
 *
 * Every call is on ExoPlayer's playback thread, as an `AudioSink` requires. [settle] is the only place where
 * the route's switch is applied.
 */
@OptIn(UnstableApi::class)
class CastingSink(private val real: AudioSink, private val route: CastRoute) : ForwardingAudioSink(real) {
    // All fields below are used on the playback thread only.
    private var active: CastFeed? = null
    private var listener: AudioSink.Listener? = null
    /** Whether ExoPlayer has the renderer playing: `play()` was called and `pause()` has not been called since. */
    private var started = false
    private var convert: PcmConvert? = null
    private var encoding = C.ENCODING_INVALID
    private var restreamPending = false
    private var endOfStream = false
    /** The stream's first presentation time. The room's position is counted from it. */
    private var startPtsUs = C.TIME_UNSET
    /** Where the next buffer is expected to start. It is used to detect a jump that the sink was not told about. */
    private var expectedPtsUs = C.TIME_UNSET
    private var lastBuffer: ByteBuffer? = null
    private var lastPtsUs = C.TIME_UNSET
    private var lastBufferFrames = 0
    private var scratch = ByteArray(0)
    private var refused = false

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        super.setListener(listener)
    }

    override fun getFormatSupport(format: Format): Int {
        // While a cast is wanted, encoded audio (Dolby to an amplifier) is refused. The next item is then
        // decoded to PCM, which the stream can carry.
        if (route.wanted != null && format.sampleMimeType != MimeTypes.AUDIO_RAW) return AudioSink.SINK_FORMAT_UNSUPPORTED
        return super.getFormatSupport(format)
    }

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(config: AudioSink.AudioSinkConfig) {
        super.configure(config)
        val format = config.format
        encoding = if (format.sampleMimeType == MimeTypes.AUDIO_RAW) format.pcmEncoding else C.ENCODING_INVALID
        val next = if (PcmEncoding.isReadable(encoding) && format.sampleRate > 0 && format.channelCount > 0) {
            PcmConvert(format.sampleRate, format.channelCount, encoding)
        } else {
            null
        }
        // Everything of the old format has been handed over by now, because the renderer configures before the
        // first buffer of the new format. A new output rate cannot go into the same WAV.
        val was = convert
        if (active?.current != null && was != null && next != null && next.outRate != was.outRate) restreamPending = true
        convert = next
        refused = false
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        settle()
        val feed = active ?: return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val conv = convert
        if (conv == null) {
            // Not PCM: the controller is told once and moves the sound back to the TV. After that, [settle] passes
            // this buffer to the TV's own output.
            if (!refused) {
                refused = true
                route.cannotCast?.invoke()
            }
            return false
        }
        if (feed.current == null || restreamPending) {
            if (restreamPending && !feed.drained()) return false
            restreamPending = false
            conv.reset()
            feed.begin(conv.outRate)
            startPtsUs = presentationTimeUs
            expectedPtsUs = presentationTimeUs
        }
        val frameBytes = PcmEncoding.bytesPerSample(encoding) * conv.inChannels
        if (buffer !== lastBuffer || presentationTimeUs != lastPtsUs) {
            // A new buffer. If it does not follow on from the last one, it is a jump that the sink was not told
            // about as a flush. The room's position is moved by the jump, as DefaultAudioSink does.
            if (expectedPtsUs != C.TIME_UNSET && abs(presentationTimeUs - expectedPtsUs) > DISCONTINUITY_US) {
                startPtsUs += presentationTimeUs - expectedPtsUs
                listener?.onPositionDiscontinuity()
            }
            lastBuffer = buffer
            lastPtsUs = presentationTimeUs
            lastBufferFrames = buffer.remaining() / frameBytes
            expectedPtsUs = presentationTimeUs + lastBufferFrames * 1_000_000L / conv.inRate
        }
        val inLeft = buffer.remaining() / frameBytes
        if (inLeft == 0) {
            buffer.position(buffer.limit())
            return true
        }
        val outWanted = (inLeft + conv.factor - 1) / conv.factor
        val allowed = feed.take(outWanted)
        if (allowed == 0) return false
        val inFrames = minOf(inLeft, conv.inputFramesFor(allowed))
        val bytes = allowed * PcmConvert.OUT_FRAME_BYTES
        if (scratch.size < bytes) scratch = ByteArray(bytes)
        val out = conv.convert(buffer, inFrames, scratch, 0)
        if (out > 0) {
            feed.ring.write(scratch, 0, out * PcmConvert.OUT_FRAME_BYTES)
            feed.wrote(out)
        }
        // A tail of less than one frame is not audio.
        if (buffer.remaining() < frameBytes) buffer.position(buffer.limit())
        return !buffer.hasRemaining()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        settle()
        val feed = active ?: return super.getCurrentPositionUs(sourceEnded)
        val stream = feed.current ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (startPtsUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val heard = feed.heardFrames() ?: 0
        return startPtsUs + heard * 1_000_000L / stream.rate
    }

    override fun play() {
        started = true
        settle()
        if (active == null) super.play()
    }

    override fun pause() {
        started = false
        settle()
        super.pause()
    }

    override fun playToEndOfStream() {
        settle()
        if (active == null) super.playToEndOfStream() else endOfStream = true
    }

    override fun isEnded(): Boolean {
        settle()
        val feed = active ?: return super.isEnded()
        return endOfStream && feed.drained()
    }

    override fun hasPendingData(): Boolean {
        settle()
        val feed = active ?: return super.hasPendingData()
        return feed.pending()
    }

    override fun getAudioTrackBufferSizeUs(): Long =
        if (active != null) C.TIME_UNSET else super.getAudioTrackBufferSizeUs()

    override fun flush() {
        settle()
        super.flush()
        active?.takenBack()
        clearStream()
    }

    override fun reset() {
        settle()
        super.reset()
        active?.takenBack()
        clearStream()
        started = false
    }

    /**
     * Applies the route's switch. Switching to a speaker: the TV's own output is paused and flushed, so the audio
     * it held is dropped and not played, and the next buffer begins a stream. Switching back to the TV: its
     * output starts again if ExoPlayer has the renderer playing. The controller has already seeked to the room's
     * position, and the flush for that seek is the next call. For a station, playback continues from the
     * decoder.
     */
    private fun settle() {
        val want = route.wanted
        if (want === active) return
        val was = active
        if (was == null) {
            super.pause()
            super.flush()
        }
        active = want
        clearStream()
        if (want == null) {
            super.flush()
            if (started) super.play()
        }
    }

    private fun clearStream() {
        startPtsUs = C.TIME_UNSET
        expectedPtsUs = C.TIME_UNSET
        lastBuffer = null
        lastPtsUs = C.TIME_UNSET
        restreamPending = false
        endOfStream = false
        refused = false
        convert?.reset()
    }

    private companion object {
        /** A buffer this far from where the last one ended counts as a jump. This is the same value as DefaultAudioSink. */
        const val DISCONTINUITY_US = 200_000L
    }
}
