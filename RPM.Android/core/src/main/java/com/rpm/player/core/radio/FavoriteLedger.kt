package com.rpm.player.core.radio

import kotlinx.serialization.Serializable

/** The newest change to one station's place in the saved stations: saved or not, when it was made (by the
 * clock of the device that made it, in Unix milliseconds), and the station itself. */
@Serializable
data class FavoriteChange(val saved: Boolean, val changedAt: Long, val station: RadioStation)

/**
 * The saved stations as changes, so they can be kept in sync with the person's other devices through
 * `cloud/station-favorites`: one change per station (keyed by its id, lower case), the newest of all
 * changes made to it anywhere. The rule is the same as in the API's store, the desktop and the iOS app:
 *
 * - The newest change to a station stands, judged by the clocks of the devices that made the changes.
 *   An unsave is kept as a change too, so it reaches the other devices and is not undone by their copies.
 * - The saved stations are listed in the order they were saved, the same on every device.
 * - A device's first ledger is its list as it was, at its places (0, 1, 2…), older than any real change.
 * - A ledger belongs to one account. When another account signs in, it starts an empty ledger: the
 *   last account's stations are in the cloud under that account, and are not handed to the next.
 *
 * Immutable: every change is a new ledger, made under [RadioFavorites]' lock.
 */
@Serializable
data class FavoriteLedger(
    /** Whose ledger this is, or null before anyone has synced it. */
    val account: String? = null,
    /** The server stamp that everything up to has been taken in. */
    val cursor: Long = 0,
    val changes: Map<String, FavoriteChange> = emptyMap(),
    /** The stations whose change this device has not had accepted by the cloud yet. */
    val unsent: Set<String> = emptySet(),
) {
    /** The saved stations, in the order they were saved. */
    val saved: List<RadioStation>
        get() = changes.entries.filter { it.value.saved }
            .sortedWith(compareBy<Map.Entry<String, FavoriteChange>> { it.value.changedAt }.thenBy { it.key })
            .map { it.value.station }

    fun holds(uuid: String): Boolean = changes[uuid.lowercase()]?.saved == true

    /** The station saved or unsaved here, at [now], or just after the change it replaces, so the new change
     * is newer whatever this device's clock says. */
    fun changed(station: RadioStation, saved: Boolean, now: Long): FavoriteLedger {
        val key = station.uuid.lowercase()
        val at = maxOf(now, (changes[key]?.changedAt ?: -1) + 1)
        return copy(changes = changes + (key to FavoriteChange(saved, at, station)), unsent = unsent + key)
    }

    /** The ledger that [userId] syncs: this one, claimed, when nobody has synced it yet or [userId] did; an
     * empty one when another account did. */
    fun forAccount(userId: String): FavoriteLedger = when (account) {
        userId -> this
        null -> copy(account = userId)
        else -> FavoriteLedger(account = userId)
    }

    /** Takes the rows the cloud listed: each row that is newer than what is held replaces it, and is no
     * longer this device's to send. The cursor moves to the newest row that arrived. */
    fun taking(rows: List<FavoriteRow>): FavoriteLedger {
        var next = changes
        var waiting = unsent
        for (row in rows) {
            val station = row.station ?: continue
            if (row.uuid.isBlank() || station.streamUrl.isBlank()) continue
            val key = row.uuid.lowercase()
            val held = next[key]
            if (held != null && held.changedAt >= row.changedAt) continue
            next = next + (key to FavoriteChange(row.saved, row.changedAt, station))
            waiting = waiting - key
        }
        val newest = rows.maxOfOrNull { it.updatedAt } ?: cursor
        return copy(changes = next, unsent = waiting, cursor = maxOf(cursor, newest))
    }

    /** What to send: the unsent changes, at most [most]. */
    fun toSend(most: Int): Map<String, FavoriteChange> =
        unsent.asSequence().mapNotNull { key -> changes[key]?.let { key to it } }.take(most).toMap()

    /** Marks [sent] accepted: each leaves the unsent set unless it was changed again while it was out. */
    fun sent(sent: Map<String, FavoriteChange>): FavoriteLedger =
        copy(unsent = unsent.filterNot { key -> sent[key]?.let { it.changedAt == changes[key]?.changedAt } == true }.toSet())

    companion object {
        /** A device's first ledger: its list as it was, oldest first, at its places, none of it sent. */
        fun of(list: List<RadioStation>): FavoriteLedger {
            val changes = LinkedHashMap<String, FavoriteChange>()
            list.forEachIndexed { at, station -> changes.putIfAbsent(station.uuid.lowercase(), FavoriteChange(true, at.toLong(), station)) }
            return FavoriteLedger(changes = changes, unsent = changes.keys.toSet())
        }
    }
}

/** One row of `GET cloud/station-favorites`: the newest change to one station, and the server's stamp. */
@Serializable
data class FavoriteRow(
    val uuid: String = "",
    val saved: Boolean = false,
    val changedAt: Long = 0,
    val station: RadioStation? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class FavoritesPage(val stations: List<FavoriteRow> = emptyList(), val owner: String? = null)

/** The body of `PUT cloud/station-favorites`: this device's changes. */
@Serializable
data class FavoritesReport(val stations: List<FavoriteOut>)

@Serializable
data class FavoriteOut(val uuid: String, val saved: Boolean, val changedAt: Long, val station: StationOut)

@Serializable
data class FavoritesUpdated(val updatedAt: Long = 0)
