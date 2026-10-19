package com.rpm.player.core.radio

import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.cloud.CloudClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the saved stations in sync with the person's other devices through `cloud/station-favorites`.
 * It reads every row updated since the cursor and takes the newer ones in ([FavoriteLedger.taking]),
 * then sends this device's changes that the cloud has not accepted yet. The desktop and iOS passes
 * work the same way.
 *
 * A pass runs when the radio opens ([RadioRepository.refresh]) and a moment after a change is made
 * here. The change is read from [RadioFavorites.unsent], not announced by whoever made it. One pass
 * runs at a time. The ledger is read and written under its own lock in short steps, never across a
 * request, so a heart pressed during a pass does not wait for the network.
 */
class FavoritesSync(
    private val cloud: CloudClient,
    private val auth: AuthClient,
    private val favorites: RadioFavorites,
    private val scope: CoroutineScope,
) {
    private val passing = Mutex()

    init {
        scope.launch {
            favorites.unsent.collectLatest { waiting ->
                if (!waiting) return@collectLatest
                // A short wait, so a few hearts pressed in a row go in one report.
                delay(AFTER_A_CHANGE_MS)
                run()
            }
        }
    }

    /** Runs a pass now, after any pass already running. */
    fun pass() {
        scope.launch(Dispatchers.IO) { run() }
    }

    private suspend fun run() = passing.withLock {
        val userId = auth.session.value?.userId ?: return@withLock
        try {
            val since = favorites.forSync {
                forAccount(userId)
                cursor
            }
            val rows = cloud.stationFavorites(since)
            favorites.forSync { take(rows) }
            val unsent = favorites.forSync { toSend(MAX_ROWS) }
            if (unsent.isEmpty()) return@withLock
            cloud.putStationFavorites(
                FavoritesReport(unsent.values.map { FavoriteOut(it.station.uuid, it.saved, it.changedAt, StationOut.of(it.station)) }),
            )
            favorites.forSync { noteSent(unsent) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing is marked as sent and the cursor stays. The next pass tries again.
        }
    }

    companion object {
        /** The API's `MaxRowsPerReport`. */
        const val MAX_ROWS = 500
        const val AFTER_A_CHANGE_MS = 2_000L
    }
}
