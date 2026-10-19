package com.rpm.player.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpm.player.core.R
import com.rpm.player.core.net.AppKey

/**
 * Somebody's round picture. It shows their avatar when they have one ([picture], an address on the
 * API), else the first letter of [name], else [glyph]. The letter is drawn under the picture until the
 * picture is loaded, so the circle is never empty. The API returns an avatar only to a caller that
 * sends the app key, which every request here does.
 */
@Composable
fun FacePicture(picture: String?, name: String?, glyph: Char, images: ImageLoader, size: Dp, letter: TextUnit, modifier: Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Ink.Smoke), contentAlignment = Alignment.Center) {
        val initial = name?.trim()?.take(1)?.uppercase().orEmpty()
        if (initial.isNotEmpty()) {
            Text(initial, style = ui(letter, FontWeight.SemiBold), color = Ink.Card)
        } else {
            Glyph(glyph, letter * 0.75f, Ink.Card)
        }
        if (picture != null) {
            val context = LocalContext.current
            val edge = with(LocalDensity.current) { size.roundToPx() }
            val request = remember(picture, edge) {
                ImageRequest.Builder(context)
                    .data(picture)
                    .size(edge)
                    .httpHeaders(NetworkHeaders.Builder().set(AppKey.HEADER, AppKey.VALUE).build())
                    .crossfade(true)
                    .build()
            }
            AsyncImage(model = request, imageLoader = images, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * The supporter mark (the Swift head's `SupporterMark`, in its colours, SupporterMetal): a filled
 * heart and "Supporter" in pale gold on a pill of brushed gold with a gold rim. [size] is the text's
 * size in the desk's units (`ts`). The heart, the gaps and the pill scale with it.
 */
@Composable
fun SupporterMark(size: Float, modifier: Modifier) {
    val pill = RoundedCornerShape(50)
    Row(
        horizontalArrangement = Arrangement.spacedBy((size * 0.5f).s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(SUPPORTER_FACE, pill)
            .border(1.5f.px, SUPPORTER_RIM, pill)
            .padding(horizontal = (size * 0.8f).s, vertical = (size * 0.2f).s),
    ) {
        Glyph(Fa.HEART, (size * 0.8f).ts, SUPPORTER_INK)
        Text(stringResource(R.string.Rpm_Profile_PlanSupporter), style = ui(size.ts, FontWeight.Medium), color = SUPPORTER_INK, maxLines = 1)
    }
}

private val SUPPORTER_INK = Color(0xFFF2DC9A)

/** Bottom left to top right, the same direction as the gradient of SupporterMetal.face. */
private val SUPPORTER_FACE = Brush.linearGradient(
    0f to Color(0x3A9A6A18),
    0.30f to Color(0x46EFAF12),
    0.42f to Color(0x72EBD07A),
    0.52f to Color(0x46EFAF12),
    1f to Color(0x389A6A18),
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f),
)

private val SUPPORTER_RIM = Brush.linearGradient(
    0f to Color(0x66B98B1E),
    0.45f to Color(0xCCEBD07A),
    1f to Color(0x66B98B1E),
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f),
)
