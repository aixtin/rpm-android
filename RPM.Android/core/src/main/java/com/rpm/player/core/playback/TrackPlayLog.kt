package com.rpm.player.core.playback

import com.rpm.player.core.net.WireJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** This TV's total for one track: how many plays, and when the last ended (Unix seconds). */
@Serializable
data class OwnPlays(val rpmId: String, val plays: Long, val lastPlayedAt: Long)

/**
 * What this TV has played, as running totals per track, kept to be sent to `cloud/track-plays` under
 * this TV's own device id. It matches the own half of the iOS app's `PlayLog`. A total is sent, never
 * a count to add on, so sending the same row twice changes nothing (the Lambda's `TrackPlayStore`).
 *
 * The totals belong to the account they were played under. Every play is noted with its account. A
 * play for an account other than the log's starts the log over with a new device id, as the iOS app
 * forgets its counts and its device id at sign-out. So one account's plays are never sent under
 * another's.
 *
 * Kept in one file, written through a temporary file. Every change runs under one mutex on IO. A
 * file that cannot be read is never overwritten, and nothing is sent from it.
 */
class TrackPlayLog(private val file: File, private val scope: CoroutineScope) {

    @Serializable
    private data class Model(
        val account: String? = null,
        val device: String? = null,
        val own: List<OwnPlays> = emptyList(),
        val sent: Map<String, Long> = emptyMap(),
    )

    private val mutex = Mutex()
    private var read = false
    private var mayWrite = true
    private var account: String? = null
    private var device = ""
    private val own = LinkedHashMap<String, OwnPlays>()
    private val sent = HashMap<String, Long>()

    /** Counts one play of [rpmId], ended at [at], for [userId]. */
    fun note(userId: String, rpmId: String, at: Long) {
        if (rpmId.isBlank()) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                readOnce()
                if (!mayWrite) return@withLock
                adopt(userId)
                val was = own[rpmId]
                own[rpmId] = OwnPlays(rpmId, (was?.plays ?: 0) + 1, maxOf(was?.lastPlayedAt ?: 0, at))
                save()
            }
        }
    }

    /** What a sync pass reads and writes, under the log's mutex. */
    suspend fun <T> forSync(block: SyncView.() -> T): T = mutex.withLock {
        readOnce()
        SyncView().block()
    }

    /** The log as a sync pass sees it; only reachable inside [forSync]. */
    inner class SyncView internal constructor() {
        /** The device id to send under, or null when nothing may be sent for [userId]: the file cannot
         * be written, or the totals held belong to another account. */
        fun deviceFor(userId: String): String? =
            device.takeIf { mayWrite && it.isNotEmpty() && account == userId }

        /** The totals that differ from what was last sent, most recently played first. */
        fun unsent(limit: Int): List<OwnPlays> = own.values
            .filter { sent[it.rpmId] != it.plays }
            .sortedByDescending { it.lastPlayedAt }
            .take(limit)

        /** Marks the totals that were sent, as they were sent. A play counted since still differs. */
        fun noteSent(rows: List<OwnPlays>) {
            for (row in rows) sent[row.rpmId] = row.plays
            save()
        }
    }

    // Starts over for an account that is not the log's own: its totals, sent marks and device id are dropped.
    private fun adopt(userId: String) {
        if (account == userId) return
        if (account != null || device.isEmpty()) device = UUID.randomUUID().toString().replace("-", "")
        account = userId
        own.clear()
        sent.clear()
    }

    private fun readOnce() {
        if (read) return
        read = true
        if (!file.exists()) return
        val model = try {
            WireJson.decodeFromString(Model.serializer(), file.readText())
        } catch (_: Exception) {
            mayWrite = false
            return
        }
        account = model.account
        device = model.device.orEmpty()
        model.own.forEach { own[it.rpmId] = it }
        sent.putAll(model.sent)
    }

    private fun save() {
        if (!mayWrite) return
        try {
            val text = WireJson.encodeToString(Model.serializer(), Model(account, device.ifEmpty { null }, own.values.toList(), sent))
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: Exception) {
            // Kept in memory and written with the next change.
        }
    }
}
