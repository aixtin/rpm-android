package com.rpm.player.core.playback

/**
 * Where this sitting began in the queue: the earliest place it has actually played, and so the top of
 * the list that a queue page shows (the iOS app's `Player.earliest`). Starting a record at track ten
 * puts the whole record in the queue, so that going back has somewhere to go. But the nine tracks above
 * it were never played, and a list that shows them above the playing row would say that this sitting
 * played and skipped them.
 *
 * A new list starts a new sitting where it starts ([handed]). The same list handed back (for example a
 * song of the record that is playing is tapped) is the player moving inside the sitting already shown.
 * The sitting reaches back only to where the player has really been: going back from track ten plays
 * track nine, which is then part of the sitting.
 *
 * Held as the entry the sitting starts at (its media id and which copy of it), not as a count, so an
 * edit anywhere in the queue leaves it on the same entry. [settle] works the place out again from the
 * queue on every change. Used on the main thread only, where the session's callbacks and the player's
 * events both arrive.
 */
class Sitting {
    /** The entry the sitting starts at, or null before any list. */
    private var first: String? = null

    /** How many entries of the queue are above the sitting, as of the last [settle]. */
    var from = 0
        private set

    /**
     * A controller handed the player [ids] to play from [startIndex], in place of [playing]. This is called
     * before the player has the new list. The next [settle] places the sitting in it.
     */
    fun handed(ids: List<String>, startIndex: Int, playing: List<String>) {
        if (ids == playing || ids.isEmpty()) return
        val start = startIndex.coerceIn(0, ids.size - 1)
        first = keyAt(ids, start)
        from = start
    }

    /** The queue is now [ids] with the player at [current]: where the sitting begins in it. */
    fun settle(ids: List<String>, current: Int): Int {
        val found = first?.let { indexOf(ids, it) } ?: -1
        // The entry that the sitting began at has left the queue: the sitting keeps its count.
        var start = if (found >= 0) found else from.coerceAtMost(ids.size)
        if (current in ids.indices) start = minOf(start, current)
        from = start
        first = if (start in ids.indices) keyAt(ids, start) else null
        return start
    }

    companion object {
        /** Where the sitting begins, as the session publishes it to controllers. */
        const val EXTRA_FROM = "rpm.sitting.from"

        /** Where the sitting begins in a queue of [count] entries playing [playing], as a controller shows it.
         * It is the session's value ([published]), and never below the playing entry, so a controller that
         * receives the player's update before the session's still shows the playing row. */
        fun shownFrom(published: Int, count: Int, playing: Int?): Int {
            val start = published.coerceIn(0, count)
            return if (playing != null && playing in 0 until count) minOf(start, playing) else start
        }

        private fun keyAt(ids: List<String>, at: Int): String = ids[at] + "#" + (0 until at).count { ids[it] == ids[at] }

        private fun indexOf(ids: List<String>, key: String): Int = ids.indices.firstOrNull { keyAt(ids, it) == key } ?: -1
    }
}
