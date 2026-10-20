package com.rpm.player.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

/**
 * Where the remote's focus stands among buttons and rows: the focused item's grown bounds, for what opens at it (a
 * row's menu, [MenuOpening]). Nothing is drawn from it. The focused item is marked by its grow and by every other
 * item dimming ([deskFocus]), as cards are ([CardButton]). No glow is drawn behind it.
 *
 * One [FocusSpot] per layer that has its own items (the screen, the side menu, the queue drawer), provided by
 * [SpotScope]. An item marks itself with [deskFocus]; while it holds the focus it tells the nearest [FocusSpot]
 * where it is. The spot is the last item to have gained the focus and not lost it, so a loss reported after the
 * next item's gain cannot clear it.
 */
@Stable
class FocusSpot {
    internal var target by mutableStateOf<Spot?>(null)
    private var owner: Any? = null

    /**
     * How many row menus are open over this layer ([RowMenu]). While one is, the layer's focus is drawn bare: the
     * item that opened it is drawn as itself, at full brightness but neither grown nor glowing ([deskFocus],
     * [rememberCardLook]). The screen is blurred round it and the menu sits beside it, which says what was pressed;
     * the grow and the glow were in the picture the menu blurs, a lit box and an orb round the sharp item.
     */
    private var menus by mutableIntStateOf(0)

    /** Whether a row's menu is open over this layer. Read it in composition or in a draw. */
    val quiet: Boolean get() = menus > 0

    internal fun menuOpened() {
        menus += 1
    }

    internal fun menuClosed() {
        if (menus > 0) menus -= 1
    }

    internal fun show(who: Any, spot: Spot) {
        owner = who
        target = spot
    }

    internal fun hide(who: Any) {
        if (owner === who) {
            owner = null
            target = null
        }
    }
}

/**
 * Whether the remote's focus is drawn. The activity's first key press turns it on: the app opens with the
 * focus on home's banner and nothing lit, so the page is not scrolled to an item the person has not
 * chosen. [keyPressed] runs before the key is handled, so that press both lights the
 * focus and does its usual job.
 */
@Stable
class FocusVisibility(initiallyShown: Boolean) {
    var shown by mutableStateOf(initiallyShown)
        private set

    fun keyPressed() {
        if (!shown) shown = true
    }
}

/** Shown, where nothing provides one (a preview, a test). [MainActivity] provides one that starts unlit. */
val LocalFocusVisibility = staticCompositionLocalOf { FocusVisibility(initiallyShown = true) }

/** Where the focused item is: its grown bounds in the screens' pixels, its corner radius grown with it, and the
 * same before the grow ([bare], [bareCorner]): what a menu opened on it keeps sharp, the item being drawn bare
 * under a menu ([FocusSpot.quiet]). */
internal data class Spot(val rect: Rect, val corner: Float, val bare: Rect, val bareCorner: Float)

val LocalFocusSpot = staticCompositionLocalOf<FocusSpot?> { null }

/** Provides [spot] to [content]. */
@Composable
fun SpotScope(spot: FocusSpot, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalFocusSpot provides spot) {
        Box(modifier, content = content)
    }
}

/**
 * Marks a focusable item for the remote: while it holds the focus it grows by `min(1.12, 1 + 12·s / longest side)`
 * about its centre, as the desktop's focused item does, and is drawn at full brightness; without it, it is drawn at
 * [KEY_LIGHT], darker than a card without the focus. A focused key no larger than [GLOW_MOST] on either side (an icon
 * key: the player's transport, the wall's sort and search) also has a soft round glow behind it, [GLOW_REACH] past
 * its edge. Rows and wide buttons get no glow, because a heavy glow over them looks wrong. Put it before the
 * item's `clickable` or `focusable`, whose focus it reads. [corner] is the item's own corner radius, kept for
 * what opens at it ([FocusSpot]). Nothing grows or dims
 * until [LocalFocusVisibility] is shown. An item without the focus is drawn in a layer kept between frames
 * ([cachedLayer]), so its dim costs no more to draw than the item does.
 */
fun Modifier.deskFocus(corner: Dp): Modifier = composed {
    val spot = LocalFocusSpot.current
    val share = LocalDrawnAt.current
    val shown = LocalFocusVisibility.current.shown
    val density = LocalDensity.current
    val me = remember { Any() }
    var focused by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var growTo by remember { mutableStateOf(1f) }
    // Under a row's menu nothing grows or glows, and the item that had the focus as the menu opened stays at full
    // brightness, though the menu has the focus ([FocusSpot.quiet]). The draws below read [quiet] again, so the grow
    // and the glow are gone in the frame the menu opens, before the menu takes its picture of the screen.
    val quiet = spot?.quiet == true
    val opener = remember(quiet) { quiet && focused }
    val grow by animateFloatAsState(if (focused && shown && !quiet) growTo else 1f, tween(GROW_MS), label = "grow")
    val light by animateFloatAsState(if (focused || opener || !shown) 1f else KEY_LIGHT, tween(GROW_MS), label = "light")
    val glow by animateFloatAsState(if (focused && shown && !quiet) LocalGlowStrength.current else 0f, tween(GROW_MS), label = "glow")
    val cornerPx = with(density) { corner.toPx() }
    val glowMost = with(density) { GLOW_MOST.s.toPx() }
    val glowReach = with(density) { GLOW_REACH.s.toPx() }

    fun tell() {
        val c = coordinates ?: return
        if (!focused || !shown || spot == null || !c.isAttached) return
        val bounds = c.boundsInScreens(share)
        val w = bounds.width * growTo
        val h = bounds.height * growTo
        val centre = bounds.center
        spot.show(me, Spot(Rect(centre.x - w / 2, centre.y - h / 2, centre.x + w / 2, centre.y + h / 2), cornerPx * growTo, bounds, cornerPx))
    }

    LaunchedEffect(shown) { tell() }

    this
        .remembersFocus()
        .zIndex(if (focused) 1f else 0f)
        .onGloballyPositioned {
            coordinates = it
            val longest = maxOf(it.size.width, it.size.height).toFloat()
            growTo = if (longest > 0) minOf(MAX_GROW, 1f + with(density) { GROW_REACH.s.toPx() } / longest) else 1f
            tell()
        }
        .onFocusChanged {
            focused = it.isFocused
            if (focused) tell() else spot?.hide(me)
        }
        .graphicsLayer {
            val g = if (spot?.quiet == true) 1f else grow
            scaleX = g
            scaleY = g
        }
        // Outside the kept layer, so the dim never reaches it.
        .drawBehind {
            val g = if (spot?.quiet == true) 0f else glow
            if (g <= 0f || maxOf(size.width, size.height) > glowMost) return@drawBehind
            val half = maxOf(size.width, size.height) / 2
            val radius = half + glowReach
            val edge = half / radius
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.30f * g),
                    edge to Color.White.copy(alpha = 0.16f * g),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        .cachedLayer(cached = !focused, light = { light })
}

/** A focusable box that draws nothing of its own: the desktop's bare button, marked for the remote by
 * [deskFocus], which takes the whole of it, [modifier]'s size and background included. */
@Composable
fun DeskButton(
    onClick: () -> Unit,
    corner: Dp,
    modifier: Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .deskFocus(corner)
            .then(modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * A one-line text that scrolls sideways while [moving], after [READ_ACROSS_WAIT_MS], so a line a card cuts
 * short can be read whole. The desktop puts the whole line on the pointer as a tip; a remote has no
 * pointer, so the focused card shows it instead. A line that fits does not move. Pair it with
 * [TextOverflow.Clip] while moving: the ellipsis would scroll along with the words.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.readAcross(moving: Boolean): Modifier =
    if (moving) basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = READ_ACROSS_WAIT_MS) else this

/** How long a focused card's cut line stands still before it scrolls: long enough to read its start. */
const val READ_ACROSS_WAIT_MS = 1200

/** The grow's numerator, `2·6·s`. */
private const val GROW_REACH = 12
private const val MAX_GROW = 1.12f
/** How long the grow and the dim take, as a card's do. */
internal const val GROW_MS = 150

/** The brightness of a key or row without the focus: 10% darker than a card's [CARD_LIGHT]. */
const val KEY_LIGHT = CARD_LIGHT - 0.10f

/** How strong a focused key's glow is, 1 at full: a screen can soften it for the keys it holds (the tuner's 0.7). */
val LocalGlowStrength = staticCompositionLocalOf { 1f }

/** The largest side, in `s`, of a key that glows when focused, and how far past its edge the glow reaches. */
private const val GLOW_MOST = 90
private const val GLOW_REACH = 26
