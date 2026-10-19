package com.rpm.player.phone.ui

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rpm.player.phone.design.radiusFor

/**
 * [picture] is anything the image loader can draw: a record's cover, or an artist's photograph on the home page.
 *
 * The background of a page that uses a record as its look. The iPhone app has the same `ArtGround`
 * (RPMUI/ArtGround.swift), and the album page and the player both use it. It is the app's own background,
 * with the picture behind it blurred until it is not recognisable. A black gradient sits on top. It is
 * darkest at the foot, because a long list of songs must stay readable there. Android 9 to 11 cannot blur
 * a picture, so there the picture is left out and only the background shows.
 */
@Composable
fun ArtGround(picture: Any?, images: ImageLoader) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        VinylGround()
        // A new picture fades in over the old one. The iPhone app does the same when the record changes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Crossfade(picture, animationSpec = tween(350), label = "ground") { shown ->
            if (shown == null) return@Crossfade
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(shown).build(),
                contentDescription = null,
                imageLoader = images,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(with(density) { radiusFor(60.dp.toPx()).toDp() }, BlurredEdgeTreatment.Rectangle),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0x59 / 255f),
                    0.42f to Color.Black.copy(alpha = 0x8C / 255f),
                    1f to Color.Black.copy(alpha = 0xBF / 255f),
                ),
            ),
        )
    }
}
