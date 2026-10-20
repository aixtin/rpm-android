package com.rpm.player.core.playback

import androidx.media3.common.MediaItem

/**
 * Marks a queued item the person added themselves, with Play next or Add to queue ([playNext], [addToQueue]), in its
 * metadata extras, as [EXTRA_RADIO] marks a song radio's. The queue drawer shows these under an "Up next" heading,
 * apart from the list that was played. The mark is on the item, so it travels with it through a move, and it is
 * kept with the saved queue ([SavedItem.upNext]), so a resumed queue shows the same headings.
 */
const val EXTRA_UP_NEXT = "rpm.upNext"

/** Whether the person added this item with Play next or Add to queue ([EXTRA_UP_NEXT]). */
val MediaItem.isUpNext: Boolean get() = mediaMetadata.extras?.getBoolean(EXTRA_UP_NEXT) == true

/** One line of the queue drawer: a song, or a heading over the songs that follow it. */
sealed interface QueuePart {
    /** The song at queue position [at]. */
    data class Song(val at: Int) : QueuePart

    /** "Up next", over a run of songs the person added; [before] is the run's first position. */
    data class UpNextHeading(val before: Int) : QueuePart

    /** Where the list that was played goes on after a run of added songs; [before] is its first position there. */
    data class ResumeHeading(val before: Int) : QueuePart
}

object QueueParts {
    /**
     * The lines for a queue whose items are [added] or not, in play order: the songs stay in their true order, and
     * every run of added songs gets an "Up next" heading over it. Where the played list goes on after such a run, a
     * smaller heading marks it; a run at the end has none, as nothing of the list follows it. A run can stand anywhere,
     * even before the song playing: the headings say where each song came from, not what is still to come.
     */
    fun of(added: List<Boolean>): List<QueuePart> {
        val parts = ArrayList<QueuePart>(added.size + 2)
        added.forEachIndexed { at, mine ->
            val after = at > 0 && added[at - 1]
            if (mine && !after) parts += QueuePart.UpNextHeading(at)
            if (!mine && after) parts += QueuePart.ResumeHeading(at)
            parts += QueuePart.Song(at)
        }
        return parts
    }

    /** Where the song at queue position [at] stands among [parts], or -1 when it is not there. */
    fun placeOf(parts: List<QueuePart>, at: Int): Int = parts.indexOfFirst { it is QueuePart.Song && it.at == at }
}
