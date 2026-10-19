package com.rpm.player.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The key this app sends to the RPM API. It ships inside every client and can be read back out of
 * one, so it filters casual traffic and does not protect anything. It has the same value as `AppKey`
 * in the iOS app and `RpmApi` in RPM.Core. The server accepts the current and the previous key.
 */
object AppKey {
    const val HEADER = "X-RPM-Key"
    const val VALUE = "rpm_live_FEvcaWAmKYVQIOi8EbDUD98kgpkpbT9r"
}

object Hosts {
    const val ACCOUNTS = "https://accounts.rpmrpmrpm.com/"
    const val API = "https://api.rpmrpmrpm.com/"
}

/** The one JSON configuration that every wire type is read with. Unknown fields are ignored, and a null
 * where a default exists reads as the default, because rows uploaded by older desktop versions lack fields. */
val WireJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * The client that every cloud request goes through. It allows up to [PER_HOST] requests to one host at
 * a time, where the OkHttp limit is 5. A cover that is not cached costs one request to the API for a
 * signed address and one to the store for the picture. With a limit of 5, a wall row of six new covers
 * plus the row decoded ahead queued behind each other on each host and filled in over several seconds.
 * A request to play waited in the same queue.
 */
fun httpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .dispatcher(Dispatcher().apply { maxRequestsPerHost = PER_HOST })
    .build()

/** How many requests the cloud client sends to one host at once. */
private const val PER_HOST = 16

/** Runs the call without blocking a thread, and cancels it when the coroutine is cancelled. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
}

/** Escapes a path segment so a name that holds a slash stays one segment. Everything outside
 * `A-Z a-z 0-9 - . _ ~` is percent-encoded from its UTF-8 bytes. */
fun escapeSegment(text: String): String = buildString {
    for (byte in text.toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt().toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~') {
            append(c)
        } else {
            append('%').append("%02X".format(byte.toInt() and 0xFF))
        }
    }
}
