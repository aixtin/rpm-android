package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import java.security.MessageDigest

/** Which picture of a record a key names. A disc label is filed apart from the cover. */
enum class RecordPicture(val stamp: String) {
    COVER(""),
    DISC("disc|"),
}

/**
 * The key the cloud stores an album's pictures under: the first 16 bytes, in hex, of SHA-256 of
 * `stamp + normalize(albumArtist) | normalize(album)`. It must give the same key as the desktop and
 * the iOS app (`AlbumArtKey`). A different answer here means no cover, so the tests pin the same two
 * values.
 */
object AlbumArtKey {

    fun of(picture: RecordPicture, albumArtist: String?, album: String?): String =
        digest(picture.stamp + normalize(albumArtist) + "|" + normalize(album))

    /** A blank album artist is not an album artist; the track's artist names the record instead. */
    fun forTrack(picture: RecordPicture, albumArtist: String?, artist: String?, album: String?): String =
        of(picture, if (isBlank(albumArtist)) artist else albumArtist, album)

    /** A compilation can be filed under its album artist or under the track's artist, depending on
     * which computer uploaded the cover, so both are asked about, album artist first. */
    fun spellings(picture: RecordPicture, albumArtist: String?, artist: String?, album: String?): List<String> {
        val byAlbumArtist = forTrack(picture, albumArtist, artist, album)
        val byArtist = of(picture, artist, album)
        return if (byAlbumArtist == byArtist) listOf(byAlbumArtist) else listOf(byAlbumArtist, byArtist)
    }

    /** Every spelling a record's tracks know, each once, in the order the tracks first name them. The
     * tracks of a record mostly carry the same names, so each set of names is hashed once. */
    fun spellings(picture: RecordPicture, tracks: List<CloudTrack>): List<String> {
        val seen = LinkedHashSet<String>()
        val asked = HashSet<Triple<String?, String?, String?>>()
        for (track in tracks) {
            val names = Triple(track.tags?.albumArtist, track.artist, track.album)
            if (asked.add(names)) seen += spellings(picture, names.first, names.second, names.third)
        }
        return seen.toList()
    }

    private fun normalize(value: String?) = (value ?: "").trim().lowercase()

    private fun isBlank(value: String?) = value == null || value.isBlank()

    /** The first 16 bytes of SHA-256, in lower-case hex. One digest per thread, reused (`digest`
     * resets it), and hex from a table. A new digest and `String.format` per byte would take seconds on
     * a TV box with 22,865 tracks. */
    private fun digest(text: String): String {
        val bytes = SHA.get()!!.digest(text.toByteArray(Charsets.UTF_8))
        val out = CharArray(32)
        for (i in 0 until 16) {
            val b = bytes[i].toInt() and 0xFF
            out[2 * i] = HEX[b ushr 4]
            out[2 * i + 1] = HEX[b and 0x0F]
        }
        return String(out)
    }

    private val SHA: ThreadLocal<MessageDigest> = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
    private val HEX = "0123456789abcdef".toCharArray()
}
