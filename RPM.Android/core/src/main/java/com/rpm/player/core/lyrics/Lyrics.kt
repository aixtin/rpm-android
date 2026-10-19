package com.rpm.player.core.lyrics

import com.rpm.player.core.net.await
import com.rpm.player.core.net.escapeSegment
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** One timed line of a song. */
data class LyricLine(val seconds: Double, val text: String)

/**
 * A song's synced lyrics, parsed from LRC. It matches the iOS app's `LyricSheet` (RPMKit) and keeps
 * line timings only. Word timings are dropped, because the TV app highlights one line at a time.
 */
data class LyricSheet(val lines: List<LyricLine>) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    /** The line being sung at [seconds]: the last one that has started, or null before the first. */
    fun index(seconds: Double): Int? {
        var low = 0
        var high = lines.size - 1
        var found: Int? = null
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].seconds <= seconds) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    companion object {
        val EMPTY = LyricSheet(emptyList())

        private val STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val OFFSET = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""")
        private val WORD_STAMP = Regex("""\s*<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>\s*""")
        private val LOOSE_STAMP =
            Regex("""^\s*(?:\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]|\(\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\)|\d{1,3}:\d{1,2}[.:]\d{1,3})\s*""")

        fun parse(lrc: String?): LyricSheet {
            if (lrc.isNullOrBlank()) return EMPTY
            val lines = mutableListOf<LyricLine>()
            var offset = 0.0
            for (raw in lrc.split('\n')) {
                val line = raw.trimEnd('\r')
                OFFSET.find(line)?.let { match ->
                    match.groupValues[1].toDoubleOrNull()?.let { offset = it / 1000 }
                    continue
                }
                val stamps = STAMP.findAll(line).toList()
                // A metadata tag, or text in a file that is not an LRC.
                if (stamps.isEmpty()) continue
                val text = unstamp(line.substring(stamps.last().range.last + 1))
                for (stamp in stamps) lines += LyricLine(maxOf(0.0, seconds(stamp) - offset), text)
            }
            return if (lines.isEmpty()) EMPTY else LyricSheet(lines.sortedBy { it.seconds })
        }

        private fun unstamp(line: String): String =
            LOOSE_STAMP.replace(WORD_STAMP.replace(line, " "), "").trim()

        private fun seconds(stamp: MatchResult): Double {
            val minutes = stamp.groupValues[1].toDoubleOrNull() ?: 0.0
            val seconds = stamp.groupValues[2].toDoubleOrNull() ?: 0.0
            val said = stamp.groupValues[3]
            val fraction = if (said.isEmpty()) 0.0 else (said.toDoubleOrNull() ?: 0.0) / 10.0.pow(said.length)
            return minutes * 60 + seconds + fraction
        }
    }
}

/**
 * Lyrics from LRCLIB, asked the same way as the iOS app's `LrcLibClient`: first `get` with the
 * track's name, artist, album and length, then `search`. The first candidate within [SLACK_S]
 * seconds of the length that has synced lyrics is used. Any failure means no lyrics.
 */
class LrcLib(private val http: OkHttpClient) {

    suspend fun sheet(artist: String?, title: String?, album: String?, durationSeconds: Double): LyricSheet {
        // Without a title there is nothing to look up.
        if (title.isNullOrBlank()) return LyricSheet.EMPTY
        if (durationSeconds > 0) {
            val exact = ask(
                "get?track_name=${escapeSegment(title)}&artist_name=${escapeSegment(artist.orEmpty())}" +
                    "&album_name=${escapeSegment(album.orEmpty())}&duration=${durationSeconds.roundToInt()}",
            )
            val sheet = (exact as? JsonObject)?.let(::read) ?: LyricSheet.EMPTY
            if (sheet.isSynced) return sheet
        }
        val found = ask("search?track_name=${escapeSegment(title)}&artist_name=${escapeSegment(artist.orEmpty())}")
        for (candidate in (found as? JsonArray).orEmpty()) {
            val record = candidate as? JsonObject ?: continue
            val theirs = record["duration"]?.jsonPrimitive?.doubleOrNull
            if (durationSeconds > 0 && theirs != null && abs(theirs - durationSeconds) > SLACK_S) continue
            val sheet = read(record)
            if (sheet.isSynced) return sheet
        }
        return LyricSheet.EMPTY
    }

    private fun read(record: JsonObject): LyricSheet =
        LyricSheet.parse(record["syncedLyrics"]?.jsonPrimitive?.contentOrNull)

    /** The answer as JSON, or null on any failure. A cancellation is passed on, not swallowed: a
     * lookup cancelled by a change of track must stop, not finish with an empty answer. */
    private suspend fun ask(path: String): JsonElement? = try {
        val request = Request.Builder().url(BASE + path).header("User-Agent", USER_AGENT).build()
        http.newCall(request).await().use { response ->
            if (response.code !in 200..299) null else Json.parseToJsonElement(response.body.string())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val BASE = "https://lrclib.net/api/"
        const val USER_AGENT = "RPM/1.0 (https://rpmrpmrpm.com)"
        const val SLACK_S = 2.5
    }
}
