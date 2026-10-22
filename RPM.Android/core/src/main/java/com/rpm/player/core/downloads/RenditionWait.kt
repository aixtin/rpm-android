package com.rpm.player.core.downloads

/**
 * Tracks whose rendition the store has not made yet, and when to ask about each one again. The iPhone's
 * `RenditionWait` (RPMLibrary/RenditionWait.swift).
 *
 * A track signed at a rate the store has not made is answered with a live encode, made by the transcoder while it
 * sends. That is right for playback and wrong for a download: a live encode that stops part way has already
 * answered success, and the download would keep a cut-off track as a whole one. So a download never takes a live
 * address. It asks the store to make the rendition, asks again after a gap that grows, and downloads the stored
 * object once the store has it. The store lists a rate as made only once the whole object is in place.
 *
 * Bookkeeping only. The time is passed in (milliseconds on the wall clock), so it is tested without a network. It is
 * owned by [AlbumDownloads] and touched only on its thread.
 */
class RenditionWait {
    /** What the store said about one track, as far as the caller acts on it. */
    enum class Said {
        /** Not waited on before at this rate. The caller signs the next tracks at once instead of waiting. */
        New,

        /** Still being made, and still inside the time allowed. */
        Waiting,

        /** Waited longer than [GIVE_UP_MS]. The caller counts a failed go. The wait starts again and the store is
         * asked again, in case the first ask was lost. */
        Overdue,
    }

    private data class Entry(val kilobits: Int, val since: Long, val next: Long, val gap: Long, val asked: Boolean)

    private val entries = HashMap<String, Entry>()

    /** Tracks the store was asked to make before their own sign answered, with the rate for each. One that then
     * answers live starts its wait already asked. */
    private val ahead = HashMap<String, Int>()

    /** Whether to sign this track now at this rate. A track waited on at another rate is due at once: the setting
     * changed, so the old wait says nothing about the new rate. */
    fun isDue(rpmId: String, kilobits: Int, now: Long): Boolean {
        val entry = entries[rpmId]
        if (entry == null || entry.kilobits != kilobits) return true
        return now >= entry.next
    }

    /** Whether a track waits for the store at any rate: what shows an album as preparing. */
    fun isWaiting(rpmId: String): Boolean = rpmId in entries

    val isEmpty: Boolean get() = entries.isEmpty()

    /** The store answered a sign for this track with a live encode. */
    fun stillMaking(rpmId: String, kilobits: Int, now: Long): Said {
        val entry = entries[rpmId]
        if (entry == null || entry.kilobits != kilobits) {
            entries[rpmId] = Entry(kilobits, since = now, next = now + FIRST_MS, gap = FIRST_MS, asked = ahead[rpmId] == kilobits)
            return Said.New
        }
        if (now - entry.since > GIVE_UP_MS) {
            entries[rpmId] = entry.copy(since = now, gap = FIRST_MS, next = now + FIRST_MS, asked = false)
            return Said.Overdue
        }
        val gap = minOf(entry.gap * 2, LONGEST_MS)
        entries[rpmId] = entry.copy(gap = gap, next = now + gap)
        return Said.Waiting
    }

    /** The tracks the store has not yet been asked to make, by rate, because one ask names one rate. */
    val unasked: Map<Int, List<String>>
        get() = entries.filterValues { !it.asked }.entries
            .groupBy({ it.value.kilobits }, { it.key })
            .mapValues { (_, ids) -> ids.sorted() }

    /** The store took the ask for these tracks at this rate. A wait dropped, or restarted at another rate while the
     * ask was out, is left as it is. */
    fun asked(rpmIds: Collection<String>, kilobits: Int) {
        for (rpmId in rpmIds) {
            val entry = entries[rpmId] ?: continue
            if (entry.kilobits == kilobits) entries[rpmId] = entry.copy(asked = true)
        }
    }

    /** Whether the store has been asked to make this track at this rate, through its own wait or ahead of it. */
    fun wasAsked(rpmId: String, kilobits: Int): Boolean {
        val entry = entries[rpmId]
        if (entry != null && entry.kilobits == kilobits && entry.asked) return true
        return ahead[rpmId] == kilobits
    }

    /** The store took an ask for tracks that have not answered a sign themselves yet. */
    fun askedAhead(rpmIds: Collection<String>, kilobits: Int) {
        for (rpmId in rpmIds) ahead[rpmId] = kilobits
    }

    /** The store has the rendition, the track is no longer wanted, or it is tried again from the start. Its wait
     * ends, and so does any ask made ahead for it. */
    fun forget(rpmIds: Iterable<String>) {
        for (rpmId in rpmIds) {
            entries.remove(rpmId)
            ahead.remove(rpmId)
        }
    }

    fun forgetAll() {
        entries.clear()
        ahead.clear()
    }

    /** When the next track is due to be asked about, or null when nothing waits. */
    val nextCheck: Long? get() = entries.values.minOfOrNull { it.next }

    companion object {
        /** The first repeat ask comes after this long, and each gap after it is double the one before, up to
         * [LONGEST_MS]. An album of twelve tracks takes the transcoder about a minute, so asking every fifteen
         * seconds at first finds it soon after it is ready. */
        const val FIRST_MS = 15_000L
        const val LONGEST_MS = 60_000L

        /** How long one track may wait before the wait counts as failed: a track still not made after this long is
         * one the transcoder is failing on. On the wall clock, so time the app spends asleep counts. */
        const val GIVE_UP_MS = 600_000L
    }
}
