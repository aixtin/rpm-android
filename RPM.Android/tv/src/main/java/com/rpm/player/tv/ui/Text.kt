package com.rpm.player.tv.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.LocalContentColor

/**
 * A line or block of text, as TV Material's `Text` draws it, without the layer that TV Material wraps it in.
 *
 * TV Material 1.1's `Text` puts every text in a graphics layer with offscreen compositing. Each text is then
 * drawn into its own texture, which is a separate render pass whenever the text is first drawn or changes.
 * On the home screen every card title and line, every quick entry's two lines, every heading and every glyph
 * became a layer: a row coming back into view drew 30 to 54 layers in one frame, which took 30 to 97 ms on an
 * Amiko. Here the text is drawn where it stands. A colour with alpha is applied per glyph, as in Compose's own
 * Material text.
 *
 * The colour is chosen as TV Material chooses it: [color], else the colour of [style], else [LocalContentColor].
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle,
) {
    val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = ink, textAlign = textAlign ?: TextAlign.Unspecified)),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}
