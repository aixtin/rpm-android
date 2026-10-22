package com.rpm.player.core.library

/**
 * Merges one playlist's running order when two devices have both changed it. It matches the iPhone's
 * `PlaylistMerge` (RPMLibrary/PlaylistMerge.swift), rule for rule.
 *
 * Three lists are used: the copy the store holds now, the copy this device wants, and the copy both started
 * from. With all three, each change made on one side can be told apart from each change made on the other, so
 * both are kept and neither side quietly undoes the other.
 *
 * **Counts, not sets.** A playlist may hold the same song twice on purpose, so the arithmetic works on how many
 * copies of each song there are, not on whether a song is in the list.
 *
 * **The store's order is the base.** When both sides reorder the same songs, only one order can win, and the
 * store's is the one every other device already shows. Songs added on this side follow it, in this side's order.
 */
object PlaylistMerge {
    /**
     * The running order that both sides should end up with.
     *
     * @param ancestor the order both sides started from, or null when it is not known. Without it no removal can
     * be shown, so none is applied: an unknown base merges to the union, which loses nothing. Read as an empty
     * list instead, every song would look removed and the playlist would empty itself.
     * @param mine the order this device wants.
     * @param theirs the order the store holds now.
     */
    fun songs(ancestor: List<String>?, mine: List<String>, theirs: List<String>): List<String> {
        // One side unchanged: the other side holds every change, and is taken as it is. The counting below
        // would give the same songs in another order, and could undo a reorder made here.
        if (ancestor != null) {
            if (ancestor == mine) return theirs
            if (ancestor == theirs) return mine
        }
        val want = targets(ancestor, mine, theirs)
        val merged = ArrayList<String>(maxOf(mine.size, theirs.size))
        val taken = HashMap<String, Int>()
        fun take(id: String) {
            val already = taken[id] ?: 0
            if (already >= (want[id] ?: 0)) return
            taken[id] = already + 1
            merged += id
        }
        for (id in theirs) take(id)
        for (id in mine) take(id)
        return merged
    }

    /**
     * How many copies of each song the merged list holds: the base's count, plus the additions, minus the
     * removals. Each of those is the larger of the two sides' figures, not their sum, so a song both devices
     * added is added once and a song both removed is removed once.
     */
    private fun targets(ancestor: List<String>?, mine: List<String>, theirs: List<String>): Map<String, Int> {
        val was = ancestor?.let(::count).orEmpty()
        val here = count(mine)
        val there = count(theirs)
        val want = HashMap<String, Int>()
        for (id in here.keys + there.keys + was.keys) {
            if (id in want) continue
            val before = was[id] ?: 0
            val ours = here[id] ?: 0
            val others = there[id] ?: 0
            val added = maxOf(ours - before, others - before)
            val removed = if (ancestor != null) maxOf(before - ours, before - others) else 0
            want[id] = maxOf(0, before + maxOf(0, added) - maxOf(0, removed))
        }
        return want
    }

    private fun count(ids: List<String>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (id in ids) counts[id] = (counts[id] ?: 0) + 1
        return counts
    }
}
