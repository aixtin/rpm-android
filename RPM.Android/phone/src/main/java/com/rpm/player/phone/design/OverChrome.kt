package com.rpm.player.phone.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where a page's sheets are drawn: over the tab bar and the mini player, as the iPhone's sheets are. A page draws its
 * own content under the chrome, but a sheet opened from it (the article, the review, a new playlist, deleting the
 * account) covers the whole app there, its scrim included, and only its card keeps clear of the chrome. The shell
 * draws this layer straight after the chrome ([OverChromeLayer]), and a page hands it a sheet with [OverChrome].
 *
 * The sheet is still composed from the page's own state: what the layer draws is the page's latest content, so the
 * sheet changes when the page does, and it goes when the page goes.
 */
@Stable
class OverChromeHost {
    internal val shown = mutableStateListOf<OverChromeEntry>()
}

/** One sheet handed to the layer: the latest content the page gave it. */
internal class OverChromeEntry(val content: State<@Composable () -> Unit>)

/** The layer the shell gives its pages, or null outside the shell, where a sheet is drawn in place. */
val LocalOverChrome = staticCompositionLocalOf<OverChromeHost?> { null }

/**
 * Draws [content] over the chrome, in the shell's [OverChromeLayer], for as long as this is composed. Outside a shell
 * it is drawn where it stands.
 */
@Composable
fun OverChrome(content: @Composable () -> Unit) {
    val host = LocalOverChrome.current
    if (host == null) {
        content()
        return
    }
    val latest = rememberUpdatedState(content)
    val entry = remember { OverChromeEntry(latest) }
    DisposableEffect(host, entry) {
        host.shown.add(entry)
        onDispose { host.shown.remove(entry) }
    }
}

/** The pages' sheets, drawn by the shell over its chrome and under what covers the whole app (the player, the picker). */
@Composable
fun OverChromeLayer(host: OverChromeHost) {
    for (entry in host.shown) {
        key(entry) { entry.content.value() }
    }
}
