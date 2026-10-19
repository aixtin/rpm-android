package com.rpm.player.core.radio

import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.cloud.CloudClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shares the station listens with the person's other devices through `cloud/station-listens`, as the
 * desktop's `CloudSyncService.StationListens` does. A pass reads every row updated since the cursor,
 * takes the other devices' rows, moves the cursor to the newest row that arrived, then sends this
 * device's rows whose totals changed since they were last sent (at most 200, most recently heard first).
 *
 * Only one pass runs at a time. The log is read and written under its own mutex in short steps, never
 * across a request, so a part counted during a pass does not wait for the network.
 */
class StationListensSync(
    private val cloud: CloudClient,
    private val auth: AuthClient,
    private val listens: StationListens,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val passing = Mutex()

    @Volatile
    private var lastPassMs = 0L

    /** Runs a pass now, after any pass already running. */
    fun pass() {
        scope.launch(Dispatchers.IO) { run() }
    }

    /** Runs a pass unless one ran in the last five minutes. Called after each part is counted. */
    fun soon() {
        if (clock() - lastPassMs >= AFTER_A_PART_MS) pass()
    }

    private suspend fun run() = passing.withLock {
        val userId = auth.session.value?.userId ?: return@withLock
        lastPassMs = clock()
        try {
            val (device, since) = listens.forSync {
                forAccount(userId)
                device to cursor
            }
            if (device == null) return@withLock
            val rows = cloud.stationListens(since)
            listens.forSync {
                apply(rows)
                // Every row that arrived moves the cursor, this device's own included.
                rows.maxOfOrNull { it.updatedAt }?.let(::noteCursor)
            }
            val unsent = listens.forSync { unsent(MAX_ROWS) }
            if (unsent.isEmpty()) return@withLock
            cloud.putStationListens(
                StationListensReport(
                    deviceId = device,
                    stations = unsent.map { StationListenOut(it.station.uuid, it.heardSeconds, it.tunes, it.lastHeardAt, StationOut.of(it.station)) },
                ),
            )
            listens.forSync { noteSent(unsent) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing is marked as sent and the cursor stays; the next pass tries again.
        }
    }

    companion object {
        /** The API's `MaxRowsPerReport`. */
        const val MAX_ROWS = 200
        const val AFTER_A_PART_MS = 5 * 60_000L
    }
}
