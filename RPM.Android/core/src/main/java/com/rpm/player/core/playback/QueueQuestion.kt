package com.rpm.player.core.playback

import androidx.media3.common.Player

/**
 * What a tap that starts a list was on, as the iPhone's `Tapped`. It decides what "Play now, keep queue" adds to the
 * queue: the one song for a tap on a row, the whole list for a Play or Shuffle key. Every caller says which, because a
 * row and a key reach the same code.
 */
enum class Tapped {
    /** A row in a record, a list, an artist's page or a search result. */
    Song,

    /** A Play or Shuffle key for the whole list, or a list of one song. */
    List,
}

/**
 * A change to the queue that is ready to start, as the iPhone's `QueueStart`. Made by [replacing], and by the two
 * answers to its [QueueQuestion]; [start] carries it out.
 */
sealed interface QueueStart {
    /** [queue] replaces the queue and plays from [from]. */
    data class Replace(val queue: List<Queued>, val from: Int) : QueueStart

    /** [songs] go in after the playing song and the one at [from] plays. The rest of the queue follows them. */
    data class PlayNow(val songs: List<Queued>, val from: Int) : QueueStart
}

/**
 * The question asked before a queue the person changed is replaced, with one start for each answer, as the iPhone's
 * `QueueQuestion`. Cancel is not here, because it starts nothing.
 */
class QueueQuestion(private val replace: QueueStart, private val keep: QueueStart) {
    /** Discard: the list replaces the queue. */
    fun discard(): QueueStart = replace

    /** Play now, keep queue: the tapped song, or the whole list for a key, plays after the song playing, and the rest
     * of the queue follows it. */
    fun keepQueue(): QueueStart = keep
}

/** What replacing the queue would cost, worked out before anything changes (the iPhone's `Replacing`). */
sealed interface Replacing {
    /** The person has not changed the songs still to come, so the list can replace the queue. */
    data class Clear(val start: QueueStart) : Replacing

    /** The person changed the songs still to come. Ask before throwing them away. */
    data class Asks(val question: QueueQuestion) : Replacing
}

/**
 * The media ids of the list the queue was last started with from outside it: a record, a list, a shuffle. The iPhone's
 * `Player.started`. Set by [playQueue], which every start from outside the queue goes through, on either app. Next,
 * previous, a queue row and the queue's own shuffle do not go through it, so the person's edits stay visible against
 * it.
 *
 * **Held for the process, not for a screen.** The phone's activity can be made again while the playback service keeps
 * the queue, and the answer must not change with it. Null until a list is started in this process: a queue put back by
 * the playback service from its last run ([LastQueue]) was not started here. Then only the songs marked as the
 * person's own ([isUpNext]) count as changes.
 */
object QueueStarted {
    @Volatile
    var ids: List<String>? = null
        internal set
}

/**
 * Whether the songs still to come differ from the list the queue was started with: the iPhone's
 * `QueueEdit.hasChangesAhead`, ported as it is. It is worked out from the queue each time, not kept as a flag set by
 * each edit, so an edit that is later undone, or added songs that have since been played, stop counting with nothing
 * to clear them.
 *
 * The songs to come must be the end of [started], and the playing song must be the one [started] has just before that
 * end. The second check catches a removed next song: what is left after it is still an exact end of the list. It asks
 * too often in one case, as on iPhone: while an added song plays, the songs after it can be the original list, but
 * the playing song is not from the list, so nothing says where in the list it is.
 *
 * @param queue the queue's ids, in order.
 * @param current the playing position, or null when nothing is playing.
 * @return false when nothing is left to come, whatever the earlier edits were.
 */
fun hasChangesAhead(queue: List<String>, current: Int?, started: List<String>): Boolean {
    val first = ((current ?: -1) + 1).coerceIn(0, queue.size)
    val ahead = queue.subList(first, queue.size)
    if (ahead.isEmpty()) return false
    if (ahead.size > started.size || ahead != started.subList(started.size - ahead.size, started.size)) return true

    // Where the playing song sits in `started` if nothing was changed. Minus one means the whole list is still to
    // come, which is right only when nothing is playing.
    val before = started.size - ahead.size - 1
    if (current == null || current !in queue.indices) return before != -1
    return before < 0 || queue[current] != started[before]
}

/**
 * Whether the person changed the songs this player still has to come: the iPhone's `Player.hasChangesAhead`. Against
 * [QueueStarted] when a list was started in this process, otherwise by the marks on the songs themselves.
 */
val Player.hasChangesAhead: Boolean
    get() {
        val count = mediaItemCount
        if (count == 0) return false
        val current = currentMediaItemIndex.takeIf { it in 0 until count }
        val started = QueueStarted.ids
            ?: return ((current ?: -1) + 1 until count).any { getMediaItemAt(it).isUpNext }
        return hasChangesAhead((0 until count).map { getMediaItemAt(it).mediaId }, current, started)
    }

/**
 * What replacing the queue with [queue] from [from] would cost: the iPhone's `Player.replacing(with:from:tapped:)`.
 * Nothing changes here. A queue that the person changed asks first, unless the list handed over is the queue itself.
 */
fun Player.replacing(queue: List<Queued>, from: Int, tapped: Tapped): Replacing {
    val replace = QueueStart.Replace(queue, from)
    if (!hasChangesAhead) return Replacing.Clear(replace)
    val same = queue.size == mediaItemCount && queue.indices.all { queue[it].mediaId == getMediaItemAt(it).mediaId }
    if (same) return Replacing.Clear(replace)
    val keep = when (tapped) {
        Tapped.Song -> QueueStart.PlayNow(listOfNotNull(queue.getOrNull(from)), 0)
        Tapped.List -> QueueStart.PlayNow(queue, from)
    }
    return Replacing.Asks(QueueQuestion(replace, keep))
}

/** Carries out what [replacing], or an answer to its question, gave. */
fun Player.start(start: QueueStart) {
    when (start) {
        is QueueStart.Replace -> playQueue(start.queue, start.from)
        is QueueStart.PlayNow -> playNow(start.songs, start.from)
    }
}

/**
 * Puts [songs] after the playing song and plays the one at [from]; the rest of the queue follows them. They are the
 * person's additions to a queue they kept ([EXTRA_UP_NEXT]), so [QueueStarted] is left as it was and they count as a
 * change while they are still to come, as on iPhone.
 */
private fun Player.playNow(songs: List<Queued>, from: Int) {
    if (from !in songs.indices) return
    if (mediaItemCount == 0) return playQueue(songs, from)
    val at = (currentMediaItemIndex + 1).coerceIn(0, mediaItemCount)
    addMediaItems(at, songs.map { mediaItemOf(it.copy(upNext = true)) })
    seekToDefaultPosition(at + from)
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) prepare()
    play()
}

/** The media id this song is queued under. */
private val Queued.mediaId: String get() = TrackRef(shelf, track.rpmId).mediaId
