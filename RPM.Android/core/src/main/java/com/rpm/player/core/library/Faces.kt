package com.rpm.player.core.library

import android.util.Log
import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.cloud.CloudClient
import com.rpm.player.core.cloud.PublicProfile
import com.rpm.player.core.net.Hosts
import com.rpm.player.core.net.escapeSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How an account shows itself (matches the iOS app's `PeerFace`): the name it goes by (null when it has none), the
 * address of its picture (null when it has none), and whether it is a supporter.
 */
data class Face(val name: String?, val picture: String?, val supporter: Boolean) {
    companion object {
        /** [profile] as a face; [supporter] is the profile's own for anybody else, `cloud/quota`'s for the person. */
        fun of(profile: PublicProfile, supporter: Boolean) = Face(
            name = profile.displayName?.trim()?.takeIf { it.isNotEmpty() },
            picture = if (profile.hasAvatar && profile.userId.isNotBlank()) avatarUrl(profile.userId, profile.avatarUpdatedAt) else null,
            supporter = supporter,
        )

        /** `profiles/{id}/avatar`, stamped with when it was last changed, so a new picture is a new address and an
         * old one can be kept for good (ProfileController.Avatar). Open to any caller with the app key. */
        fun avatarUrl(userId: String, stamp: Long): String =
            Hosts.API + "profiles/" + escapeSegment(userId) + "/avatar" + (if (stamp > 0) "?v=$stamp" else "")
    }
}

/**
 * The signed-in account's own face: its profile (`profile`, which makes one on the first ask) and whether it is
 * a supporter (`cloud/quota`, unlimited storage), read when the session starts. Null until both are read, and
 * when signed out. A read that fails leaves it null; the next session asks again. An answer is published only
 * if the account that asked is still the one signed in.
 */
class OwnFace(private val cloud: CloudClient, private val auth: AuthClient, scope: CoroutineScope) {
    private val current = MutableStateFlow<Face?>(null)
    val face: StateFlow<Face?> = current.asStateFlow()

    init {
        scope.launch {
            auth.session.map { it?.userId }.distinctUntilChanged().collectLatest { user ->
                current.value = null
                if (user != null) read(user)
            }
        }
    }

    private suspend fun read(user: String) {
        val face = try {
            withContext(Dispatchers.IO) {
                val profile = async { cloud.ownProfile() }
                val quota = async { cloud.quota() }
                Face.of(profile.await(), quota.await().unlimited)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "own profile not read: $e")
            return
        }
        if (auth.session.value?.userId == user) current.value = face
    }

    private companion object {
        const val TAG = "rpm.share"
    }
}
