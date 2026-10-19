package com.rpm.player.core.radio

import android.util.LruCache
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.rpm.player.core.art.SharpScaled
import com.rpm.player.core.net.AppKey
import com.rpm.player.core.net.Hosts
import com.rpm.player.core.net.await
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException

/**
 * A station's logo to draw: the rpm API's mark for [uuid] (a 512 px picture the logo crawl settled on,
 * WebP or SVG), else the logos the directory lists for it. [stamp] is the logo stamp from `radio/state`.
 * It only changes the address, so a changed logo is not served from a cache, and it is part of the
 * cache key here for the same reason.
 *
 * The desktop goes on to the station's homepage icons and Wikipedia when both fail. That is not ported.
 */
data class StationLogo(val uuid: String, val stamp: String, val logos: List<String>) {
    companion object {
        fun of(station: RadioStation, stamp: String): StationLogo =
            StationLogo(station.uuid, stamp, listOfNotNull(station.favicon) + station.otherLogos)
    }
}

class StationLogoFetcher(
    private val logo: StationLogo,
    private val options: Options,
    private val http: OkHttpClient,
    private val held: LruCache<StationLogo, Pair<ByteArray, String?>>,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        // One download per logo per run, whichever surface asks first. A station's box, its colours and its
        // badge are three requests for one logo, and each one is a different picture in the image loader's memory.
        val bytes = held.get(logo) ?: download().also { held.put(logo, it) }
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes.first), options.fileSystem, SharpScaled),
            mimeType = bytes.second,
            dataSource = DataSource.NETWORK,
        )
    }

    private suspend fun download(): Pair<ByteArray, String?> {
        val api = Hosts.API + "radio/logo/" + logo.uuid.lowercase() + (if (logo.stamp.isNotEmpty()) "?v=" + logo.stamp else "")
        // A 404 means the API has no logo. Any other failure is not remembered as that, and the
        // directory's logos are still worth trying.
        return get(api, withKey = true) ?: logo.logos.firstNotNullOfOrNull { get(it, withKey = false) }
            ?: throw IOException("no logo for station ${logo.uuid}")
    }

    /** The body and its media type, or null when the answer is not a picture. */
    private suspend fun get(url: String, withKey: Boolean): Pair<ByteArray, String?>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", RadioBrowser.USER_AGENT)
                .apply { if (withKey) header(AppKey.HEADER, AppKey.VALUE) }
                .build()
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) return null
                val type = response.header("Content-Type")?.substringBefore(';')?.trim()
                if (type != null && !type.startsWith("image/")) return null
                response.body.bytes() to type
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    class Factory(private val http: OkHttpClient) : Fetcher.Factory<StationLogo> {
        /** Logos fetched this run, keyed by the logo asked for (the station, its logo stamp and its addresses),
         * up to [HELD_BYTES]. A changed logo has a new stamp, so it is fetched again. */
        private val held = object : LruCache<StationLogo, Pair<ByteArray, String?>>(HELD_BYTES) {
            override fun sizeOf(key: StationLogo, value: Pair<ByteArray, String?>): Int = value.first.size
        }

        override fun create(data: StationLogo, options: Options, imageLoader: ImageLoader): Fetcher =
            StationLogoFetcher(data, options, http, held)
    }

    private companion object {
        const val HELD_BYTES = 4 * 1024 * 1024
    }
}

class StationLogoKeyer : Keyer<StationLogo> {
    override fun key(data: StationLogo, options: Options): String = "station:" + data.uuid.lowercase() + ":" + data.stamp
}
