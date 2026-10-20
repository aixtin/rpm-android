package com.rpm.player.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

/**
 * A focusable card, marked for the remote without a glow: the focused card grows by
 * [CARD_GROW] about its centre and is drawn at full brightness; every other card is drawn at [CARD_LIGHT]
 * brightness. Buttons and rows are marked the same way ([deskFocus]). [content] is told whether the card has the focus,
 * and is drawn in a layer kept between frames while it has not ([cachedLayer]); the dim is that layer's colour
 * filter, so a dimmed card costs no more to draw than a card at full brightness.
 *
 * While it has the focus it tells the nearest [FocusSpot] where it stands, grown, as [deskFocus] does, so a row's menu
 * opened on it ([heldOkMenu]) keeps it sharp and sits beside it.
 */
@Composable
fun CardButton(
    onClick: () -> Unit,
    modifier: Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val look = rememberCardLook(focused)
    val spot = LocalFocusSpot.current
    val share = LocalDrawnAt.current
    val shown = LocalFocusVisibility.current.shown
    val me = remember { Any() }
    // Where the grown card stands, told from the box that grows. Watched only on the focused card while the focus is
    // drawn, so the cards that are only scrolled past cost nothing for it.
    val telling = if (focused && shown && spot != null) {
        Modifier.onGloballyPositioned { c ->
            if (!c.isAttached) return@onGloballyPositioned
            val bounds = c.boundsInScreens(share)
            val w = bounds.width * CARD_GROW
            val h = bounds.height * CARD_GROW
            val centre = bounds.center
            spot.show(me, Spot(Rect(centre.x - w / 2, centre.y - h / 2, centre.x + w / 2, centre.y + h / 2), 0f, bounds, 0f))
        }
    } else {
        Modifier
    }
    // The grow is drawn inside the focus target, never around it. A scroll that brings the focused card into
    // view measures it through every layer above it and keeps its unscaled size at the grown box's corner, so a
    // grow around the target would move the box the page centres by 4% of the card's height: the page would
    // follow it down and up on every move along a row. The inner box fills the card's padded area
    // ([propagateMinConstraints]), so the grow is about the centre of the card.
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (!focused) spot?.hide(me)
            }
            .remembersFocus()
            .then(modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = contentAlignment,
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .then(telling)
                .graphicsLayer {
                    scaleX = look.grow
                    scaleY = look.grow
                }
                .cachedLayer(cached = !focused, light = { look.light }),
            contentAlignment = contentAlignment,
        ) { content(focused) }
    }
}

/**
 * A card's grow and brightness for the focus, each eased over [GROW_MS]. Read them in a layer or draw block,
 * so the ease redraws the card without composing it again.
 */
@Stable
class CardLook internal constructor(growState: State<Float>, lightState: State<Float>, quietState: State<Boolean>) {
    private val eased by growState
    private val quiet by quietState
    /** 1 at once while a row's menu is open over the card's layer ([FocusSpot.quiet]), not eased back. */
    val grow: Float get() = if (quiet) 1f else eased
    val light by lightState
}

/**
 * The look of a card that is [focused] or not. Nothing grows or dims until [LocalFocusVisibility] is shown,
 * so home opens at full brightness with nothing chosen; the first key press then dims every card but the
 * focused one. Each card's look depends only on its own focus and that one flag, so the order in which two
 * cards hear of a focus move does not matter.
 *
 * While a row's menu is open over the card's layer ([FocusSpot.quiet]) no card grows, and the card that had the
 * focus as the menu opened keeps its full brightness, as the item the menu is about, drawn bare.
 */
@Composable
fun rememberCardLook(focused: Boolean): CardLook {
    val shown = LocalFocusVisibility.current.shown
    val quiet = LocalFocusSpot.current?.quiet == true
    val opener = remember(quiet) { quiet && focused }
    val grow = animateFloatAsState(if (focused && shown && !quiet) CARD_GROW else 1f, tween(GROW_MS), label = "grow")
    val light = animateFloatAsState(if (focused || opener || !shown) 1f else CARD_LIGHT, tween(GROW_MS), label = "light")
    val quietState = rememberUpdatedState(quiet)
    return remember(grow, light, quietState) { CardLook(grow, light, quietState) }
}

/** The focused card's size: 8% larger, the same as a home card grows. */
const val CARD_GROW = 1.08f

/** The brightness of a card without the focus: 25% darker. */
const val CARD_LIGHT = 0.75f
