package com.rpm.player.tv.ui

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.rpm.player.core.room.CoverColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colours of a picture (a cover, or a list's picture): the room palette and the record's hue,
 * read on a background thread. The reading is keyed on the picture, so a change of picture cancels
 * the reading for the previous picture. Until the new picture is read, the old colours stay, so a
 * change of record turns the room gradually and does not flash it to the fallback colours. The result
 * is null before the first reading and when there is no picture.
 *
 * Every reading is kept for the life of the app in [READINGS], by the picture's memory key. A picture
 * that was read before gives its reading as the first value. A screen opened on it (the player, a
 * record's page) then draws its frame and its vinyl in the picture's colours from the first frame,
 * and not in grey until the reading comes back.
 */
@Composable
fun rememberCoverReading(picture: Any?, images: ImageLoader): CoverColor.Reading? {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<CoverColor.Reading?>(picture?.let { READINGS.get(readingKey(images, it, context)) }) }
    LaunchedEffect(picture) {
        if (picture == null) {
            reading = null
            return@LaunchedEffect
        }
        readCoverColours(images, picture, context)?.let { reading = it }
    }
    return reading
}

/**
 * Reads [picture]'s colours, or gives the reading kept from before, and keeps it in [READINGS], where
 * [rememberCoverReading] finds it on its first frame. A screen can call this ahead of drawing its cards (the radio
 * home does while it opens), so the cards come up in their colours rather than in grey. Null where the picture
 * cannot be read.
 */
suspend fun readCoverColours(images: ImageLoader, picture: Any, context: android.content.Context): CoverColor.Reading? {
    val key = readingKey(images, picture, context)
    READINGS.get(key)?.let { return it }
    val request = ImageRequest.Builder(context)
        .data(picture)
        .size(COLOUR_SAMPLE)
        .allowHardware(false)
        // Kept in memory apart from the cards' pictures. Under the same key, this small software
        // bitmap would replace a card's larger hardware bitmap, and the card would decode its cover again.
        .memoryCacheKeyExtra("use", "colour")
        .build()
    val bitmap = (images.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
    val read = withContext(Dispatchers.Default) { CoverColor.read(bitmap) }
    READINGS.put(key, read)
    return read
}

/** The key a reading is kept under: the picture's memory key ([memoryKeyOf]), else the picture itself. */
private fun readingKey(images: ImageLoader, picture: Any, context: android.content.Context): Any =
    memoryKeyOf(images, picture, context) ?: picture

/** Every picture's colours read so far, by [readingKey]. A reading is a few colours, so a few
 * hundred readings cost little memory. */
private val READINGS = LruCache<Any, CoverColor.Reading>(READINGS_KEPT)

/** How many readings are kept: more than a screen of covers, the rooms and the stations. */
private const val READINGS_KEPT = 400

/** The size a picture is decoded at to read its colours; the reading itself downscales to 48 x 48. */
private const val COLOUR_SAMPLE = 256
