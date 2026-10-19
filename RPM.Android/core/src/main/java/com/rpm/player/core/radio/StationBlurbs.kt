package com.rpm.player.core.radio

import com.rpm.player.core.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One short line saying what a station is, for the radio banner. The line is the description the admin
 * wrote where there is one ([RadioStation.edit], applied by [StationRules.shown]), else the
 * `og:description` of the station's front page, else its `meta name="description"`, cut to [LONGEST]
 * characters at a sentence end or a word (RPM.Core/Radio/StationBlurb.cs). The desktop asks Wikipedia when
 * the page says nothing; this does not, so such a station has no line. Asked once per station and kept
 * for the life of the app. Two asks while the first is still running share its answer.
 */
class StationBlurbs(private val http: OkHttpClient) {
    private val lock = Mutex()
    private val known = HashMap<String, CompletableDeferred<String?>>()

    suspend fun of(station: RadioStation): String? {
        // The admin's line is used as written, not through [shorten]: the admin keeps it within the same 80
        // characters, and [shorten] drops anything under 12. It is not cached with the pages' lines, so a
        // changed edit shows the next time the card asks (the callers key on it).
        station.edit?.description?.let { return it }
        val page = station.homepage?.takeIf { it.startsWith("http") } ?: return null
        val key = station.uuid.lowercase()
        val (asked, mine) = lock.withLock {
            known[key]?.let { it to false } ?: CompletableDeferred<String?>().also { known[key] = it }.let { it to true }
        }
        if (!mine) return asked.await()
        val line = try {
            read(page)
        } catch (e: CancellationException) {
            lock.withLock { known.remove(key) }
            asked.complete(null)
            throw e
        }
        asked.complete(line)
        return line
    }

    private suspend fun read(page: String): String? = try {
        val request = Request.Builder().url(page).header("User-Agent", RadioBrowser.USER_AGENT).build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) return null
            val source = response.body.source()
            source.request(HEAD_READ.toLong())
            val head = source.buffer.snapshot().utf8().take(HEAD_READ)
            fromPage(head)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        /** About two lines of the banner's 300-wide box. */
        const val LONGEST = 80
        private const val SHORTEST = 12
        private const val HEAD_READ = 64 * 1024
        private val META = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("([a-zA-Z:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")

        /** The page's own description: Open Graph first, then the plain one. */
        fun fromPage(html: String): String? {
            var social: String? = null
            var plain: String? = null
            for (tag in META.findAll(html)) {
                val attributes = ATTRIBUTE.findAll(tag.value).associate { m ->
                    m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value.orEmpty())
                }
                val what = attributes["property"] ?: attributes["name"] ?: continue
                val content = attributes["content"] ?: continue
                if (social == null && what.equals("og:description", ignoreCase = true)) social = content
                else if (plain == null && what.equals("description", ignoreCase = true)) plain = content
            }
            return shorten(social) ?: shorten(plain)
        }

        /** One line of at most [LONGEST]: whole if it fits, else to the last sentence end, else to a word with an ellipsis. */
        fun shorten(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val line = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                .replace(Regex("\\s+"), " ").trim()
            if (line.length < SHORTEST) return null
            if (line.length <= LONGEST) return line
            val sentence = (LONGEST downTo SHORTEST).firstOrNull { i -> i <= line.length && line[i - 1] in ".!?" && (i == line.length || line[i] == ' ') }
            if (sentence != null) return line.substring(0, sentence).trimEnd()
            val word = line.lastIndexOf(' ', LONGEST - 1)
            return (if (word >= SHORTEST) line.substring(0, word) else line.substring(0, LONGEST - 1)).trimEnd() + "\u2026"
        }
    }
}
