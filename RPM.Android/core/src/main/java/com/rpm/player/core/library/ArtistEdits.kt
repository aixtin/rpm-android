package com.rpm.player.core.library

import com.rpm.player.core.net.AppKey
import com.rpm.player.core.net.Hosts
import com.rpm.player.core.net.WireJson
import com.rpm.player.core.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * What the admin changed about one act, as the rpm API's `artist/edits` answers it: a
 * [description] shown in place of the Wikipedia article, and acts to show first ([related]) or never ([unrelated])
 * among the related artists. The pictures the admin added or hid reach the app through `artwork` and are not read here.
 */
@Serializable
data class ArtistEdit(
    val description: String? = null,
    val related: List<String> = emptyList(),
    val unrelated: List<String> = emptyList(),
) {
    /** [computed] (the collection's own related acts, closest first) with the admin's lists on it: the acts named
     * under [related] that the collection holds first, in the admin's order, then the rest without any named under
     * [unrelated], at most [RelatedArtists.MOST]. Acts are matched by [NameMatch.squash], as the wall matches them. */
    fun relate(computed: List<ArtistWallItem>, self: ArtistWallItem, walls: Walls): List<ArtistWallItem> {
        if (related.isEmpty() && unrelated.isEmpty()) return computed
        val never = unrelated.mapTo(HashSet()) { NameMatch.squash(it) }
        val me = NameMatch.squash(self.name)
        val byName = walls.artists.associateBy { NameMatch.squash(it.name) }
        val first = related.mapNotNull { byName[NameMatch.squash(it)] }
            .filter { NameMatch.squash(it.name) != me && NameMatch.squash(it.name) !in never }
            .distinctBy { NameMatch.squash(it.name) }
        val firstNames = first.mapTo(HashSet()) { NameMatch.squash(it.name) }
        val rest = computed.filter { val n = NameMatch.squash(it.name); n !in never && n !in firstNames }
        return (first + rest).take(RelatedArtists.MOST)
    }

    companion object {
        val NONE = ArtistEdit()
    }
}

/**
 * The admin's edits by act, asked once per act and kept for the life of the app, as [Artwork] keeps its answers. A
 * failed ask is [ArtistEdit.NONE] and is not kept, so the next page opened for the act asks again: a page with no edits
 * is the page as it was before there was an admin page, which is the right thing to show while the API does not answer.
 */
class ArtistEdits(private val http: OkHttpClient) {
    private val answered = ConcurrentHashMap<String, ArtistEdit>()

    /** The edits kept for [artist], or null when none have been asked for yet. Never asks. */
    fun peek(artist: String): ArtistEdit? = answered[key(artist)]

    suspend fun of(artist: String): ArtistEdit {
        val key = key(artist)
        if (key.isEmpty()) return ArtistEdit.NONE
        answered[key]?.let { return it }
        val url = (Hosts.API + "artist/edits").toHttpUrl().newBuilder().addQueryParameter("name", artist.trim()).build()
        val request = Request.Builder().url(url).header(AppKey.HEADER, AppKey.VALUE).build()
        return try {
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) return ArtistEdit.NONE
                WireJson.decodeFromString(ArtistEdit.serializer(), response.body.string())
            }.also { answered[key] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ArtistEdit.NONE
        }
    }

    /** As the API keys them: the name trimmed and lower-cased. */
    private fun key(artist: String) = artist.trim().lowercase()
}
