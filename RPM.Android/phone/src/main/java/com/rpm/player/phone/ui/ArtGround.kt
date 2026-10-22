package com.rpm.player.phone.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.rpm.player.phone.design.BlurredPicture
import com.rpm.player.core.room.Rgb

/**
 * [picture] is anything the image loader can draw: a record's cover, or an artist's photograph on the home page.
 * [accent] is the cover's accent ([rememberAccent]), laid over the blurred picture; none for a photograph.
 *
 * The background of a page that uses a record as its look. The iPhone app has the same `ArtGround`
 * (RPMUI/ArtGround.swift), and the album page and the player both use it. It is the app's own background,
 * with the picture behind it blurred until it is not recognisable. A black gradient sits on top. It is
 * darkest at the foot, because a long list of songs must stay readable there. The picture is blurred once as
 * it loads ([BlurredPicture]), not on every frame.
 */
@Composable
fun ArtGround(picture: Any?, images: ImageLoader, accent: Rgb? = null) {
    Box(Modifier.fillMaxSize()) {
        VinylGround()
        // A new picture fades in over the old one. The iPhone app does the same when the record changes.
        Crossfade(picture, animationSpec = tween(350), label = "ground") { shown ->
            if (shown == null) return@Crossfade
            BlurredPicture(shown, images, sigma = 60.dp, modifier = Modifier.fillMaxSize())
        }
        // The colour read off the picture, laid over it at about a fifth, so a ground made from a grey sleeve still has
        // the record's own cast. A photograph of a person gets none: the colour there is the photograph's.
        if (picture != null && accent != null) {
            Box(Modifier.fillMaxSize().background(accent.color().copy(alpha = 0.18f)))
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
