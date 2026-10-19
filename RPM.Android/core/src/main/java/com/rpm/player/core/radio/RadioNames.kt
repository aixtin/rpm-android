package com.rpm.player.core.radio

import java.text.Normalizer

/**
 * Station names as they are printed and as they are compared. Same rules as `RadioNames.cs` in
 * RPM.Core: [clean] for the card, [full] when two cards would otherwise print the same, [fold] for
 * matching two directory entries that are one station.
 */
object RadioNames {

    /** The headline of the name (up to the first spaced dash or bar) without codec and bitrate words. */
    fun clean(name: String?): String = strip(headline(name.orEmpty()), name)

    /** The whole name without codec and bitrate words. */
    fun full(name: String?): String = strip(name.orEmpty(), name)

    /** The tags that say what a station plays. Hash marks ("#HQ") and quality marks ("MP3 128k") are left out. */
    fun genres(tags: List<String>): List<String> =
        tags.map { it.trim() }.filter { it.isNotEmpty() && it[0] != '#' && !allQuality(it) }

    /** Gives the full name to every station whose clean name another listed station shares. A station
     * with a title set by the admin counts under that title and never gets a strapline. */
    fun distinguish(stations: List<RadioStation>): List<RadioStation> {
        if (stations.size < 2) return stations
        val heads = HashMap<String, Int>()
        for (station in stations) {
            val head = (station.edit?.name ?: clean(station.name)).lowercase()
            heads[head] = (heads[head] ?: 0) + 1
        }
        if (heads.values.none { it > 1 }) return stations
        return stations.map { station ->
            if (station.edit?.name != null) return@map station
            val head = clean(station.name)
            val whole = full(station.name)
            if ((heads[head.lowercase()] ?: 0) > 1 && head != whole) station.copy(shownName = whole) else station
        }
    }

    /**
     * The name reduced to what tells it apart: accents off, lower case, words joined. Filler words
     * ("radio", "fm", codec names) and the country's own name (after the first word) are left out.
     */
    fun fold(name: String?, countryCode: String?): String {
        val words = split(full(name))
        if (words.isEmpty()) return ""
        val geo = GEO[countryCode?.trim()?.uppercase().orEmpty()]
        var kept = words.filterIndexed { i, word -> word !in NOISE && !(i > 0 && geo != null && word in geo) }
        if (kept.isEmpty()) kept = words.filter { it !in NOISE }
        return (kept.ifEmpty { words }).joinToString("")
    }

    private fun split(text: String): List<String> {
        val words = ArrayList<String>()
        val word = StringBuilder(12)
        for (c in deaccent(text)) {
            if (c.isLetterOrDigit() || c == '+') {
                word.append(c.lowercaseChar())
                continue
            }
            if (word.isNotEmpty()) {
                words.add(word.toString())
                word.clear()
            }
        }
        if (word.isNotEmpty()) words.add(word.toString())
        return words
    }

    private fun deaccent(text: String): String =
        Normalizer.normalize(
            Normalizer.normalize(text, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() },
            Normalizer.Form.NFC,
        )

    private val NOISE = setOf(
        "radio", "radios", "fm", "am", "hd", "sd", "dab", "live", "stream", "streaming",
        "online", "net", "web", "webradio", "digital", "channel", "station", "the", "and",
        "mp3", "aac", "aacp", "ogg", "opus", "vorbis", "flac", "wma", "hls", "high", "low",
        "hq", "sq", "lq", "hifi", "kbps", "kbit", "kb", "quality", "stereo", "official",
    )

    private val GEO: Map<String, Set<String>> = mapOf(
        "NL" to setOf("nederland", "nederlandse", "netherlands", "holland", "dutch"),
        "GB" to setOf("uk", "britain", "british", "england", "unitedkingdom"),
        "US" to setOf("usa", "america", "american"),
        "DE" to setOf("deutschland", "germany", "german"),
        "FR" to setOf("france", "french"),
        "IT" to setOf("italia", "italy", "italian", "italiana"),
        "ES" to setOf("espana", "spain", "spanish"),
        "BE" to setOf("belgie", "belgium", "belgique"),
        "AT" to setOf("osterreich", "austria"),
        "CH" to setOf("schweiz", "switzerland", "suisse", "svizzera"),
        "SE" to setOf("sverige", "sweden"),
        "NO" to setOf("norge", "norway"),
        "DK" to setOf("danmark", "denmark"),
        "FI" to setOf("suomi", "finland"),
        "PT" to setOf("portugal", "portuguesa"),
        "PL" to setOf("polska", "poland"),
        "IE" to setOf("ireland", "eire"),
        "CA" to setOf("canada"),
        "AU" to setOf("australia"),
        "BR" to setOf("brasil", "brazil"),
        "MX" to setOf("mexico"),
        "IN" to setOf("india"),
    )

    /** Takes out every codec or bitrate marker. Returns the original if nothing would be left. */
    private fun strip(text: String, original: String?): String {
        if (text.isEmpty()) return original.orEmpty()
        val cut = StringBuilder(text.length)
        var at = 0
        while (at < text.length) {
            val end = marker(text, at)
            if (end != null) {
                at = end
                continue
            }
            cut.append(text[at])
            at++
        }
        val left = tidy(cut.toString())
        return left.ifEmpty { text }
    }

    private fun headline(name: String): String {
        var at = -1
        for (dash in DASHES) {
            val found = name.indexOf(dash)
            if (found > 0 && (at < 0 || found < at)) at = found
        }
        return if (at > 0) name.substring(0, at).trimEnd() else name
    }

    private val DASHES = listOf(" - ", " – ", " — ", " -- ", " | ")

    /** Where a codec or bitrate marker starting at [at] ends: "#FLAC", "(320k)", "MP3 128k". */
    private fun marker(text: String, at: Int): Int? {
        if (text[at] == '#') {
            var end = at + 1
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '+')) end++
            if (isQuality(text.substring(at + 1, end))) return if (end < text.length && text[end] == '#') end + 1 else end
            return null
        }
        val close = when (text[at]) {
            '(' -> ')'
            '[' -> ']'
            else -> '\u0000'
        }
        if (close != '\u0000') {
            val shut = text.indexOf(close, at + 1)
            return if (shut > at && allQuality(text.substring(at + 1, shut))) shut + 1 else null
        }
        // Inside a word, so not the start of a marker.
        if (!text[at].isLetterOrDigit() || (at > 0 && text[at - 1].isLetterOrDigit())) return null

        var run = at
        var last = -1
        var named = false
        while (run < text.length) {
            var word = run
            while (word < text.length && (text[word].isLetterOrDigit() || text[word] == '+')) word++
            if (word == run) break
            val it = text.substring(run, word)
            if (isQuality(it)) {
                named = true
            } else if (!isBitrate(it)) {
                break
            }
            last = word
            run = word
            while (run < text.length && (text[run] == ' ' || text[run] == '/')) run++
        }
        return if (named && last > at) last else null
    }

    private fun allQuality(inside: String): Boolean {
        var named = false
        for (word in inside.split(' ', '/', ',', '-').filter { it.isNotEmpty() }) {
            if (isQuality(word)) {
                named = true
            } else if (!isBitrate(word)) {
                return false
            }
        }
        return named
    }

    private fun isQuality(word: String): Boolean {
        val folded = word.trim().trimEnd('+').lowercase()
        if (folded.isEmpty()) return false
        if (folded in WORDS) return true
        for (unit in UNITS) {
            if (!folded.endsWith(unit)) continue
            val rate = folded.dropLast(unit.length)
            if (rate.isNotEmpty() && isBitrate(rate)) return true
        }
        return false
    }

    private fun isBitrate(word: String): Boolean {
        if (word.isEmpty() || word.any { it !in '0'..'9' }) return false
        val rate = word.toIntOrNull() ?: return false
        return rate in SLOWEST..FASTEST
    }

    private val UNITS = listOf("kbps", "kbit", "kbs", "kb", "k")

    private fun tidy(text: String): String {
        val run = StringBuilder(text.length)
        var space = false
        for (c in text) {
            if (c.isWhitespace()) {
                space = true
                continue
            }
            if (space && run.isNotEmpty()) run.append(' ')
            space = false
            run.append(c)
        }
        return run.toString().trim { it in DANGLERS }
    }

    private val DANGLERS = setOf(' ', '-', '–', '|', ',', ':', '/', '(', ')', '[', ']')

    private val WORDS = setOf(
        "mp3", "aac", "aacp", "aac+", "he-aac", "heaac", "ogg", "opus", "vorbis", "flac", "wma", "wav",
        "alac", "mp2", "m4a", "hls",
        "hq", "hd", "sq", "lq", "hifi", "hi-fi", "highquality", "lowquality", "lofi", "lo-fi",
        "kbps", "kbit", "kb", "bitrate", "stereo", "mono", "cd", "cdq", "quality",
    )

    private const val SLOWEST = 8
    private const val FASTEST = 2000
}
