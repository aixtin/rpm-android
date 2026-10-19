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
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * The stations the person keeps, in sync with their other devices ([FavoritesSync], `cloud/station-favorites`).
 * Saved whole, so a favourite still plays after the directory drops it.
 *
 * Held as a [FavoriteLedger] in [ledgerFile]: every save and unsave with the time it was made, which is
 * what the sync sends and merges. The list itself is also written to [file], in the iOS app's shape (a
 * bare JSON array of stations), which older builds of this app read. A device's first ledger is made
 * from that list.
 *
 * Every change runs under one mutex, after the files have been read, and writes what it produced. A
 * file that cannot be read is never overwritten. Changes then last until the app closes.
 */
class RadioFavorites(
    private val file: File,
    private val ledgerFile: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private var read = false
    private var unreadable = false
    private var ledger = FavoriteLedger()

    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    private val _unsent = MutableStateFlow(false)

    /** Whether a change made here has not been accepted by the cloud yet. [FavoritesSync] sends when it turns true. */
    val unsent: StateFlow<Boolean> = _unsent.asStateFlow()

    /** Reads the files once; later calls do nothing. */
    fun load() {
        scope.launch(Dispatchers.IO) { mutex.withLock { readOnce() } }
    }

    /** Saves [station] at the end of the list, or takes it out when it is already kept. */
    fun toggle(station: RadioStation) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                readOnce()
                // Saved without what folding added. That is never saved.
                val plain = station.copy(shownName = null, otherLogos = emptyList(), alsoFiledAs = emptyList())
                settle(ledger.changed(plain, saved = !ledger.holds(station.uuid), now = clock()))
            }
        }
    }

    fun holds(uuid: String): Boolean = _stations.value.any { it.uuid.equals(uuid, ignoreCase = true) }

    /** Runs [block] on the ledger under the lock, for the sync. Never held across a request. */
    suspend fun <T> forSync(block: SyncView.() -> T): T = mutex.withLock {
        readOnce()
        SyncView().block()
    }

    /** What the sync may do to the ledger. */
    inner class SyncView {
        val cursor: Long get() = ledger.cursor

        fun forAccount(userId: String) = settle(ledger.forAccount(userId))

        fun take(rows: List<FavoriteRow>) = settle(ledger.taking(rows))

        fun toSend(most: Int): Map<String, FavoriteChange> = ledger.toSend(most)

        fun noteSent(sent: Map<String, FavoriteChange>) = settle(ledger.sent(sent))
    }

    /** Holds [next], shows its list, and writes it, where it differs from what is held. Called under the lock. */
    private fun settle(next: FavoriteLedger) {
        if (next == ledger) return
        ledger = next
        _stations.value = next.saved
        _unsent.value = next.unsent.isNotEmpty()
        write(next)
    }

    private fun readOnce() {
        if (read) return
        read = true
        try {
            ledger = when {
                ledgerFile.exists() -> WireJson.decodeFromString(FavoriteLedger.serializer(), ledgerFile.readText())
                file.exists() -> FavoriteLedger.of(WireJson.decodeFromString(ListSerializer(RadioStation.serializer()), file.readText()))
                else -> FavoriteLedger()
            }
        } catch (_: Exception) {
            unreadable = true
        }
        _stations.value = ledger.saved
        _unsent.value = ledger.unsent.isNotEmpty()
    }

    private fun write(next: FavoriteLedger) {
        if (unreadable) return
        replace(ledgerFile, WireJson.encodeToString(FavoriteLedger.serializer(), next))
        replace(file, WireJson.encodeToString(ListSerializer(RadioStation.serializer()), next.saved))
    }

    /** Written through a temporary file and a rename, so a write that is cut short leaves the old file whole. */
    private fun replace(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }
}
