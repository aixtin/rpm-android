package com.rpm.player.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The secret that ties a social sign-in to the app that started it. The app sends only the hash when it
 * starts the sign-in, and the secret itself when it collects the tokens, so someone who has the login URL (it
 * holds the session id) cannot collect them. The server side is `PollSecret` in rpm.Api, and the desktop and
 * iOS apps make the same hash. Change all of them together. */
object PollSecret {
    /** The header the secret is sent in when polling. */
    const val HEADER = "X-Poll-Secret"

    /** The header the one-time code is sent in when the phone collects a sign-in. The browser brings the code
     * back to the app, and the server hands out the tokens only with the code and the secret together.
     * `SocialSignIn.CodeHeader` in rpm.Api. */
    const val CODE_HEADER = "X-Sign-In-Code"

    private val random = SecureRandom()

    /** A new random secret: 32 bytes, base64url without padding. */
    fun make(): String = ByteArray(32).also(random::nextBytes).let(::base64url)

    /** The hash sent for [secret]: the base64url SHA-256 of its UTF-8 bytes, without padding. */
    fun hashOf(secret: String): String =
        base64url(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)))

    private fun base64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
