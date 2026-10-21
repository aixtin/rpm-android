package com.rpm.player.phone.library

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rpm.player.core.R
import com.rpm.player.core.cloud.ApiException
import com.rpm.player.core.cloud.CommunityAlbum
import com.rpm.player.core.cloud.CommunityClient
import com.rpm.player.core.cloud.Review
import com.rpm.player.core.cloud.ReviewSort
import com.rpm.player.core.library.AlbumSort
import com.rpm.player.core.library.CloudAlbum
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * A message for the reader: a string and what goes in it. Held as a resource rather than as text, so the wall is
 * read without a context.
 */
data class Said(@param:StringRes val text: Int, val arg: Any? = null)

/**
 * The reviews and ratings other listeners gave one record: the state behind the foot of the record page. It is the
 * iPhone's `ReviewWall` (rpm/ReviewWall.swift).
 *
 * **The record's community id is not made from its tags.** rpm makes an id the first time anybody asks about a
 * record, and every copy of the same pressing resolves to it, so the id is asked for ([CommunityClient.resolveAlbum]).
 *
 * The state is Compose state, read by the page as it draws. Every call runs on the main thread; only the requests
 * leave it.
 */
class ReviewWall(private val community: CommunityClient, private val album: CloudAlbum) {
    var entry by mutableStateOf<CommunityAlbum?>(null)
        private set
    var reviews by mutableStateOf<List<Review>>(emptyList())
        private set
    var mine by mutableStateOf<Review?>(null)
        private set
    var reading by mutableStateOf(false)
        private set

    /** Why the wall could not be read, shown in place of an empty wall. */
    var trouble by mutableStateOf<Said?>(null)
        private set
    var sort by mutableStateOf(ReviewSort.Votes)
        private set

    private var total by mutableIntStateOf(0)
    private var page = 1

    val hasMore: Boolean get() = reviews.size < total

    /** The average of every rating, in stars. */
    val average: Double get() = entry?.averageRating ?: 0.0
    val ratingCount: Int get() = entry?.ratingCount ?: 0

    /** The reader's own rating, in stars. */
    val myStars: Double get() = (mine?.rating ?: 0) / 2.0

    suspend fun read() {
        if (entry != null || reading) return
        reading = true
        try {
            // The record's own tags are sent, so a record another listener already asked about resolves to the same entry.
            entry = community.resolveAlbum(album.artist, album.album, AlbumSort.yearOf(album.tracks), album.tracks.size)
            load(1, replacing = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trouble = message(e, R.string.Album_CommunityOffline)
        } finally {
            reading = false
        }
    }

    suspend fun sorted(order: ReviewSort) {
        if (order == sort) return
        sort = order
        load(1, replacing = true)
    }

    suspend fun more() {
        if (!hasMore || reading) return
        load(page + 1, replacing = false)
    }

    private suspend fun load(wanted: Int, replacing: Boolean) {
        val entry = entry ?: return
        reading = true
        try {
            val said = community.reviews(entry.albumId, sort, wanted)
            page = wanted
            total = said.total
            reviews = if (replacing) said.items else reviews + said.items
            trouble = null
            // After the page, not beside it: most readers have written nothing, so a parallel ask is usually wasted.
            if (replacing) mine = community.myReview(entry.albumId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trouble = message(e, R.string.Album_CommunityOffline)
        } finally {
            reading = false
        }
    }

    /**
     * Writes or replaces the reader's own review. [stars] is 0 to 5 in halves; the server takes it doubled. Null when
     * it is done, or what went wrong.
     */
    suspend fun write(stars: Double, body: String): Said? {
        val entry = entry ?: return Said(R.string.Album_CommunityOffline)
        if (stars <= 0) return Said(R.string.Rpm_Album_RatingNeeded)
        return try {
            mine = community.writeReview(entry.albumId, (stars * 2).roundToInt(), body.trim())
            // The wall and the average have both changed. They are read again rather than patched: the server works out
            // the average over every rating, and a guess here could disagree with it.
            refresh()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message(e, R.string.Rpm_Album_ReviewSaveFailed)
        }
    }

    suspend fun deleteMine(): Said? {
        val entry = entry ?: return null
        return try {
            community.deleteMyReview(entry.albumId)
            mine = null
            refresh()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message(e, R.string.Rpm_Album_ReviewRemoveFailed)
        }
    }

    /**
     * Votes on another listener's review. The same vote again withdraws it. A reader cannot vote on their own review:
     * the row does not offer it, and this refuses any call that gets past the row.
     */
    suspend fun vote(review: Review, value: Int) {
        val entry = entry ?: return
        if (review.userId == mine?.userId) return
        val wanted = if (review.myVote == value) 0 else value
        val said = community.vote(entry.albumId, review.userId, wanted) ?: return
        reviews = reviews.map { if (it.userId == review.userId) said else it }
    }

    private suspend fun refresh() {
        val entry = entry ?: return
        this.entry = community.album(entry.albumId) ?: entry
        load(1, replacing = true)
    }

    /** What to tell the reader about a failure, as the iPhone's `RPMError.userMessage(refused:)` words it. */
    private fun message(e: Exception, @StringRes refused: Int): Said = when (e) {
        is ApiException.Unauthorized -> Said(R.string.Rpm_Error_SessionExpired)
        is ApiException.NotFound -> Said(refused)
        is ApiException.Status -> Said(R.string.Rpm_Error_Status, e.code)
        is java.io.IOException -> Said(R.string.Rpm_Net_Failed)
        else -> Said(refused)
    }
}

/**
 * One wall per record, kept for the life of the shell. Several record pages exist at once (a record opened from a
 * record's Similar albums row, another under home), and each must show its own record's wall. A page that comes back
 * into view finds its wall already read.
 */
class ReviewWalls(private val community: CommunityClient) {
    private val walls = HashMap<String, ReviewWall>()

    fun of(album: CloudAlbum): ReviewWall = walls.getOrPut(album.key) { ReviewWall(community, album) }
}
