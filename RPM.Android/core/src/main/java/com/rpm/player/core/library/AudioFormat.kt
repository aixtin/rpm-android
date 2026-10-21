package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrackTags

/**
 * What a file is, in the short form a record sleeve prints: "FLAC", "24/96", "5.1". It is the iPhone's
 * `AudioFormat`, so the line under a record's count says the same on both phones.
 *
 * The codec is mostly the extension, except `.m4a`, which holds either AAC or Apple Lossless: there the decoder's
 * own description decides. A lossless file is judged by its resolution and a lossy one by its bitrate. Stereo is
 * left out, because a line that says "stereo" on every record says nothing about any of them.
 */
object AudioFormat {
    /** Whether the row says anything about its format at all. */
    fun hasFormat(tags: CloudTrackTags): Boolean = !tags.extension.isNullOrBlank() || tags.channels > 0 || tags.sampleRate > 0

    /** Everything worth saying, in reading order, leaving out what is unknown. [mono] is the word for one channel. */
    fun describe(tags: CloudTrackTags, mono: String): List<String> {
        if (!hasFormat(tags)) return emptyList()
        val parts = ArrayList<String>(3)
        val name = codec(tags.extension, tags.codecDescription)
        if (name != null) parts += name
        quality(name, tags.bitsPerSample, tags.sampleRate, tags.bitrateKbps)?.let { parts += it }
        channels(tags.channels, mono)?.let { parts += it }
        return parts
    }

    /** The record's line: its first track with a format answers for the whole record, as a sleeve does. */
    fun of(tracks: List<com.rpm.player.core.cloud.CloudTrack>, mono: String): String =
        tracks.firstNotNullOfOrNull { t -> t.tags?.takeIf { hasFormat(it) } }?.let { describe(it, mono).joinToString("  ·  ") }.orEmpty()

    fun codec(extension: String?, description: String?): String? {
        val trimmed = extension.orEmpty().trim().lowercase()
        if (trimmed.isEmpty()) return null
        val ext = if (trimmed.startsWith(".")) trimmed else ".$trimmed"
        if (ext in MPEG4) {
            if (!description.isNullOrEmpty()) {
                if (says(description, "alac", "apple lossless")) return "ALAC"
                // Before plain AC-3, because "eac3" contains "ac3".
                if (says(description, "eac3", "e-ac-3", "ec-3")) return "Dolby Digital Plus"
                if (says(description, "ac3", "ac-3")) return "Dolby Digital"
            }
            return "AAC"
        }
        return when (ext) {
            ".flac" -> "FLAC"
            ".mp3" -> "MP3"
            ".wav" -> "WAV"
            ".aif", ".aiff", ".aifc" -> "AIFF"
            ".ogg", ".oga" -> "OGG"
            ".opus" -> "Opus"
            ".wma" -> "WMA"
            ".ape" -> "APE"
            ".wv" -> "WavPack"
            ".mpc" -> "Musepack"
            ".dsf", ".dff" -> "DSD"
            ".alac" -> "ALAC"
            else -> null
        }
    }

    /** The layout as people say it, or null for stereo and for a count with no common name. */
    fun channels(count: Int, mono: String): String? = when (count) {
        1 -> mono
        3 -> "2.1"
        4 -> "4.0"
        5 -> "5.0"
        6 -> "5.1"
        7 -> "6.1"
        8 -> "7.1"
        10 -> "9.1"
        12 -> "7.1.4"
        else -> null
    }

    fun quality(codec: String?, bitsPerSample: Int, sampleRate: Int, bitrateKbps: Int): String? {
        if (codec !in LOSSLESS) return if (bitrateKbps > 0) "$bitrateKbps kbps" else null
        if (bitsPerSample <= 0 || sampleRate <= 0) return null
        val khz = sampleRate / 1000.0
        val said = if (kotlin.math.abs(khz - Math.round(khz)) < 0.05) Math.round(khz).toString() else "%.1f".format(java.util.Locale.ROOT, khz)
        return "$bitsPerSample/$said"
    }

    private val MPEG4 = setOf(".m4a", ".m4b", ".mp4", ".aac")
    private val LOSSLESS = setOf("FLAC", "ALAC", "WAV", "AIFF", "APE", "WavPack", "DSD")

    private fun says(description: String, vararg words: String): Boolean = words.any { description.contains(it, ignoreCase = true) }
}
