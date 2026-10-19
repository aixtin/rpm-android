package com.rpm.player.core.auth

import kotlinx.serialization.Serializable

/** A signed-in session as this app keeps it. It has the same fields as the iPhone app's `Session`. */
@Serializable
data class Session(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

/** The answer of `auth/login`, `auth/refresh` and a finished `auth/poll-session`. A refresh answer has no
 * refresh token, which is why every field is optional. */
@Serializable
data class LoginResponse(
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
)

@Serializable
data class SocialStart(val loginUrl: String, val sessionId: String)

@Serializable
data class SocialPoll(
    val status: String? = null,
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
) {
    val isComplete: Boolean get() = status == "complete" && !accessToken.isNullOrEmpty()

    fun asLogin() = LoginResponse(userId, email, displayName, accessToken, refreshToken, expiresIn)
}

/** The sign-in providers that the accounts service offers, with the names it expects. */
enum class Provider(val wireName: String) {
    GOOGLE("Google"),
    APPLE("Apple"),

    /** An rpm account's email and password: the accounts service passes the name through to the hosted sign-in
     * page, whose own email form is `COGNITO` (checked against the hosted sign-in page). */
    ACCOUNT("COGNITO"),
}
