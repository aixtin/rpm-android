package com.rpm.player.core.playback

import android.os.Bundle
import com.rpm.player.core.cloud.CloudTrackTags
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How much of the volume levelling is applied (the desktop's NormalizeMode, the iOS app's
 * `NormalizeMode`). Stored by name, so a mode added in the middle cannot change a saved choice.
 */
enum class LevelMode {
    /** Every record plays at the level it was mastered at. */
    Off,

    /** The default. A record played in order is levelled as a whole, so its quiet song stays quiet beside
     * its loud one. A mixed queue is levelled track by track. */
    Album,

    /** Every track levelled on its own. */
    Track,
}

/** How loud something was measured to be: its programme loudness in LUFS, and its true peak as a linear
 * amplitude (1.0 is full scale). Null where nothing measured it. */
data class Level(val lufs: Double?, val truePeak: Double?) {
    /** False for a track nothing measured, and for one a meter could not read (negative infinity). */
    val known: Boolean get() = lufs != null && lufs.isFinite()

    companion object {
        val UNKNOWN = Level(null, null)

        /** What a catalogue row says about itself. Zero means "not measured" in both fields. */
        fun of(tags: CloudTrackTags?): Level {
            if (tags == null) return UNKNOWN
            return Level(tags.loudnessLufs.takeIf { it < 0 }, tags.truePeak.takeIf { it > 0 })
        }
    }
}

/**
 * The gain that brings a measured loudness to the reference. It is ported constant for constant from
 * RPM.Core/Analysis/ReplayGain.cs through the iOS app's `ReplayGain`. A record that is levelled wrongly
 * plays at a level nobody chose, and nothing reports it, so tests pin the numbers to the desktop's.
 */
object ReplayGain {
    /** What everything is levelled to, in LUFS: ReplayGain 2.0's reference, not a streaming service's -14. */
    const val REFERENCE_LUFS = -18.0

    /** How close to full scale a levelled track may come, in dBTP. */
    const val PEAK_CEILING_DB = -1.0

    /** How far a track may be turned up or down, in dB. */
    const val MAX_GAIN_DB = 15.0

    fun gainDb(lufs: Double, target: Double = REFERENCE_LUFS): Double = if (lufs.isFinite()) target - lufs else 0.0

    /** Held within [MAX_GAIN_DB] either way, and held back so the peak lands under [PEAK_CEILING_DB]. It only
     * ever holds a gain back: a peak far under the ceiling does not push a track up to it. A [truePeak] of 0
     * or less is unknown, and then there is nothing to hold the gain back by. */
    fun clamp(gainDb: Double, truePeak: Double): Double {
        if (!gainDb.isFinite()) return 0.0
        val capped = min(max(gainDb, -MAX_GAIN_DB), MAX_GAIN_DB)
        if (truePeak <= 0 || !truePeak.isFinite()) return capped
        val headroom = PEAK_CEILING_DB - 20 * log10(truePeak)
        return min(capped, headroom)
    }

    fun of(lufs: Double, truePeak: Double): Double = clamp(gainDb(lufs), truePeak)

    fun linear(gainDb: Double): Float = if (gainDb.isFinite()) 10.0.pow(gainDb / 20).toFloat() else 1f

    /** A record's loudness from its tracks': the length-weighted mean of their mean squares, not of their dB,
     * because loudness does not average in decibels. Tracks that nothing measured are left out. The result
     * is negative infinity when none was measured. A track of unknown length counts as one second. */
    fun albumLufs(tracks: List<Pair<Double, Double>>): Double {
        var sum = 0.0
        var weight = 0.0
        for ((lufs, seconds) in tracks) {
            if (!lufs.isFinite()) continue
            val length = if (seconds > 0) seconds else 1.0
            sum += 10.0.pow(lufs / 10) * length
            weight += length
        }
        return if (weight > 0) 10 * log10(sum / weight) else Double.NEGATIVE_INFINITY
    }
}

/**
 * What one stream is levelled from, worked out from the queue when the stream starts ([QueueRecord]): the
 * track's reading, its record's, whether the queue is that record played as a record, and whether the
 * record's owner marked it classical. Every field must be stated: a stream built without [classical]
 * would level every classical record and nothing would say so.
 */
data class StreamLoudness(val track: Level, val album: Level, val inAlbumOrder: Boolean, val classical: Boolean) {
    companion object {
        /** A station, or anything else with no reading: never levelled. */
        val NONE = StreamLoudness(Level.UNKNOWN, Level.UNKNOWN, inAlbumOrder = false, classical = false)
    }
}

/**
 * Whether a stream is levelled and by how much, in dB (the desktop's Normalization.Resolve and the iOS
 * app's `Normalization.resolve`). Off levels nothing. A record marked classical is left as it is unless
 * the setting says to level classical too. In album mode a record played in order takes its album's
 * reading where it is known. Otherwise the track's own reading is used, and a track that nobody
 * measured gets no gain. The TV app has no per-album answer: the desktop keeps that in its own library only.
 */
object Normalization {
    fun gainDb(mode: LevelMode, includeClassical: Boolean, stream: StreamLoudness): Double {
        if (mode == LevelMode.Off) return 0.0
        if (stream.classical && !includeClassical) return 0.0
        if (mode == LevelMode.Album && stream.inAlbumOrder && stream.album.known) return decide(stream.album)
        return if (stream.track.known) decide(stream.track) else 0.0
    }

    private fun decide(level: Level): Double = ReplayGain.of(level.lufs ?: 0.0, level.truePeak ?: 0.0)
}

/**
 * The person's levelling settings as the audio thread reads them. They are written on the main thread
 * by the app's settings (the TV's `PlayerSettings`), handed to [com.rpm.player.core.Services], and
 * read by [LevelSink] for every buffer, so a change reaches the record that is already playing.
 */
class Levelling(mode: LevelMode, includeClassical: Boolean) {
    @Volatile var mode: LevelMode = mode

    @Volatile var includeClassical: Boolean = includeClassical
}

/** Where a queued track carries its reading, so the sink can level it from the queue alone and a resumed
 * queue keeps it. Absent when nothing measured it. */
const val EXTRA_LUFS = "rpm.lufs"
const val EXTRA_PEAK = "rpm.peak"

/** Writes [level] into a queued item's extras. Nothing for what is not known. */
fun Bundle.putLevel(level: Level) {
    level.lufs?.takeIf { level.known }?.let { putDouble(EXTRA_LUFS, it) }
    level.truePeak?.let { putDouble(EXTRA_PEAK, it) }
}

/** A queued item's reading, from its extras. */
fun Bundle?.level(): Level {
    if (this == null) return Level.UNKNOWN
    val lufs = if (containsKey(EXTRA_LUFS)) getDouble(EXTRA_LUFS) else null
    val peak = if (containsKey(EXTRA_PEAK)) getDouble(EXTRA_PEAK) else null
    return Level(lufs, peak)
}

/** One queued item as the levelling sees it: which record it is on, its reading and its length. */
data class QueuedReading(val album: String, val albumArtist: String, val station: Boolean, val level: Level, val seconds: Double)

/**
 * A queue's record, if it is one (the iOS app's `relevelTheQueue`): a queue of one track, or of tracks
 * that all name the same album and album artist, is a record played as a record. Its reading is the
 * length-weighted mean of its tracks' readings, with the loudest peak among them. Any other queue, or
 * a queue that holds a station, is not a record, and its tracks are levelled one by one.
 */
object QueueRecord {
    data class Answer(val inAlbumOrder: Boolean, val album: Level)

    fun of(queue: List<QueuedReading>): Answer {
        if (queue.isEmpty() || queue.any { it.station }) return Answer(false, Level.UNKNOWN)
        val first = queue.first()
        val one = queue.all { it.album.equals(first.album, ignoreCase = true) && it.albumArtist.equals(first.albumArtist, ignoreCase = true) }
        if (!one) return Answer(false, Level.UNKNOWN)
        val readings = queue.filter { it.level.known }.map { it.level.lufs!! to it.seconds }
        if (readings.isEmpty()) return Answer(true, Level.UNKNOWN)
        val peak = queue.mapNotNull { it.level.truePeak }.filter { it > 0 }.maxOrNull()
        return Answer(true, Level(ReplayGain.albumLufs(readings), peak))
    }
}
