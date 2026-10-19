package com.rpm.player.tv.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the collection screens are drawn on, chosen in the settings panel. The moving room can be limited to the
 * now playing screen, while the pages use still backdrops. The player always has the moving room.
 *
 * [Default] and [Pegasus] are the desktop's backdrops of the same names (RPM.Desktop's `Backdrop`; the files are
 * copies in assets/backdrops). [Dark] is a flat anthracite. [Room] is the moving room.
 */
enum class PageBackground(val file: String?) {
    Default("backdrops/default.jpg"),
    Pegasus("backdrops/pegasus.jpg"),
    Dark(null),
    Room(null),
}

/** The colour of the dark backdrop, anthracite #28282D. It is drawn as it is, with no shade. */
val ANTHRACITE = Color(0xFF28282D)

/**
 * A still backdrop that fills its box: the picture cropped to fit, drawn at brightness [dim] (the screens' shade,
 * the same as the room's picture has), or the flat anthracite colour. [onShown] is called once the backdrop has
 * been drawn, so whatever it replaces can be removed without an empty frame in between.
 */
@Composable
fun BackdropPicture(background: PageBackground, dim: Float, onShown: () -> Unit, modifier: Modifier) {
    val told by rememberUpdatedState(onShown)
    val file = background.file
    if (file == null) {
        Box(modifier.background(ANTHRACITE))
        LaunchedEffect(background) {
            withFrameNanos { }
            told()
        }
        return
    }
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val picture by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decode(context, file, maxOf(metrics.widthPixels, metrics.heightPixels)) }
    }
    val shown = picture ?: return
    Image(
        bitmap = shown,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = if (dim < 1f) ColorFilter.tint(Color(dim, dim, dim), androidx.compose.ui.graphics.BlendMode.Modulate) else null,
        modifier = modifier,
    )
    LaunchedEffect(shown) {
        withFrameNanos { }
        told()
    }
}

/** The asset at [path], decoded by whole halvings (powers of two) to no less than [longest] pixels on its longer side. */
private fun decode(context: android.content.Context, path: String, longest: Int): ImageBitmap? = runCatching {
    val bytes = context.assets.open(path).use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longest) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
