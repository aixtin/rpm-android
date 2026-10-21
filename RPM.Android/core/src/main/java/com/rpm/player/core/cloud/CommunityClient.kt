package com.rpm.player.core.cloud

import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.net.AppKey
import com.rpm.player.core.net.Hosts
import com.rpm.player.core.net.WireJson
import com.rpm.player.core.net.await
import com.rpm.player.core.net.escapeSegment
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * rpm's shared album wall: the entries that everybody shares for a record, and what people wrote about them. It is
 * the iPhone's `RPMCommunityClient`, with the calls the record page uses.
 *
 * **Reading does not need a token. Writing does.** Every read works signed out; the writes are the calls that fail
 * without one. The routes are at the root of the API host, as `albums/resolve`, not under a `community/` prefix.
 */
class CommunityClient(private val http: OkHttpClient, private val auth: AuthClient) {

    /**
     * The record's rpm id. rpm makes the id if it has not seen this record before, and every copy of the same
     * pressing resolves to the same id, so the id is asked for and never built here.
     */
    suspend fun resolveAlbum(artist: String?, title: String, year: Int, trackCount: Int): CommunityAlbum =
        send(
            "POST",
            "albums/resolve",
            WireJson.encodeToString(ResolveBody.serializer(), ResolveBody(artist, title, year, trackCount)),
            CommunityAlbum.serializer(),
        )!!

    /** One entry by its id, or null when it cannot be read. Read again after a write, for the new average. */
    suspend fun album(albumId: String): CommunityAlbum? =
        runCatching { get("albums/${escapeSegment(albumId)}", CommunityAlbum.serializer()) }.getOrNull()

    /** One page of what people wrote, in [sort]'s order. */
    suspend fun reviews(albumId: String, sort: ReviewSort, page: Int, pageSize: Int = 20): ReviewPage =
        get("albums/${escapeSegment(albumId)}/reviews?sort=${sort.wire}&page=$page&pageSize=$pageSize", ReviewPage.serializer())

    /** The signed-in account's own review of the record, or null when it has written none (or cannot be asked). */
    suspend fun myReview(albumId: String): Review? =
        runCatching { get("albums/${escapeSegment(albumId)}/reviews/me", Review.serializer()) }.getOrNull()

    /** Writes or replaces the account's own review. [rating] is in half stars, doubled: 1 to 10. */
    suspend fun writeReview(albumId: String, rating: Int, body: String): Review =
        send(
            "PUT",
            "albums/${escapeSegment(albumId)}/reviews/me",
            WireJson.encodeToString(ReviewBody.serializer(), ReviewBody(rating, body)),
            Review.serializer(),
        )!!

    suspend fun deleteMyReview(albumId: String) {
        send<Unit>("DELETE", "albums/${escapeSegment(albumId)}/reviews/me", null, null)
    }

    /** Votes on another person's review: 1 up, -1 down, 0 to withdraw. Null when the vote was not taken. */
    suspend fun vote(albumId: String, userId: String, value: Int): Review? = runCatching {
        send(
            "POST",
            "albums/${escapeSegment(albumId)}/reviews/${escapeSegment(userId)}/vote",
            WireJson.encodeToString(VoteBody.serializer(), VoteBody(value)),
            Review.serializer(),
        )
    }.getOrNull()

    private suspend fun <T> get(path: String, reader: DeserializationStrategy<T>): T = send("GET", path, null, reader)!!

    /** One call. A null [reader] reads nothing back (a delete). */
    private suspend fun <T> send(method: String, path: String, json: String?, reader: DeserializationStrategy<T>?): T? {
        val token = auth.accessToken()
        val body = json?.toRequestBody("application/json".toMediaType())
            ?: if (method == "GET") null else ByteArray(0).toRequestBody(null)
        val request = Request.Builder()
            .url(Hosts.API + path)
            .header(AppKey.HEADER, AppKey.VALUE)
            .apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }
            .method(method, body)
            .build()
        http.newCall(request).await().use { response ->
            when (response.code) {
                in 200..299 -> return reader?.let { WireJson.decodeFromString(it, response.body.string()) }
                401, 403 -> throw ApiException.Unauthorized()
                404 -> throw ApiException.NotFound()
                else -> throw ApiException.Status(response.code)
            }
        }
    }

    @Serializable
    private data class ResolveBody(val artist: String?, val title: String, val year: Int, val trackCount: Int)

    @Serializable
    private data class ReviewBody(val rating: Int, val body: String)

    @Serializable
    private data class VoteBody(val value: Int)
}

/** rpm's shared entry for one record: its id, and what the wall makes of it so far. */
@Serializable
data class CommunityAlbum(
    val albumId: String,
    val artist: String = "",
    val title: String = "",
    val reviewCount: Int = 0,
    val averageRating: Double = 0.0,
    val ratingCount: Int = 0,
)

/** What somebody wrote about a record. [rating] is in half stars, doubled: 1 to 10. [myVote] is the reader's own. */
@Serializable
data class Review(
    val albumId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val hasAvatar: Boolean = false,
    val rating: Int = 0,
    val body: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val ups: Int = 0,
    val downs: Int = 0,
    val score: Int = 0,
    val myVote: Int = 0,
    /** The author's account was deleted: the name is empty, and the page says "Deleted user". */
    val authorDeleted: Boolean = false,
)

@Serializable
data class ReviewPage(
    val items: List<Review> = emptyList(),
    val page: Int = 0,
    val pageSize: Int = 0,
    val total: Int = 0,
)

/** How a list of reviews is ordered, as the server names the orders. */
enum class ReviewSort(val wire: String) { Votes("votes"), Date("date") }
