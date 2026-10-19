package com.rpm.player.core.cast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** The namespaces this app speaks on. */
object CastNamespace {
    const val CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
    const val HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    const val RECEIVER = "urn:x-cast:com.google.cast.receiver"
    const val MEDIA = "urn:x-cast:com.google.cast.media"

    /** RPM's own namespace, which only RPM's receiver listens on (`NAMESPACE` in
     * `cast/receiver.html` in the rpm repo). The messages on it are listed in [ControlWords]. */
    const val CONTROL = "urn:x-cast:com.rpmrpmrpm.control"
}

/** The receiver applications this app launches. */
object CastApps {
    /** RPM's own receiver. It is the one that sends messages back on [CastNamespace.CONTROL]. */
    const val RPM = "41BD77B1"

    /** Google's Default Media Receiver. It plays the stream and cannot send messages back. Used as the fallback. */
    const val DEFAULT_MEDIA = "CC1AD845"
}

/** The JSON payloads this app sends. Requests are built without a `requestId`; [CastLink.ask] adds it. */
object CastPayload {
    val connect: JsonObject = type("CONNECT")
    val close: JsonObject = type("CLOSE")
    val ping: JsonObject = type("PING")
    val pong: JsonObject = type("PONG")
    val getStatus: JsonObject = type("GET_STATUS")

    fun launch(appId: String): JsonObject = buildJsonObject {
        put("type", "LAUNCH")
        put("appId", appId)
    }

    fun stopApp(sessionId: String): JsonObject = buildJsonObject {
        put("type", "STOP")
        put("sessionId", sessionId)
    }

    /** The room's volume, 0 to 1. */
    fun setVolume(level: Double): JsonObject = buildJsonObject {
        put("type", "SET_VOLUME")
        put("volume", buildJsonObject { put("level", level.coerceIn(0.0, 1.0)) })
    }

    fun play(mediaSession: Int): JsonObject = media("PLAY", mediaSession)
    fun pause(mediaSession: Int): JsonObject = media("PAUSE", mediaSession)
    fun stopMedia(mediaSession: Int): JsonObject = media("STOP", mediaSession)

    /**
     * The LOAD for one stream. `contentId` and `contentUrl` both carry the address because some receivers read
     * one and some read the other. The stream is LIVE: it has no length, and a track change inside it is sent
     * on the control channel instead. [autoplay] is the player's play state at the moment the LOAD is sent.
     * A change after that is sent to the receiver separately.
     */
    fun load(url: String, title: String, artist: String?, album: String?, artUrl: String?, autoplay: Boolean): JsonObject =
        buildJsonObject {
            put("type", "LOAD")
            put("autoplay", autoplay)
            put("currentTime", 0)
            put(
                "media",
                buildJsonObject {
                    put("contentId", url)
                    put("contentUrl", url)
                    put("contentType", "audio/wav")
                    put("streamType", "LIVE")
                    put(
                        "metadata",
                        buildJsonObject {
                            // 3 is MusicTrackMediaMetadata.
                            put("metadataType", 3)
                            put("title", title)
                            put("songName", title)
                            artist?.let { put("artist", it) }
                            album?.let { put("albumName", it) }
                            artUrl?.let { put("images", buildJsonArray { add(buildJsonObject { put("url", it) }) }) }
                        },
                    )
                },
            )
        }

    /** Adds [requestId] to [payload]. The answer is matched by this number only. */
    fun stamp(payload: JsonObject, requestId: Int): String =
        JsonObject(payload + ("requestId" to JsonPrimitive(requestId))).toString()

    /** The `requestId` of a payload, or null if it has none (a message that nobody asked for). */
    fun requestIdOf(payload: String): Int? = objectOf(payload)?.get("requestId")?.let { (it as? JsonPrimitive)?.intOrNull }?.takeIf { it > 0 }

    /** The `type` of a payload, or null if the payload is not a JSON object with a `type`. */
    fun typeOf(payload: String): String? = objectOf(payload)?.string("type")

    private fun type(name: String): JsonObject = buildJsonObject { put("type", name) }

    private fun media(type: String, mediaSession: Int): JsonObject = buildJsonObject {
        put("type", type)
        put("mediaSessionId", mediaSession)
    }
}

/** What a receiver reports about itself (`RECEIVER_STATUS`). It is read leniently: every field may be missing. */
data class ReceiverStatus(val applications: List<Application>, val volume: Volume?) {
    data class Application(
        val appId: String,
        /** This launch; used to stop it again. */
        val sessionId: String,
        /** Where the app's messages go. It is not `receiver-0`: a message on one of the app's namespaces that is
         * sent there is silently dropped. */
        val transportId: String,
        val namespaces: List<String>,
    )

    data class Volume(val level: Double?, val muted: Boolean?, val controlType: String?) {
        /** A `fixed` device is wired to an amplifier that controls the volume, so setting a level has no effect. */
        val canBeSet: Boolean get() = controlType != "fixed"
    }

    fun app(appId: String): Application? = applications.firstOrNull { it.appId == appId }

    companion object {
        fun of(payload: String): ReceiverStatus? {
            val root = objectOf(payload) ?: return null
            if (root.string("type") != "RECEIVER_STATUS") return null
            val status = root["status"] as? JsonObject ?: return null
            val apps = (status["applications"] as? JsonArray).orEmpty().mapNotNull { element ->
                val app = element as? JsonObject ?: return@mapNotNull null
                Application(
                    appId = app.string("appId") ?: return@mapNotNull null,
                    sessionId = app.string("sessionId") ?: return@mapNotNull null,
                    transportId = app.string("transportId") ?: return@mapNotNull null,
                    namespaces = (app["namespaces"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("name") },
                )
            }
            val volume = (status["volume"] as? JsonObject)?.let {
                Volume(it.double("level"), (it["muted"] as? JsonPrimitive)?.booleanOrNull, it.string("controlType"))
            }
            return ReceiverStatus(apps, volume)
        }
    }
}

/** What the receiver reports about the stream (one entry of `MEDIA_STATUS`). */
data class MediaStatus(
    val mediaSessionId: Int,
    /** `IDLE`, `BUFFERING`, `PLAYING`, `PAUSED`. */
    val playerState: String,
    val currentTime: Double?,
    /** Why it is idle: `FINISHED`, `CANCELLED`, `INTERRUPTED` or `ERROR`. Absent for a LOAD that was accepted
     * but has not started yet. */
    val idleReason: String?,
)

/**
 * The room's answer when asked for its status. There are three cases. A receiver whose media has ended
 * answers `GET_STATUS` with an empty list. If that were read as "no status", it would be the same as no
 * answer at all, and a stop at the speaker would have no effect on the phone. Ported from
 * `RPMCast/CastMedia.swift`.
 */
sealed interface RoomReport {
    data class Holding(val status: MediaStatus) : RoomReport

    /** It answered and has nothing loaded: after a stop at the speaker, or between two streams. */
    data object NothingLoaded : RoomReport

    /** It did not answer. */
    data object NoAnswer : RoomReport

    companion object {
        fun of(payload: String?): RoomReport {
            val root = payload?.let(::objectOf) ?: return NoAnswer
            if (root.string("type") != "MEDIA_STATUS") return NoAnswer
            val first = (root["status"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return NothingLoaded
            return mediaStatusOf(first)?.let(::Holding) ?: NothingLoaded
        }
    }
}

/** The meaning of a receiver's answer to a LOAD. */
sealed interface LoadAnswer {
    /** The receiver has the stream. This is the media session that every later command carries. It does not mean
     * the stream is playing. */
    data class Taken(val mediaSessionId: Int) : LoadAnswer

    /** A later LOAD reached the receiver first, so it dropped this one. This is not a refusal, so nothing is reported. */
    data object Superseded : LoadAnswer

    data class Refused(val reason: String) : LoadAnswer

    companion object {
        fun of(payload: String): LoadAnswer {
            val root = objectOf(payload) ?: return Refused("unreadable")
            return when (root.string("type")) {
                // Check for cancelled before refused: a receiver that means "overtaken" is not refusing.
                "LOAD_CANCELLED" -> Superseded
                "LOAD_FAILED", "INVALID_REQUEST", "INVALID_PLAYER_STATE" -> Refused(root.string("reason") ?: root.string("type").orEmpty())
                "MEDIA_STATUS" -> {
                    val first = (root["status"] as? JsonArray)?.firstOrNull() as? JsonObject
                    first?.let(::mediaStatusOf)?.let { Taken(it.mediaSessionId) } ?: Refused("no media session")
                }
                else -> Refused(root.string("type").orEmpty())
            }
        }
    }
}

/**
 * What RPM's receiver is told about the current record: the `meta` message. The stream stays the same while the
 * records inside it change, so this message keeps the screen up to date. It also sets the clock anchor: at
 * stream second [streamAt] the record was [trackAt] seconds in. Ported from `CastNowPlaying`
 * (`RPMCast/CastControl.swift`).
 */
data class CastNowPlaying(
    val title: String,
    val artist: String?,
    val album: String?,
    val artUrl: String?,
    val seconds: Double?,
    val trackAt: Double,
    val streamAt: Double,
) {
    fun payload(): String = buildJsonObject {
        put("type", ControlWords.META)
        put("title", title)
        put("trackAt", trackAt)
        put("streamAt", streamAt)
        artist?.let { put("artist", it) }
        album?.let { put("album", it) }
        artUrl?.let { put("art", it) }
        seconds?.let { put("duration", it) }
    }.toString()
}

/** The message types on the control channel, in both directions. `ReceiverPageTest` checks that they match
 * the receiver page. */
object ControlWords {
    // Sent to the receiver.
    const val META = "meta"
    const val ART = "art"

    /** Every message type this app sends. */
    val SENT = listOf(META, ART)

    // Sent by the receiver.
    const val NEXT = "next"
    const val PREV = "prev"
    const val ART_FAILED = "artfail"
    const val HELLO = "hello"
    const val PLAYING = "playing"
    const val STARVED = "starved"
    const val ERROR = "error"
    const val LOAD = "load"
    const val FAVORITE = "favorite"

    /** Every message type this app reads. */
    val HEARD = listOf(NEXT, PREV, ART_FAILED, HELLO, PLAYING, STARVED, ERROR, LOAD, FAVORITE)

    /** The protocol version the receiver echoes in `hello` (its `PROTOCOL`). */
    const val PROTOCOL = 1

    /**
     * The cover as bytes, for a receiver that could not fetch it from its address. Null is also a valid answer:
     * this record has no cover. A picture over [ART_LIMIT] is sent as null, because a message over the frame limit
     * makes some receivers (seen on a Shield) reset the socket instead of refusing the message.
     */
    fun art(jpeg: ByteArray?): String = buildJsonObject {
        put("type", ART)
        val fits = jpeg?.takeIf { it.isNotEmpty() && it.size <= ART_LIMIT }
        if (fits != null) put("data", "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(fits)) else put("data", JsonNull)
    }.toString()

    /** The largest cover size in bytes before base64 adds a third. The frame limit is 64 KiB, and the JSON and
     * protobuf around the cover need some room. Same as the phone's `CastCover.limit`. */
    const val ART_LIMIT = 44_000
}

/** A message from RPM's receiver. */
sealed interface RoomWord {
    data object Next : RoomWord
    data object Previous : RoomWord

    /** The receiver could not load the cover from its address. Reply with the cover bytes. */
    data object ArtFailed : RoomWord

    /** Any other message: it is logged and nothing else happens. */
    data class Said(val type: String, val payload: String) : RoomWord

    companion object {
        fun of(payload: String): RoomWord? = when (val type = CastPayload.typeOf(payload)) {
            null -> null
            ControlWords.NEXT -> Next
            ControlWords.PREV -> Previous
            ControlWords.ART_FAILED -> ArtFailed
            else -> Said(type, payload)
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

internal fun objectOf(payload: String): JsonObject? =
    runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

private fun mediaStatusOf(status: JsonObject): MediaStatus? {
    val session = (status["mediaSessionId"] as? JsonPrimitive)?.intOrNull ?: return null
    return MediaStatus(
        mediaSessionId = session,
        playerState = status.string("playerState").orEmpty(),
        currentTime = status.double("currentTime"),
        idleReason = status.string("idleReason"),
    )
}
