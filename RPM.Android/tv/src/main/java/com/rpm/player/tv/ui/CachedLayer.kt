package com.rpm.player.tv.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Draws what it wraps into a layer of its own, kept between frames while [cached]. A card that has
 * not changed is then one picture for the render thread to issue and the GPU to draw, instead of every
 * picture, line of text and shade in it, and a scroll moves the picture. On the Amiko, scrolling home,
 * this lowers the render thread's time from 8.6 to 6.1 ms a frame and the GPU's time from 19 to 14 ms
 * (medians).
 *
 * Do not use it on a focused card ([cached] false): the card grows, and a layer is drawn at its own
 * size, so it would be scaled up and blurry. Do not use it on anything that changes every frame, such
 * as a drifting room, because its layer would be redrawn every frame on top of being shown.
 *
 * [light] is the brightness the layer is drawn at, read each time the layer is updated
 * ([CardLook.light]). Below 1 it is a colour filter on the layer, which multiplies the colours and
 * keeps the transparency. On a cached layer this costs nothing more. On a layer that is not cached,
 * the layer is drawn offscreen while it is dim.
 */
fun Modifier.cachedLayer(cached: Boolean, light: () -> Float): Modifier =
    graphicsLayer {
        compositingStrategy = if (cached) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        val l = light()
        colorFilter = if (l < 1f) ColorFilter.tint(Color(l, l, l), BlendMode.Modulate) else null
    }
