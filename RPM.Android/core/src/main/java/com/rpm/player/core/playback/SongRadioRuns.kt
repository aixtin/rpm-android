package com.rpm.player.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.RadioCrate
import com.rpm.player.core.library.SongRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The song radios, run where playback lives so a radio carries on with the app gone.
 *
 * A radio's queue items carry its name ([EXTRA_RADIO]), and that is the only thing that says the radio is on, as
 * the queue rows carry it on the computer: an item of a radio becoming the current one asks that radio for the songs
 * after it, and a queue replaced by anything else holds none of its items. There is no flag to forget to clear.
 * A song added with Play next plays in its turn, and the radio carries on from its own item after it.
 *
 * Runs on the player's thread. Only the picking goes to a worker, and what it returns is checked again against the
 * queue as it is when the answer lands, so an answer that arrives after the queue changed adds nothing it should not.
 */
class SongRadioRuns(
    private val player: Player,
    private val scope: CoroutineScope,
    /** The collection on screen, or null while none is loaded. */
    private val collection: () -> Collection?,
) {
    /** One radio: the song it started from, and every song it has queued, oldest first. */
    private class Run(val origin: String) {
        val history = ArrayList<String>()
        val queued = HashSet<String>()

        fun note(id: String) {
            if (queued.add(id)) history.add(id)
        }
    }

    private val runs = HashMap<String, Run>()
    private var crate: Pair<Collection, RadioCrate>? = null
    private var asking: Job? = null

    fun attach() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = topUp()
        })
    }

    /** The current item belongs to a radio: picks songs to follow it, on a worker, and queues them when they come. */
    private fun topUp() {
        val item = player.currentMediaItem ?: return
        val name = item.mediaMetadata.extras?.getString(EXTRA_RADIO) ?: return
        val ref = TrackRef.parse(item.mediaId) ?: return

        // Radios with no item left in the queue are over.
        val live = (0 until player.mediaItemCount).mapNotNullTo(HashSet()) { radioOf(player.getMediaItemAt(it)) }
        runs.keys.retainAll(live)
        val run = runs.getOrPut(name) { Run(originOf(name)).also { it.note(originOf(name)) } }

        if (wanted() <= 0) return
        val inQueue = (0 until player.mediaItemCount).mapNotNullTo(HashSet()) { TrackRef.parse(player.getMediaItemAt(it).mediaId)?.rpmId }
        val history = run.history.toList()
        val held = collection() ?: return
        // A radio picks from the collection its song is in; one started on another shelf than the one now on
        // screen has nothing to pick from.
        if (held.shelf != ref.shelf) return

        asking?.cancel()
        asking = scope.launch {
            val picks = withContext(Dispatchers.Default) {
                SongRadio.pick(crateOf(held), ref.rpmId, run.origin, inQueue, history, SongRadio.AHEAD, Random.Default)
            }
            take(name, run, held, picks)
        }
    }

    /** Appends picks until [SongRadio.AHEAD] songs follow the current one, if the radio is still the one playing. */
    private fun take(name: String, run: Run, held: Collection, picks: List<String>) {
        val current = player.currentMediaItem ?: return
        if (radioOf(current) != name) return
        val wanted = wanted()
        if (wanted <= 0) return

        val inQueue = (0 until player.mediaItemCount).mapNotNullTo(HashSet()) { TrackRef.parse(player.getMediaItemAt(it).mediaId)?.rpmId }
        val items = ArrayList<MediaItem>()
        for (id in picks) {
            if (items.size == wanted) break
            // Queued by this radio once and since taken out by the listener: not put back.
            if (id in run.queued || id in inQueue) continue
            val track = held.tracksById[id] ?: continue
            val queued = queuedOf(held, track).copy(radio = name)
            val ref = TrackRef(queued.shelf, id)
            // Straight into the player, so the address the session puts on a controller's items is put on here.
            items.add(mediaItemOf(queued).buildUpon().setUri(CloudTrackUri.of(ref)).build())
            run.note(id)
        }
        if (items.isNotEmpty()) player.addMediaItems(items)
    }

    private fun wanted(): Int = SongRadio.AHEAD - (player.mediaItemCount - 1 - player.currentMediaItemIndex)

    /** The crate of [held], built once per collection. A collection is equal only to itself, so a reload builds
     * a new one. */
    private fun crateOf(held: Collection): RadioCrate = synchronized(this) {
        crate?.takeIf { it.first === held }?.second ?: RadioCrate.of(held).also { crate = held to it }
    }

    companion object {
        /** A new radio's name: the song it starts from, and a stamp, so a second radio on the same song is a new
         * radio with a history of its own. */
        fun newRun(origin: String): String = "$origin#${System.nanoTime()}"

        internal fun originOf(name: String): String = name.substringBefore('#')

        private fun radioOf(item: MediaItem): String? = item.mediaMetadata.extras?.getString(EXTRA_RADIO)
    }
}
