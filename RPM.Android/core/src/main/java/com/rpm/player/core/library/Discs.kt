package com.rpm.player.core.library

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import com.rpm.player.core.cloud.CloudClient
import com.rpm.player.core.cloud.CoverSize
import com.rpm.player.core.cloud.Shelf
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A record's disc picture to draw: the transparent PNG the desktop uploads under the album's disc key
 * (`AlbumArt.DiscKeyFor`, a hash of `disc|album artist|album`), on [shelf], with the keys to try in order
 * and when the first was written (0 where that is not known, which keeps it out of the disk cache).
 */
data class DiscArt(val shelf: Shelf, val keys: List<String>, val stamp: Long)

/**
 * The disc picture for [album], or null when the index lists none for it. An index that could not be read
 * gives null too: most albums have no disc picture, and asking for each one without the index costs a
 * request per record that ends in a 404. The iOS app also asks only for discs its listing names
 * (`ArtworkStore.warm(discOf:)`).
 */
fun discFor(album: CloudAlbum, index: CoverIndex, shelf: Shelf): DiscArt? {
    val known = index as? CoverIndex.Known ?: return null
    val keys = AlbumArtKey.spellings(RecordPicture.DISC, album.tracks).filter { it in known.keys }
    if (keys.isEmpty()) return null
    return DiscArt(shelf, keys, known.stamps[keys.first()] ?: 0L)
}

/**
 * Fetches a [DiscArt] the way [CoverFetcher] fetches a cover, but always the original. The API's sized copies are
 * JPEGs laid on black (`CoverSizes.Cut`), and a disc picture is a circle with transparent corners and a hole in
 * the middle: as a copy it would come back as a black square.
 */
class DiscFetcher(
    private val disc: DiscArt,
    private val options: Options,
    private val cloud: CloudClient,
    private val disk: DiskCache?,
) : Fetcher {
    override suspend fun fetch(): FetchResult =
        StoredPictures.fetch(StoredPictures.DISC, disc.shelf, disc.keys, disc.stamp, CoverSize.Original, options, cloud, disk)

    class Factory(private val cloud: CloudClient) : Fetcher.Factory<DiscArt> {
        override fun create(data: DiscArt, options: Options, imageLoader: ImageLoader): Fetcher =
            DiscFetcher(data, options, cloud, imageLoader.diskCache)
    }
}

class DiscKeyer : Keyer<DiscArt> {
    override fun key(data: DiscArt, options: Options): String = StoredPictures.memoryKey(StoredPictures.DISC, data.shelf, data.keys)
}

/**
 * Whether a disc picture is a whole record or only its label, read off its transparency. It follows the desktop's
 * `DiscStamp.Covers` and the iOS app's `ScannedDisc`, which ask the same question of the same pixels.
 *
 * A scan of a CD is opaque out to its rim and replaces the drawn record. A scan of a vinyl label is a square the
 * size of the whole disc that is transparent outside its middle third, and is drawn on the drawn record, which
 * shows around it. Nothing about where the picture came from says which it is, so the picture is read.
 */
object DiscScan {
    /** Where on the radius the alpha is read: outside any label and inside the rim of any disc. */
    private const val RIM = 0.8

    /** How many points around that ring are read. */
    private const val SAMPLES = 64

    /**
     * True when at least half of the points read on the ring at [RIM] of the radius are opaque (alpha 128 or
     * more). [alphaAt] gives the alpha (0 to 255) of a pixel. True for a picture too small to read: it is taken
     * to be a whole record.
     */
    fun coversRecord(width: Int, height: Int, alphaAt: (x: Int, y: Int) -> Int): Boolean {
        val radius = min(width, height) / 2.0 * RIM
        if (radius < 4) return true
        val cx = width / 2.0
        val cy = height / 2.0
        var opaque = 0
        var taken = 0
        for (i in 0 until SAMPLES) {
            val angle = 2 * Math.PI * i / SAMPLES
            val x = (cx + radius * cos(angle)).roundToInt()
            val y = (cy + radius * sin(angle)).roundToInt()
            if (x < 0 || y < 0 || x >= width || y >= height) continue
            taken++
            if (alphaAt(x, y) >= 128) opaque++
        }
        return taken == 0 || opaque * 2 >= taken
    }
}
