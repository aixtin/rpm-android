package com.rpm.player.phone.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rpm.player.core.R
import com.rpm.player.core.papers.EncyclopediaArticle
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.GlassButton
import com.rpm.player.phone.design.GlassCard
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.Weight
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.kicker
import com.rpm.player.phone.design.pt

/** How many lines of an article are shown under a name before the Read more key. */
private const val ABOUT_LINES = 3

/** The width of the fade on the last shown line, before the Read more key. And the gap between the text and the key. */
private val FADE = 28.dp
private val BESIDE_KEY = 6.dp

/** The licence of Wikipedia's text. CC BY-SA requires a reuse to name the licence and link to it. */
private const val LICENCE = "https://creativecommons.org/licenses/by-sa/4.0/"

/**
 * The encyclopedia text about a record or an artist, shown under its name. It matches the iPhone's
 * `AboutText`. The paragraphs run together, and only the first [ABOUT_LINES] lines are shown. If there is
 * more text, the last line fades out before a Read more key. Tapping the text or the key opens the whole
 * article ([AboutSheet]). The text fades in once it is laid out.
 */
@Composable
fun AboutText(article: EncyclopediaArticle, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val words = remember(article) { article.extract.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var key by remember { mutableIntStateOf(0) }
    val clipped = layout?.hasVisualOverflow == true
    val shown by animateFloatAsState(if (layout != null) 1f else 0f, tween(300), label = "about")
    val fade = with(density) { FADE.toPx() }
    val gap = with(density) { BESIDE_KEY.toPx() }

    Box(modifier.fillMaxWidth().alpha(shown)) {
        BasicText(
            words,
            Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val text = layout ?: return@drawWithContent
                    if (!text.hasVisualOverflow) return@drawWithContent
                    // The last shown line fades out, and the space for the key is left clear.
                    val last = text.lineCount - 1
                    val top = text.getLineTop(last)
                    val bottom = text.getLineBottom(last)
                    val room = size.width - key - gap
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = room - fade, endX = room),
                        Offset(room - fade, top),
                        Size(fade, bottom - top),
                        blendMode = BlendMode.DstOut,
                    )
                    drawRect(Color.Black, Offset(room, top), Size(size.width - room, bottom - top), blendMode = BlendMode.DstOut)
                },
            maxLines = ABOUT_LINES,
            onTextLayout = { layout = it },
            style = display(14f).copy(color = Ink.white(0xB3), lineHeight = pt(14f * 1.2f + 7f)),
        )
        if (clipped) {
            val text = layout
            // Placed level with the middle of the last line.
            val middle = text?.let { (it.getLineTop(it.lineCount - 1) + it.getLineBottom(it.lineCount - 1)) / 2 } ?: 0f
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .onSizeChanged { key = it.width }
                    .offset { IntOffset(0, (middle - with(density) { 12.dp.toPx() }).toInt()) },
            ) {
                GlassButton(onClick = onOpen, stretches = false, height = 24.dp, radius = 12.dp, inset = 11.dp, size = 12f) { style ->
                    BasicText(stringResource(R.string.Rpm_About_ReadMore), style = style)
                }
            }
        }
    }
}

/**
 * The whole article, shown over the page. It matches the iPhone's `AboutSheet`. It has the heading, the name,
 * the paragraphs in a scroll that is as tall as the screen allows, Close and Read on Wikipedia keys, and the
 * licence. A tap outside the card closes it.
 *
 * @param heading what the article is, for example "About the artist". [name] is the name of the record or artist it is
 * about.
 * @param bottomRoom how much of the bottom of the screen the chrome covers. The card is placed in the space above it,
 * so its keys are never under the mini player.
 */
@Composable
fun AboutSheet(heading: String, name: String, article: EncyclopediaArticle, backdrop: Backdrop, bottomRoom: Dp, onClose: () -> Unit) {
    val links = LocalUriHandler.current
    val paragraphs = remember(article) { article.extract.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() } }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    val shown by animateFloatAsState(if (opened) 1f else 0f, tween(180), label = "sheet")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .alpha(shown)
            .background(Color.Black.copy(alpha = 0xB3 / 255f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .statusBarsPadding()
            .padding(bottom = bottomRoom),
        contentAlignment = Alignment.Center,
    ) {
        val textRoom = maxOf(160.dp, maxHeight - 280.dp)
        GlassCard(
            backdrop,
            Modifier
                .width(minOf(330.dp, maxWidth - 32.dp))
                // The card takes its own taps, so a tap on it does not close it.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            radius = 18.dp,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(heading, style = displayBold(19f).copy(color = Color.White))
                    BasicText(name, maxLines = 1, style = kicker())
                }
                Column(
                    Modifier.heightIn(max = textRoom).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (paragraph in paragraphs) {
                        BasicText(paragraph, style = display(15f).copy(color = Ink.white(0xD9), lineHeight = pt(15f * 1.2f + 3f)))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Narrow side padding, so that "Read on Wikipedia" fits in half the card in every language.
                    GlassButton(onClick = onClose, modifier = Modifier.weight(1f), height = 42.dp, inset = 8.dp, size = 14f) { style ->
                        BasicText(stringResource(R.string.About_Close), style = style)
                    }
                    GlassButton(onClick = { links.openUri(article.url) }, modifier = Modifier.weight(1f), weight = Weight.Primary, height = 42.dp, inset = 8.dp, size = 14f) { style ->
                        BasicText(stringResource(R.string.TrackInfo_ReadOnWikipedia), maxLines = 1, style = style)
                    }
                }
                BasicText(
                    stringResource(R.string.Wikipedia_Licence),
                    Modifier
                        .fillMaxWidth()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { links.openUri(LICENCE) },
                    style = display(11f).copy(color = Ink.white(0x80), textAlign = TextAlign.End),
                )
            }
        }
    }
}
