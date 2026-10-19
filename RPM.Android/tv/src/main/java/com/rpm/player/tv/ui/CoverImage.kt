package com.rpm.player.tv.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.rpm.player.core.radio.StationLogo
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import coil3.request.Disposable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A picture in a card's well. It is shown at once when the image loader has it in memory. When it
 * arrives from the disk or the network, it fades in over [COVER_IN_MS], so a cover does not appear
 * in one frame on the empty well. [onShown] is called once the picture is drawn whole: at once from
 * memory, or at the end of the fade otherwise.
 *
 * The fade starts when the picture arrives, not when it is first drawn, and it is read in the
 * picture's own layer. The loader's crossfade is not used. It starts at the first draw, so a card
 * composed ahead off screen would fade as it scrolled into view, and its kept layer ([cachedLayer])
 * would be redrawn on every frame of that fade. A late picture redraws its card's layer only for the
 * length of the fade.
 */
@Composable
fun CoverImage(model: Any, images: ImageLoader, contentScale: ContentScale, modifier: Modifier, onShown: (SuccessResult) -> Unit) {
    val scope = rememberCoroutineScope()
    val fade = remember(model) { Animatable(0f) }
    // Set when the picture came from memory. It is then drawn whole from its first frame.
    var instant by remember(model) { mutableStateOf(false) }
    val shown by rememberUpdatedState(onShown)
    AsyncImage(
        model = model,
        contentDescription = null,
        imageLoader = images,
        contentScale = contentScale,
        onSuccess = { state ->
            if (state.result.dataSource == DataSource.MEMORY_CACHE) {
                instant = true
                shown(state.result)
            } else {
                scope.launch {
                    fade.animateTo(1f, tween(COVER_IN_MS))
                    shown(state.result)
                }
            }
        },
        modifier = modifier.graphicsLayer {
            alpha = if (instant) 1f else fade.value
            // One picture: its alpha is applied to the single draw, with no layer of its own to compose it in.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    )
}

/** How long a cover that arrives late takes to fade in. */
const val COVER_IN_MS = 160

/**
 * A request that decodes [model] into the image loader's memory at [width] by [height] pixels, in the
 * same way as a card's own request would: no transformation (so the same memory key), filled and
 * inexact. This is what the loader's Compose image requests when a card gives it a size by its box
 * and crops. A card drawn later finds the picture in memory and shows it at once ([CoverImage]).
 */
fun aheadRequest(context: Context, model: Any, width: Int, height: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(model)
        .size(width, height)
        .scale(Scale.FILL)
        .precision(Precision.INEXACT)
        .build()

/**
 * Decodes the pictures past the end of a list into the image loader's memory once the list has come
 * to rest, so they are there when the next row or cards come into view. [lastVisible] is read on
 * every frame in which the list moves. [wanted] returns the requests for a resting place (the last
 * index on screen), or none.
 *
 * Only a place where the list has stayed for [AHEAD_SETTLE_MS] makes requests. Moving on cancels the
 * requests of the last place that have not finished. If every row that a scroll passed were
 * requested, with nothing cancelled, dozens of requests would queue ahead of the covers on screen,
 * and those covers would stay empty for seconds (on the wall on the Amiko). A request that has
 * already finished is not undone: its picture stays in memory.
 */
@OptIn(FlowPreview::class)
@Composable
fun DecodeAhead(images: ImageLoader, key: Any, lastVisible: () -> Int, wanted: (last: Int) -> List<ImageRequest>) {
    val read by rememberUpdatedState(lastVisible)
    val ask by rememberUpdatedState(wanted)
    LaunchedEffect(images, key) {
        var pending = emptyList<Disposable>()
        try {
            snapshotFlow { read() }
                .debounce(AHEAD_SETTLE_MS)
                .distinctUntilChanged()
                .collect { last ->
                    pending.forEach { it.dispose() }
                    pending = if (last < 0) emptyList() else ask(last).map { images.enqueue(it) }
                }
        } finally {
            pending.forEach { it.dispose() }
        }
    }
}

/** How long a list stays put before the pictures past it are requested. */
const val AHEAD_SETTLE_MS = 250L

/**
 * What a [CardRow] decodes ahead of the cards past its end. [of] gives a card's picture, or null for
 * a card whose picture is not a plain image request (a room, or an artist's photograph that is still
 * being requested). [edge] is the side of the square the card draws it in. It is the same size that
 * the card requests, so the memory key and size match.
 */
class RowPictures<T>(val images: ImageLoader, val edge: Dp, val of: (T) -> Any?)

/**
 * The mini player's picture. When the record changes, the last picture stays as the placeholder until
 * the new one is loaded, then it crossfades to the new one over [HELD_FADE_MS], so the well never goes
 * blank between two records. The first picture uses the same picture that a card left in memory, as
 * the player's sleeve does ([PlayerPicture]). The last picture is a placeholder only for a picture of
 * the same kind (a cover for a cover, a station's logo for a logo), because the two kinds are drawn
 * at different scales.
 */
@Composable
fun HeldCover(picture: Any, images: ImageLoader, contentScale: ContentScale, modifier: Modifier) {
    val context = LocalContext.current
    // The picture drawn before this one. It is not state: it is read only when the request is made again.
    val last = remember { arrayOfNulls<Any>(1) }
    val request = remember(picture, images) {
        val before = last[0]?.takeIf { it != picture && (it is StationLogo) == (picture is StationLogo) } ?: picture
        ImageRequest.Builder(context)
            .data(picture)
            // The key the last picture was stored under. It has no transformation and no size.
            .placeholderMemoryCacheKey(memoryKeyOf(images, before, context))
            .crossfade(HELD_FADE_MS)
            .build()
    }
    SideEffect { last[0] = picture }
    AsyncImage(model = request, imageLoader = images, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

/** How long the mini player's picture takes to crossfade from the last record's picture to the new one's. */
const val HELD_FADE_MS = 250

/**
 * The key under which the image loader keeps [model]'s picture in memory for a request with no
 * transformation and no extras, which is how the cards request pictures. The model is mapped as the
 * loader maps it (an address to a URI) and then keyed. Null when the loader has no key for the model.
 */
fun memoryKeyOf(images: ImageLoader, model: Any, context: Context): String? {
    val options = Options(context)
    return images.components.key(images.components.map(model, options), options)
}
