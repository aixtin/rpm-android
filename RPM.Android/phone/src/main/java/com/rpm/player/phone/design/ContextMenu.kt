package com.rpm.player.phone.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One entry of a [ContextMenu]: what it says, its icon, and what it does. The icon is drawn in Font Awesome's regular
 * (outline) face, which is the nearest this app has to the thin SF Symbols an iPhone menu shows.
 */
class MenuItem(val label: String, val glyph: String, val onPress: () -> Unit)

/**
 * A long-press menu, drawn as an iPhone draws SwiftUI's `contextMenu` on a dark screen. The iPhone app has no menu of
 * its own here: it uses the system's, so this copies the system's look rather than the app's other panels.
 *
 * - The screen behind is dimmed, except the row that was pressed, which stays lit on a faint platter as if lifted
 *   out of the page. iOS also blurs the screen. That blur is left out: a whole-screen blur on this phone costs more
 *   than the moment is worth, and the dim alone already says that the page is out of play.
 * - The menu is 250 dp wide, with 13 dp corners, rows at least 44 dp high, the label on the left in the system face
 *   at 17 and the icon on the right, and hairlines between the rows. It is frosted like iOS's menu material, with a
 *   dark fill over the frost so the labels read over any cover.
 * - It opens under the row when there is room, otherwise over it. Over the row, the items are in reverse order, as
 *   iOS orders a menu shown above its source: the first item is always the one nearest the finger.
 * - It grows out of the row with a spring, and fades away when it is put down. A tap outside it, or Back, puts it down.
 *
 * @param source where the pressed row is, in the coordinates of the window this overlay fills.
 * @param onDismiss called once the menu has faded, whether an item was chosen or not.
 */
@Composable
fun ContextMenu(backdrop: Backdrop, source: Rect, items: List<MenuItem>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 520f)) }
    fun close() {
        if (leaving) return
        leaving = true
        scope.launch {
            shown.animateTo(0f, tween(140))
            onDismiss()
        }
    }
    BackHandler(onBack = ::close)

    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.asPaddingValues(density)
    val top = with(density) { (insets.calculateTopPadding() + MENU_MARGIN).toPx() }
    val bottom = with(density) { (insets.calculateBottomPadding() + MENU_MARGIN).toPx() }
    val gap = with(density) { MENU_GAP.toPx() }
    val margin = with(density) { MENU_SIDE.toPx() }
    val platter = with(density) { PLATTER_CORNER.toPx() }
    // Whether the menu stands over the row: when there is no room for it under the row, and there is room over it.
    // Its height is worked out from its rows here, before it is drawn, so the order of the items and the corner it
    // grows from are known on the first frame. A label long enough to take two lines makes it a little taller; the
    // layout below places it by its true height.
    val tall = with(density) { (ROW_HEIGHT * items.size + HAIRLINE * (items.size - 1).coerceAtLeast(0)).toPx() }
    var screen by remember { mutableStateOf(0f) }
    val above = screen > 0f && source.bottom + gap + tall > screen - bottom && source.top - gap - tall >= top

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screen = it.height.toFloat() }
            .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
            .drawBehind {
                val hole = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(source, CornerRadius(platter)))
                }
                drawPath(hole, DIM)
                drawRoundRect(PLATTER, source.topLeft, source.size, CornerRadius(platter))
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = ::close),
    ) {
        Layout(
            content = {
                val shape = RoundedCornerShape(MENU_CORNER)
                Column(
                    Modifier
                        .width(MENU_WIDTH)
                        .graphicsLayer {
                            val grow = 0.6f + 0.4f * shown.value
                            scaleX = grow
                            scaleY = grow
                            transformOrigin = TransformOrigin(0.15f, if (above) 1f else 0f)
                        }
                        .frost(backdrop, shape, CARD_BLUR)
                        .clip(shape)
                        .background(MENU_FILL)
                        // A tap on the menu's own padding must not fall through to the dim, which would close it.
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    val ordered = if (above) items.asReversed() else items
                    ordered.forEachIndexed { at, item ->
                        if (at > 0) Box(Modifier.fillMaxWidth().height(HAIRLINE).background(SEPARATOR))
                        Entry(item) {
                            item.onPress()
                            close()
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val menu = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val y = when {
                above -> source.top - gap - menu.height
                source.bottom + gap + menu.height <= height - bottom -> source.bottom + gap
                // Room on neither side: the menu stands at the foot of the screen, over the row.
                else -> height - bottom - menu.height
            }
            val x = (source.left + margin).coerceIn(margin, (width - margin - menu.width).coerceAtLeast(margin))
            layout(width, height) { menu.place(x.roundToInt(), y.roundToInt()) }
        }
    }
}

/** One row of the menu: the label at the left, the icon at the right, lit while a finger is on it. */
@Composable
private fun Entry(item: MenuItem, onPress: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .background(if (pressed) PRESSED else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = onPress)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            item.label,
            Modifier.weight(1f).padding(end = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = system(17f).copy(color = Color.White),
        )
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            BasicText(item.glyph, style = iconOutline(16f).copy(color = Color.White))
        }
    }
}

/** iOS's context menu sizes, in points, which are dp here. */
private val MENU_WIDTH = 250.dp
private val ROW_HEIGHT = 44.dp
private val MENU_CORNER = 13.dp
private val MENU_GAP = 8.dp
private val MENU_SIDE = 16.dp
private val MENU_MARGIN = 8.dp
private val PLATTER_CORNER = 12.dp
private val HAIRLINE = 0.5.dp

/**
 * The colours of iOS's dark menu, which are the system's and not the app's theme ([Ink]): the dim over the screen,
 * the platter under the lifted row, the menu's fill over its frost, its separators and a pressed row.
 */
private val DIM = Color.Black.copy(alpha = 0.45f)
private val PLATTER = Color.White.copy(alpha = 0x14 / 255f)
private val MENU_FILL = Color(0xB8252528)
private val SEPARATOR = Color.White.copy(alpha = 0x26 / 255f)
private val PRESSED = Color.White.copy(alpha = 0x1F / 255f)
