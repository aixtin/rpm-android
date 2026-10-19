package com.rpm.player.core.library

import android.content.SharedPreferences
import com.rpm.player.core.auth.AuthClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The stars the person gave records on this TV, 1 to 5, by account and the record's first cover key. Kept on this
 * device only: the desktop keeps its album ratings in its own library and the cloud has no route for them, so a
 * rating made here is not seen on another device. Written on the main thread.
 */
class AlbumRatings(private val prefs: SharedPreferences, private val auth: AuthClient) {
    private val changes = MutableStateFlow(0)

    /** Moves on every rating written, so a screen that reads [of] reads again. */
    val changed: StateFlow<Int> = changes.asStateFlow()

    /** The stars given [album], 0 for none. */
    fun of(album: CloudAlbum): Int = keyOf(album)?.let { prefs.getInt(it, 0) } ?: 0

    /** Gives [album] [stars] (0 clears it). False when no one is signed in. */
    fun rate(album: CloudAlbum, stars: Int): Boolean {
        val key = keyOf(album) ?: return false
        prefs.edit().apply { if (stars <= 0) remove(key) else putInt(key, stars.coerceAtMost(MOST)) }.apply()
        changes.value += 1
        return true
    }

    private fun keyOf(album: CloudAlbum): String? {
        val user = auth.session.value?.userId ?: return null
        val cover = AlbumArtKey.spellings(RecordPicture.COVER, album.tracks).firstOrNull() ?: album.key
        return "$user|$cover"
    }

    companion object {
        const val MOST = 5
    }
}
