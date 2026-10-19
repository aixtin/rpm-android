package com.rpm.player.core.radio

import com.rpm.player.core.net.WireJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** How long a station has been heard: this device's running totals, or another device's. */
@Serializable
data class ListenRow(val station: RadioStation, val heardSeconds: Double, val tunes: Int, val lastHeardAt: Long)

/**
 * The stations heard on this TV, and on the person's other devices, as running totals per station per
 * device. Same rules as `RadioListens.cs` in RPM.Core: this device keeps its 200 most heard and up to
 * 800 rows from other devices, and [top] adds every device's totals together. Everything is kept in
 * one file, written through a temporary file. Every change runs under one mutex on IO. A file that
 * cannot be read is never overwritten.
 */
class StationListens(private val file: File, private val scope: CoroutineScope, private val clock: () -> Long) {

    @Serializable
    private data class TheirRow(val device: String, val listen: ListenRow)

    @Serializable
    private data class SentRow(val uuid: String, val heardSeconds: Double, val tunes: Int)

    @Serializable
    private data class Model(
        val heard: List<ListenRow> = emptyList(),
        val device: String? = null,
        val account: String? = null,
        val cursor: Long = 0,
        val sent: List<SentRow> = emptyList(),
        val theirs: List<TheirRow> = emptyList(),
    )

    private val mutex = Mutex()
    private var read = false
    private var mayWrite = true
    private var heard = LinkedHashMap<String, ListenRow>()
    private var theirs = LinkedHashMap<Pair<String, String>, ListenRow>()
    private var sent = HashMap<String, Pair<Double, Int>>()
    private var device = ""
    private var account: String? = null
    private var cursor = 0L

    private val _top = MutableStateFlow<List<ListenRow>>(emptyList())

    /** Every station heard, every device's totals added together, most heard first. */
    val top: StateFlow<List<ListenRow>> = _top.asStateFlow()

    fun load() {
        scope.launch(Dispatchers.IO) { mutex.withLock { readOnce() } }
    }

    /** Adds [seconds] heard on [station]; [newTune] counts one more tune. A part under 30 s with no
     * listen before it is not a listen, which the counter decides before calling. */
    fun note(station: RadioStation, seconds: Double, newTune: Boolean) {
        if (seconds <= 0 || station.uuid.isBlank()) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                readOnce()
                val key = station.uuid.lowercase()
                val was = heard[key]
                heard[key] = ListenRow(
                    station = station.copy(shownName = null, otherLogos = emptyList(), alsoFiledAs = emptyList()),
                    heardSeconds = (was?.heardSeconds ?: 0.0) + seconds,
                    tunes = (was?.tunes ?: 0) + if (newTune) 1 else 0,
                    lastHeardAt = clock() / 1000,
                )
                trim()
                changed()
            }
        }
    }

    /** What a sync pass reads and writes, all under the log's mutex. */
    suspend fun <T> forSync(block: SyncView.() -> T): T = mutex.withLock {
        readOnce()
        SyncView().block()
    }

    /** The log as a sync pass sees it. Only reachable inside [forSync], so every step holds the mutex. */
    inner class SyncView internal constructor() {
        /** This device's id, or null when the file cannot be written (an id that is not kept would give
         * a new device on every launch). */
        val device: String? get() = this@StationListens.device.takeIf { mayWrite && it.isNotEmpty() }
        val cursor: Long get() = this@StationListens.cursor

        /** Starts over for [userId] when the log was last synced for someone else: the cursor, the sent
         * marks and the other devices' rows go, and this device's own totals stay. */
        fun forAccount(userId: String) {
            if (account == userId) return
            account = userId
            this@StationListens.cursor = 0
            sent.clear()
            theirs.clear()
            changed()
        }

        /** Takes the other devices' rows. This device's own rows, and rows identical to what is held, are skipped. */
        fun apply(rows: List<StationListenWire>) {
            var taken = 0
            for (row in rows) {
                val station = row.station ?: continue
                if (row.deviceId.isBlank() || row.deviceId == this@StationListens.device || station.uuid.isBlank() || station.streamUrl.isBlank()) continue
                val next = ListenRow(station, row.heardSeconds.coerceAtLeast(0.0), row.tunes.coerceAtLeast(0), row.lastHeardAt.coerceAtLeast(0))
                val key = station.uuid.lowercase() to row.deviceId
                if (theirs[key] == next) continue
                theirs[key] = next
                taken++
            }
            if (taken > 0) {
                trimTheirs()
                changed()
            }
        }

        fun noteCursor(stamp: Long) {
            if (stamp <= this@StationListens.cursor) return
            this@StationListens.cursor = stamp
            changed()
        }

        /** This device's rows whose totals differ from what was last sent, most recently heard first. */
        fun unsent(limit: Int): List<ListenRow> = heard.values
            .filter { row -> sent[row.station.uuid.lowercase()] != (row.heardSeconds to row.tunes) }
            .sortedByDescending { it.lastHeardAt }
            .take(limit)

        /** Marks the values that were sent, so a part added since still differs and is sent next time. */
        fun noteSent(rows: List<ListenRow>) {
            for (row in rows) sent[row.station.uuid.lowercase()] = row.heardSeconds to row.tunes
            changed()
        }
    }

    private fun readOnce() {
        if (read) return
        read = true
        val model = if (file.exists()) {
            try {
                WireJson.decodeFromString(Model.serializer(), file.readText())
            } catch (_: Exception) {
                mayWrite = false
                null
            }
        } else {
            null
        }
        model?.heard?.forEach { heard[it.station.uuid.lowercase()] = it }
        model?.theirs?.forEach { theirs[it.listen.station.uuid.lowercase() to it.device] = it.listen }
        model?.sent?.forEach { sent[it.uuid.lowercase()] = it.heardSeconds to it.tunes }
        cursor = model?.cursor ?: 0
        account = model?.account
        device = model?.device.orEmpty()
        if (device.isEmpty() && mayWrite) {
            device = UUID.randomUUID().toString().replace("-", "")
            save()
        }
        publish()
    }

    private fun changed() {
        save()
        publish()
    }

    private fun publish() {
        _top.value = (heard.values + theirs.values)
            .groupBy { it.station.uuid.lowercase() }
            .map { (_, rows) ->
                val newest = rows.maxBy { it.lastHeardAt }
                ListenRow(newest.station, rows.sumOf { it.heardSeconds }, rows.sumOf { it.tunes }, newest.lastHeardAt)
            }
            .sortedWith(compareByDescending<ListenRow> { it.heardSeconds }.thenByDescending { it.lastHeardAt })
    }

    private fun trim() {
        if (heard.size <= KEPT) return
        val kept = heard.entries.sortedWith(
            compareByDescending<Map.Entry<String, ListenRow>> { it.value.heardSeconds }.thenByDescending { it.value.lastHeardAt },
        ).take(KEPT)
        heard = LinkedHashMap(kept.associate { it.key to it.value })
        sent.keys.retainAll(heard.keys)
    }

    private fun trimTheirs() {
        if (theirs.size <= KEPT * 4) return
        val kept = theirs.entries.sortedWith(
            compareByDescending<Map.Entry<Pair<String, String>, ListenRow>> { it.value.heardSeconds }.thenByDescending { it.value.lastHeardAt },
        ).take(KEPT * 4)
        theirs = LinkedHashMap(kept.associate { it.key to it.value })
    }

    private fun save() {
        if (!mayWrite) return
        try {
            val model = Model(
                heard = heard.values.toList(),
                device = device.ifEmpty { null },
                account = account,
                cursor = cursor,
                sent = sent.map { (uuid, value) -> SentRow(uuid, value.first, value.second) },
                theirs = theirs.map { (key, listen) -> TheirRow(key.second, listen) },
            )
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(WireJson.encodeToString(Model.serializer(), model))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val KEPT = 200

        /** Heard for less than this, a tune is not a listen (the desktop's `NoticedSeconds`). */
        const val NOTICED_SECONDS = 30.0
    }
}

/** One row of `GET cloud/station-listens`: a device's running totals for one station. */
@Serializable
data class StationListenWire(
    val uuid: String = "",
    val deviceId: String = "",
    val heardSeconds: Double = 0.0,
    val tunes: Int = 0,
    val lastHeardAt: Long = 0,
    val station: RadioStation? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class StationListensPage(val stations: List<StationListenWire> = emptyList())

/** The body of `PUT cloud/station-listens`: this device's running totals, written whole per row. */
@Serializable
data class StationListensReport(val deviceId: String, val stations: List<StationListenOut>)

@Serializable
data class StationListenOut(val uuid: String, val heardSeconds: Double, val tunes: Int, val lastHeardAt: Long, val station: StationOut)

/** A station as the API stores it with a listen; exactly the fields its contract names. */
@Serializable
data class StationOut(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val homepage: String?,
    val favicon: String?,
    val country: String?,
    val countryCode: String?,
    val language: String?,
    val tags: List<String>,
    val codec: String?,
    val bitrateKbps: Int,
    val isHls: Boolean,
) {
    companion object {
        /** Held to the API's limits (text 2048 characters, 50 tags of 200). A station over them would make
         * the API refuse the whole report. */
        fun of(s: RadioStation) = StationOut(
            s.uuid, s.name.take(TEXT), s.streamUrl.take(TEXT), s.homepage?.take(TEXT), s.favicon?.take(TEXT),
            s.country?.take(TEXT), s.countryCode?.take(TEXT), s.language?.take(TEXT),
            s.tags.take(50).map { it.take(200) }, s.codec?.take(TEXT), s.bitrateKbps, s.isHls,
        )

        private const val TEXT = 2048
    }
}

@Serializable
data class StationListensUpdated(val updatedAt: Long = 0)
