package com.rpm.player.core.playback

/**
 * One row of a list of songs as a page shows it. It matches the iOS app's `QueueRow` (RPMAudio/QueueRows.swift).
 *
 * Identity, position and place on the screen are three different things. [id] is the song and which
 * copy of it this is, counted over the whole queue, so a row keeps its identity through a reorder and
 * when the sitting reaches further back. [at] is the position in the queue. Every action is given
 * this position, never the row's place on the screen.
 */
class QueueRow<T>(val id: String, val at: Int, val item: T, val state: State) {
    enum class State { Played, Playing, Coming }
}

/**
 * Which part of a list is on the screen. It matches the iOS app's `QueueLook`. It is a stated answer,
 * not an omitted one: the queue is shown from where the sitting began ([sitting]), and a stored list whole ([Whole]).
 */
class QueueLook private constructor(
    /** The first position shown. */
    val from: Int,
    /** The position playing, or null. */
    val playing: Int?,
) {
    companion object {
        /** All of a list, none of it played: a stored playlist. */
        val Whole = QueueLook(0, null)

        /** The queue from where the sitting began ([Sitting]), with the playing entry marked. */
        fun sitting(from: Int, playing: Int?) = QueueLook(from, playing)
    }
}

object QueueRows {
    /** The rows of [queue] that [look] shows, each knowing whether it is played, playing or still to come. */
    fun <T> of(queue: List<T>, look: QueueLook, idOf: (T) -> String): List<QueueRow<T>> {
        val seen = HashMap<String, Int>()
        val first = look.from.coerceIn(0, queue.size)
        val rows = ArrayList<QueueRow<T>>(queue.size - first)
        queue.forEachIndexed { at, item ->
            // Counted over the whole queue, including the entries above the top of the list: a copy's
            // identity must not change when the sitting reaches further back.
            val id = idOf(item)
            val copy = seen[id] ?: 0
            seen[id] = copy + 1
            if (at < first) return@forEachIndexed
            val playing = look.playing
            val state = when {
                at == playing -> QueueRow.State.Playing
                playing != null && at < playing -> QueueRow.State.Played
                else -> QueueRow.State.Coming
            }
            rows += QueueRow("$id#$copy", at, item, state)
        }
        return rows
    }

    /**
     * Where a row goes when it is dragged from screen place [from] and dropped at screen place [to].
     * The result is positions in the queue (the landing position is read after the dragged row is taken
     * out), or null for a drag that moves nothing. A drag never lands in the past: the lowest position
     * is the entry after the one playing, or the top of the list when nothing is playing.
     *
     * @param count how long the whole queue is.
     */
    fun drop(rows: List<QueueRow<*>>, look: QueueLook, from: Int, to: Int, count: Int): Pair<Int, Int>? {
        if (from !in rows.indices) return null
        val source = rows[from].at
        // Dropped past the last row means the end of the queue: one past the last position.
        val landing = if (to < rows.size) rows[to].at else count
        // Out before in: everything below the hole has moved up a place by the time the row goes back.
        var destination = if (landing > source) landing - 1 else landing
        destination = maxOf(destination, look.playing?.let { it + 1 } ?: look.from)
        destination = minOf(destination, maxOf(count - 1, 0))
        return if (destination == source) null else source to destination
    }
}
