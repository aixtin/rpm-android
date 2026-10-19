package com.rpm.player.core.lyrics

import com.rpm.player.core.cloud.CloudClient
import com.rpm.player.core.playback.TrackRef
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** One word's time span in the recording, in seconds from the start of the track. */
@Serializable
data class MeasuredWord(val from: Double = 0.0, val to: Double = 0.0)

/** One lyric line as a desktop measured it: its place in the sheet, its time and text, and its words in
 * reading order. */
@Serializable
data class MeasuredLine(val index: Int = 0, val seconds: Double = 0.0, val text: String = "", val words: List<MeasuredWord> = emptyList())

/** One line of this device's sheet, sent so the recording can be timed against it. */
@Serializable
data class SheetLine(val index: Int, val seconds: Double, val text: String)

@Serializable
data class MeasureAsk(val lines: List<SheetLine>)

@Serializable
data class MeasuredAnswer(val lines: List<MeasuredLine> = emptyList(), val owner: String? = null)

/**
 * The word timing that a desktop measured from a track's recording (`cloud/lyric-timing/{rpmId}`),
 * used for karaoke on this device. A measured line is used only when it is the same line on this
 * device's sheet: the same text, the same time, and the same number of words ([forLine]). Every
 * other line uses the estimate from [LyricTiming.sweep].
 *
 * Read once per track. A track that nobody has measured, or a failed read, gives no measurement.
 */
class MeasuredLyrics(private val cloud: CloudClient) {
    suspend fun of(track: TrackRef): List<MeasuredLine> = try {
        cloud.lyricTiming(track)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** Asks the cloud to time [sheet]'s words from [track]'s recording (`cloud/lyric-timing/{rpmId}/measure`).
     * The transcoder does the work, and [of] reads the answer later. False when the request failed. */
    suspend fun ask(track: TrackRef, sheet: LyricSheet): Boolean = try {
        cloud.measureLyrics(track, MeasureAsk(sheet.lines.mapIndexed { i, line -> SheetLine(i, line.seconds, line.text) }))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    companion object {
        /** Whether [measured] covers most of [sheet]'s sung lines. If it does, no new measurement is needed. */
        fun covers(measured: List<MeasuredLine>, sheet: LyricSheet): Boolean {
            val sung = sheet.lines.filter { !LyricScore.isWordless(it.text) }
            if (sung.isEmpty()) return true
            val placed = sung.count { forLine(measured, it.text, it.seconds) != null }
            return placed * 4 >= sung.size * 3
        }

        /** How far apart two times may be and still belong to the same line. LRC stamps are kept to the hundredth. */
        private const val SAME_MOMENT = 0.05

        /** The measured words of the line [text] at [seconds], as spans of [text], or null when no
         * measured line matches. */
        fun forLine(measured: List<MeasuredLine>, text: String, seconds: Double): List<WordSpan>? {
            val line = measured.firstOrNull { it.text == text && kotlin.math.abs(it.seconds - seconds) < SAME_MOMENT } ?: return null
            val shape = LyricTiming.sweep(text, seconds, Double.POSITIVE_INFINITY)
            if (shape.size != line.words.size) return null
            return shape.mapIndexed { i, span -> span.copy(from = line.words[i].from, to = maxOf(line.words[i].from, line.words[i].to)) }
        }
    }
}
