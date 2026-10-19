package com.rpm.player.core.radio

import com.rpm.player.core.net.WireJson
import com.rpm.player.core.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** Every Radio Browser mirror failed. Saved stations still play. */
class RadioDirectoryUnavailable(cause: Throwable?) : IOException("no Radio Browser mirror answered", cause)

/**
 * What to ask the directory for. Every field must be stated: a search with no country passes `null` for
 * it on purpose, and does not leave it out. [tag] is a genre's search term, matched loosely (`tagExact`
 * false), as the desktop asks.
 */
data class RadioQuery(val name: String?, val tag: String?, val countryCode: String?, val limit: Int)

/** A country the directory files stations under, and how many. */
data class RadioCountry(val name: String, val code: String, val stationCount: Int)

/**
 * The Radio Browser directory (radio-browser.info), a port of `RadioBrowserClient.cs` in RPM.Core.
 * Every call tries the mirror that last answered, then the others in turn, and fails with
 * [RadioDirectoryUnavailable] only when none answers. Stations are searched with `json/stations/search`,
 * ordered by clicks, with broken stations hidden, as the desktop and the iOS app do. Stations are also
 * fetched by uuid with `json/stations/byuuid`, for stations that the admin has retagged ([byUuid]).
 */
class RadioBrowser(private val http: OkHttpClient) {

    /** The mirror that answered last. If two calls race to set it, it ends up as one of two hosts that
     * both answered, which is fine. */
    @Volatile
    private var preferred = MIRRORS.first()

    suspend fun search(query: RadioQuery): List<RadioStation> {
        val path = buildString {
            append("json/stations/search?hidebroken=true&order=clickcount&reverse=true")
            query.name?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&name=").append(escape(it)) }
            query.tag?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&tagExact=false&tag=").append(escape(it)) }
            query.countryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&countrycode=").append(escape(it)) }
            append("&limit=").append(query.limit.coerceIn(1, 1000))
        }
        val text = get(path, CacheControl.FORCE_NETWORK)
        return WireJson.decodeFromString(ListSerializer(StationDto.serializer()), text).mapNotNull(StationDto::toStation)
    }

    /**
     * The stations with these uuids (`json/stations/byuuid`), broken ones included. These are the
     * stations that the admin filed under a genre that their directory tags do not name, so a genre
     * search never returns them. Asked a hundred at a time.
     */
    suspend fun byUuid(uuids: List<String>): List<RadioStation> =
        uuids.filter { it.isNotBlank() }.chunked(100).flatMap { chunk ->
            val text = get("json/stations/byuuid?uuids=" + chunk.joinToString(",") { escape(it.trim()) }, CacheControl.FORCE_NETWORK)
            WireJson.decodeFromString(ListSerializer(StationDto.serializer()), text).mapNotNull(StationDto::toStation)
        }

    /** Every country the directory files stations under (`json/countries`), with how many. */
    suspend fun countries(): List<RadioCountry> {
        val text = get("json/countries", CacheControl.FORCE_NETWORK)
        return WireJson.decodeFromString(ListSerializer(CountryDto.serializer()), text).mapNotNull { row ->
            val code = row.iso_3166_1?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: return@mapNotNull null
            RadioCountry(row.name?.trim().orEmpty(), code, row.stationcount.coerceAtLeast(0))
        }
    }

    @Serializable
    private data class CountryDto(val name: String? = null, val iso_3166_1: String? = null, val stationcount: Int = 0)

    /**
     * Tells the directory that a station was tuned (its click count orders every search). Fire and
     * forget: it never throws and touches no state. Asked past any cache, so that every tune is counted.
     */
    suspend fun click(uuid: String) {
        try {
            get("json/url/${escape(uuid)}", CacheControl.FORCE_NETWORK)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun get(path: String, cache: CacheControl): String {
        var last: Throwable? = null
        val first = preferred
        for (host in listOf(first) + MIRRORS.filter { it != first }) {
            try {
                val request = Request.Builder()
                    .url("https://$host/$path")
                    .header("User-Agent", USER_AGENT)
                    .cacheControl(cache)
                    .build()
                http.newCall(request).await().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} from $host")
                    val body = response.body.string()
                    preferred = host
                    return body
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw RadioDirectoryUnavailable(last)
    }

    private fun escape(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    @Serializable
    private data class StationDto(
        val stationuuid: String? = null,
        val name: String? = null,
        val url: String? = null,
        val url_resolved: String? = null,
        val homepage: String? = null,
        val favicon: String? = null,
        val tags: String? = null,
        val country: String? = null,
        val countrycode: String? = null,
        val language: String? = null,
        val codec: String? = null,
        val bitrate: Int = 0,
        val votes: Int = 0,
        val clickcount: Int = 0,
        val hls: Int = 0,
        val lastcheckok: Int = 1,
    ) {
        /** The station, or null without a stream, a name or a uuid. The resolved address is preferred:
         * the directory has already followed a playlist file to the stream inside it. */
        fun toStation(): RadioStation? {
            val stream = url_resolved?.trim()?.takeIf { it.isNotEmpty() } ?: url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val name = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val uuid = stationuuid?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return RadioStation(
                uuid = uuid,
                name = name,
                streamUrl = stream,
                homepage = homepage.blankless(),
                favicon = favicon.blankless(),
                country = country.blankless(),
                countryCode = countrycode.blankless()?.uppercase(),
                language = language.blankless(),
                tags = tags.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
                codec = codec.blankless(),
                bitrateKbps = bitrate.coerceAtLeast(0),
                votes = votes.coerceAtLeast(0),
                clickCount = clickcount.coerceAtLeast(0),
                isHls = hls != 0,
                reachable = lastcheckok != 0,
            )
        }

        private fun String?.blankless(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        /** The desktop's list, with `all.` first: it is DNS round-robin over the others. */
        val MIRRORS = listOf(
            "all.api.radio-browser.info",
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "at1.api.radio-browser.info",
            "nl1.api.radio-browser.info",
        )

        /** Sent to the directory and to every station's stream, as the desktop does. */
        const val USER_AGENT = "RPM/1.0"
    }
}
