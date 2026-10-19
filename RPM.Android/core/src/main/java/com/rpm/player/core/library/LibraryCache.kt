package com.rpm.player.core.library

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import java.io.ByteArrayInputStream
import com.rpm.player.core.cloud.CloudMoodLists
import com.rpm.player.core.cloud.CloudPlaylist
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.TrackPlaysRow
import com.rpm.player.core.net.WireJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import com.rpm.player.core.cloud.Shelf
import java.io.File
import java.security.MessageDigest

/** What the library is built from: the cloud's answers to the library's reads. [coverKeys] is null when
 * the cover index could not be read, in which case each album is asked about directly. */
@Serializable
data class CloudLibrary(
    val tracks: List<CloudTrack>,
    /** The cover index as older cached libraries hold it: the keys alone. Null in a library read now, and
     * used only when [coverStamps] is null. */
    val coverKeys: List<String>?,
    /** The cover index: each album key with a stored cover, and when that cover was written. Null when the
     * index could not be read (or in an older cached library). */
    val coverStamps: Map<String, Long>?,
    val plays: List<TrackPlaysRow>,
    val moods: CloudMoodLists?,
    val playlists: List<CloudPlaylist>,
    /** When the reads this was built from began, on this TV's clock (unix ms). What tells a playlist made
     * here after the read from one deleted elsewhere ([OwnEdits.over]). The default 0 is for a cached library
     * that has no value: it reads as older than anything made here. */
    val readAt: Long = 0,
) {
    val coverIndex: CoverIndex
        get() = coverStamps?.let { CoverIndex.Known(it) }
            ?: coverKeys?.let { keys -> CoverIndex.Known(keys.associateWith { 0L }) }
            ?: CoverIndex.Unknown
}

/**
 * The last library read from the cloud, kept on the TV per account and per shelf, so the next launch can
 * show it at once while the cloud is read again. A shelf another account shares has its own file, so it
 * never replaces the person's own.
 * Written through a temporary file and a rename, one write at a time; a file that does not parse is
 * ignored. Kept in the app's cache folder, which the system may empty: then the next launch waits for
 * the cloud.
 *
 * **The tracks are kept in [SLICES] slices, read side by side.** Parsing the whole library as one JSON
 * document takes about 5 s on a TV box even when compiled (14 MB, 22,865 tracks), all of it on one
 * core of four, before anything can be built. The file is one header line of byte lengths, then the
 * library without its tracks, then each slice of tracks as a JSON array. A file in the older layout, one
 * JSON document, is still read as one.
 */
@OptIn(ExperimentalSerializationApi::class)
class LibraryCache(private val dir: File) {

    private val writing = Mutex()

    private fun fileFor(user: String, shelf: Shelf): File {
        val who = user.filter { it.isLetterOrDigit() || it == '-' }
        return when (shelf) {
            Shelf.Own -> File(dir, "library-$who.json")
            is Shelf.Borrowed -> File(dir, "library-$who-shared-${shortHash(shelf.ownerId)}.json")
        }
    }

    /** The first eight bytes of the SHA-256 of [text], in hex: a file name that does not spell an account id. */
    private fun shortHash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    /** The cached library of [shelf] for [user], or null when there is none or it does not parse. */
    suspend fun read(user: String, shelf: Shelf): CloudLibrary? = withContext(Dispatchers.IO) {
        val file = fileFor(user, shelf)
        if (!file.exists()) return@withContext null
        try {
            val bytes = file.readBytes()
            if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
                // The older layout: one document.
                return@withContext WireJson.decodeFromStream(CloudLibrary.serializer(), ByteArrayInputStream(bytes))
            }
            readSliced(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** The sliced layout: the header's lengths, then each part parsed on its own core. */
    private suspend fun readSliced(bytes: ByteArray): CloudLibrary? = coroutineScope {
        val end = bytes.indexOf('\n'.code.toByte())
        val header = String(bytes, 0, end, Charsets.US_ASCII).split(' ')
        if (header.firstOrNull() != MAGIC) return@coroutineScope null
        val lengths = header.drop(1).map { it.toInt() }
        var at = end + 1
        val parts = lengths.map { length -> (at until at + length).also { at += length } }
        fun stream(part: IntRange) = ByteArrayInputStream(bytes, part.first, part.last - part.first + 1)
        val rest = async(Dispatchers.Default) { WireJson.decodeFromStream(CloudLibrary.serializer(), stream(parts.first())) }
        val slices = parts.drop(1).map { part ->
            async(Dispatchers.Default) { WireJson.decodeFromStream(TRACKS, stream(part)) }
        }
        rest.await().copy(tracks = slices.flatMap { it.await() })
    }

    suspend fun write(user: String, shelf: Shelf, library: CloudLibrary) = writing.withLock {
        withContext(Dispatchers.IO) {
            val file = fileFor(user, shelf)
            val temp = File(dir, file.name + ".tmp")
            try {
                val rest = WireJson.encodeToString(CloudLibrary.serializer(), library.copy(tracks = emptyList())).toByteArray(Charsets.UTF_8)
                val size = (library.tracks.size + SLICES - 1) / SLICES
                val slices = library.tracks.chunked(size.coerceAtLeast(1)).map { WireJson.encodeToString(TRACKS, it).toByteArray(Charsets.UTF_8) }
                val parts = listOf(rest) + slices
                temp.outputStream().buffered().use { out ->
                    out.write((MAGIC + parts.joinToString("") { " " + it.size } + "\n").toByteArray(Charsets.US_ASCII))
                    parts.forEach(out::write)
                }
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            } catch (e: CancellationException) {
                temp.delete()
                throw e
            } catch (_: Exception) {
                temp.delete()
            }
        }
    }

    private companion object {
        /** The first word of the sliced layout's header. */
        const val MAGIC = "RPMLIB2"

        /** How many slices the tracks are kept in: the cores of the boxes this is for. */
        const val SLICES = 4

        val TRACKS = ListSerializer(CloudTrack.serializer())
    }

    /** Forgets the cached library of [shelf] for [user]: a share withdrawn leaves nothing behind. */
    suspend fun delete(user: String, shelf: Shelf) = writing.withLock {
        withContext(Dispatchers.IO) { fileFor(user, shelf).delete() }
    }
}
