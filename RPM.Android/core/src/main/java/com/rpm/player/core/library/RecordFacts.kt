package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack

/** A record's file format and quality as shown on its page: "FLAC" and "24/96", or "MP3" and "320 kbps". */
data class RecordFormat(val format: String, val quality: String?)

/** What a record's page says about it beyond its names, read off its tracks' tags. */
object RecordFacts {
    /** The year most of the record's tracks give, or null when none gives one. */
    fun year(tracks: List<CloudTrack>): Int? = mostOf(tracks.mapNotNull { it.tags?.year?.takeIf { y -> y > 0 } })

    /**
     * The format most of the record's tracks are in, by their file extension in capitals, with their bit depth
     * and sample rate in kHz ("24/96", "16/44.1"), or their bitrate for a file without a bit depth ("320 kbps").
     * The commonest pairing is taken, so one odd bonus track does not change the line. Null when no track says.
     */
    fun format(tracks: List<CloudTrack>): RecordFormat? = mostOf(
        tracks.mapNotNull { track ->
            val tags = track.tags ?: return@mapNotNull null
            val format = tags.extension?.trim()?.trimStart('.')?.uppercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val quality = when {
                tags.bitsPerSample > 0 && tags.sampleRate > 0 -> "${tags.bitsPerSample}/${kilohertz(tags.sampleRate)}"
                tags.bitrateKbps > 0 -> "${tags.bitrateKbps} kbps"
                else -> null
            }
            RecordFormat(format, quality)
        },
    )

    /** [hz] in kHz with one decimal where it has one: 96000 is "96", 44100 is "44.1". */
    fun kilohertz(hz: Int): String = if (hz % 1000 == 0) (hz / 1000).toString() else "%.1f".format(java.util.Locale.ROOT, hz / 1000.0)

    private fun <T> mostOf(values: List<T>): T? = values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}
