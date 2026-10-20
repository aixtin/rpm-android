package com.rpm.player.tv.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.rpm.player.core.R
import com.rpm.player.core.motion.LocalMotion
import com.rpm.player.core.motion.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One entry of the key legend: the key's glyph and what it does on the screen. */
class LegendKey(val glyph: Char, @StringRes val label: Int)

/**
 * The keys that do something worth saying on [route], two to four of them, as the screens' own code has them:
 * Back on home opens the side menu (RpmApp's `back`) and on every other screen leaves it; a held OK opens a
 * row's menu wherever a row carries [heldOkMenu] (home's quick songs are left out: most of home is cards,
 * where a held OK does nothing); the artists list's alphabet is entered with Right from the last column
 * ([ArtistsScreen]); the carousel turns on Left and Right and opens its letters on Up ([AlbumCarousel]); and
 * radio opens its modes on Right held for 2 seconds ([RadioScreen]). [carousel] is whether the albums screen is
 * the carousel ([PlayerSettings.albumCarousel]).
 */
fun legendOf(route: Route, carousel: Boolean): List<LegendKey> {
    val move = LegendKey(Fa.ARROWS, R.string.Tv_LegendMove)
    val open = LegendKey(Fa.OK, R.string.Playlists_Reopen)
    val heldMenu = LegendKey(Fa.OK, R.string.Tv_LegendHoldMenu)
    val back = LegendKey(Fa.UNDO, R.string.Album_Back)
    return when (route) {
        Route.Home -> listOf(LegendKey(Fa.UNDO, R.string.Viz_Menu), move, open)
        Route.Albums ->
            if (carousel) listOf(LegendKey(Fa.ARROWS_H, R.string.Tv_LegendTurn), LegendKey(Fa.ARROW_UP, R.string.Tv_LegendLetters), open, back)
            else listOf(move, open, back)
        Route.Search -> listOf(move, open, heldMenu, back)
        Route.Radio -> listOf(LegendKey(Fa.OK, R.string.Tv_LegendTune), heldMenu, LegendKey(Fa.ARROW_RIGHT, R.string.Tv_LegendHoldModes), back)
        Route.Artists -> listOf(move, open, LegendKey(Fa.ARROW_RIGHT, R.string.Tv_LegendLetters), back)
        is Route.Record, is Route.OfList -> listOf(move, LegendKey(Fa.OK, R.string.AlbumMenu_Play), heldMenu, back)
        is Route.OfArtist -> listOf(move, open, heldMenu, back)
        Route.Playlists, Route.Computers, Route.Genres, is Route.OfGenre -> listOf(move, open, back)
    }
}

/**
 * Which screen the key legend is up for, if any. A screen gets its legend once, as it is opened: the stack hands
 * every opening a new, larger id ([StackEntry]), so a screen come back to with Back, or shown again as the player or
 * the side menu closes over it, has an id already seen and gets none. Someone who has just opened a screen is the one
 * who may not know its keys; coming back to it, they have been there. Once per screen kind for the whole session was
 * weighed and left: the legend is five seconds long and goes on the first key, so it costs little to see again, and
 * the setting turns it off for good.
 */
@Stable
class LegendState {
    /** The screen the legend is up for; null when none is. */
    var entry by mutableStateOf<StackEntry?>(null)
        private set

    /** Whether the legend up has been told to go: a key was pressed, or its screen is no longer in front. */
    var away by mutableStateOf(false)
        private set

    private var newest = -1L

    /** Told whenever the screen in front changes, or whether the legend may be shown ([ready]) does. A screen not yet
     * given its legend gets it now, if it may; otherwise one that is up goes. */
    fun front(top: StackEntry, ready: Boolean) {
        if (ready && top.id > newest) {
            newest = top.id
            entry = top
            away = false
        } else if (entry != null && (!ready || entry != top)) {
            away = true
        }
    }

    /** Any key sends the legend away. */
    fun keyDown() {
        if (entry != null) away = true
    }

    internal fun gone(which: StackEntry) {
        if (entry == which) {
            entry = null
            away = false
        }
    }
}

/**
 * The key legend: a small card of the side menu's dimmed glass ([LiveGlassCard], corner 7·s, [GLASS_DIM]) with
 * the keys of the screen in front in one row, each its glyph (20·s) and what it does (20·s SemiBold), 28·s apart.
 * It fades in over [LEGEND_IN_MS], stays [LEGEND_HELD_MS] or until [LegendState.away], and fades out over
 * [LEGEND_OUT_MS]; under reduced motion ([Motion.Still]) it comes and goes at once, as the screens do
 * ([StackedScreen]). It takes no focus and no keys, so the remote acts on the screen under it all along.
 */
@Composable
fun ControlsLegend(state: LegendState, backdrop: Backdrop, carousel: Boolean, modifier: Modifier) {
    val entry = state.entry ?: return
    val fades = LocalMotion.current < Motion.Still
    val shown = remember(entry.id) { Animatable(0f) }
    LaunchedEffect(entry.id) {
        launch { if (fades) shown.animateTo(1f, tween(LEGEND_IN_MS, easing = QuadInOut)) else shown.snapTo(1f) }
        withTimeoutOrNull(LEGEND_HELD_MS) { snapshotFlow { state.away }.first { it } }
        if (fades) shown.animateTo(0f, tween(LEGEND_OUT_MS, easing = QuadInOut)) else shown.snapTo(0f)
        state.gone(entry)
    }
    val keys = remember(entry.route, carousel) { legendOf(entry.route, carousel) }
    LiveGlassCard(backdrop = backdrop, corner = 7.s, modifier = modifier.graphicsLayer { alpha = shown.value }) {
        Box(Modifier.matchParentSize().background(GLASS_DIM))
        Row(
            Modifier.padding(horizontal = 20.s, vertical = 14.s),
            horizontalArrangement = Arrangement.spacedBy(28.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            keys.forEach { key ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(key.glyph, 20.ts, Ink.Words)
                    Spacer(Modifier.width(10.s))
                    Text(stringResource(key.label), style = ui(20.ts, FontWeight.SemiBold), color = Ink.Words, maxLines = 1)
                }
            }
        }
    }
}

/** Where the legend stands: the bottom right, out of the way of the screens' own keys, unless the mini player is
 * there on this screen ([onMini]), then the bottom left. A mini player at the bottom centre is wide enough that a
 * legend of four keys in the bottom right would run under it, so the legend goes to the top right instead. */
fun legendCorner(onMini: MiniPlace?): Alignment = when (onMini) {
    MiniPlace.BottomRight -> Alignment.BottomStart
    MiniPlace.Bottom -> Alignment.TopEnd
    else -> Alignment.BottomEnd
}

/** How long the legend takes to come up, stays up, and goes. */
private const val LEGEND_IN_MS = 180
private const val LEGEND_HELD_MS = 5_000L
private const val LEGEND_OUT_MS = 220
