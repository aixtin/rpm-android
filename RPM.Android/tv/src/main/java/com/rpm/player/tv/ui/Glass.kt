package com.rpm.player.tv.ui

import android.graphics.Bitmap
import com.rpm.player.core.graphics.boxBlur
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositing
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * What the desktop's glass panels blur: the screen under them.
 *
 * The desktop's `GlassBrush` blurs the live backdrop. Android blurs a view's backdrop only from API 31,
 * and the boxes this app is for run 28 to 30, so the backdrop is taken once instead: [capture] renders
 * the screen layer to a bitmap, shrinks it to a sixth and blurs it there on a background thread (three
 * box passes, about a Gaussian of 21 px: 40% less than the desktop's 34 px). A panel draws its own part of
 * that picture under its smoke. The room is a surface view behind the window and is not in the picture; where the picture is
 * clear, the room shows through the panel, and the room is soft enough that it needs no blur.
 *
 * Right for panels over a screen that does not move while they are up (the side menu); a panel over a
 * moving screen captures again when it is shown.
 */
@Stable
class Backdrop(internal val layer: GraphicsLayer) {
    /** The share of the window's pixels [layer] is recorded at: the screens' ([DrawnAt]). Main thread. */
    var share by mutableStateOf(1f)

    /** The last capture, blurred, at [SHRINK] of the screen's size; null before the first. */
    var blurred by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * What stands in for the room under a panel: the room is a surface behind the window, so the capture
     * is clear wherever the room shows, and without a ground of its own the panel would show the sharp
     * screen under it through those parts. The room's own colours, dimmed as the room is drawn.
     */
    var ground by mutableStateOf(Color.Black)

    /**
     * What lies under the screens, which their layer does not hold: the page's picture, or a still of the room when
     * the room is the live surface behind the window. Asked for at each [capture] with the size of the screens'
     * picture (any size it answers is stretched to that), and drawn under it, so a blur shows the colours that are
     * really on the screen, not [ground] where the screens are clear. Null, or a null answer, leaves [ground] there.
     */
    var under: (suspend (width: Int, height: Int) -> Bitmap?)? = null

    /** Renders the screen layer and blurs it. False when the screen could not be rendered, which leaves the
     * last capture in place: a layer that must not show another moment's screen checks this. Cancelling it
     * also leaves the last capture in place. [radius] is the box radius at the shrunk size: [RADIUS] for the
     * panels' usual 21 px, [DEEP_RADIUS] for the live glass's 35 px. */
    suspend fun capture(radius: Int = RADIUS): Boolean {
        val full = try {
            layer.toImageBitmap().asAndroidBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        val base = try {
            under?.invoke(full.width, full.height)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        blurred = withContext(Dispatchers.Default) {
            val top = if (full.config == Bitmap.Config.HARDWARE) full.copy(Bitmap.Config.ARGB_8888, false) else full
            val soft = if (base == null) top else {
                // The screens over what is under them, as the screen shows them.
                val merged = Bitmap.createBitmap(top.width, top.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(merged)
                val under = if (base.config == Bitmap.Config.HARDWARE) base.copy(Bitmap.Config.ARGB_8888, false) else base
                canvas.drawBitmap(under, null, android.graphics.Rect(0, 0, top.width, top.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                canvas.drawBitmap(top, 0f, 0f, null)
                merged
            }
            val small = Bitmap.createScaledBitmap(soft, (soft.width / SHRINK).coerceAtLeast(1), (soft.height / SHRINK).coerceAtLeast(1), true)
            boxBlur(small, radius)
            small.asImageBitmap()
        }
        return true
    }
}

/** Records what this draws into [backdrop]'s layer, so a panel can be given a picture of it. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = drawWithContent {
    backdrop.layer.record { this@drawWithContent.drawContent() }
    drawLayer(backdrop.layer)
}

/**
 * A panel of the desktop's light glass: the blurred backdrop, then `#26FFFFFF`, then a 1 px rim lighter
 * at the top and the bottom than in the middle (LightSmokeBrush and LightSmokeRimBrush).
 */
@Composable
fun GlassPanel(backdrop: Backdrop?, corner: Dp, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(corner)
    var origin by remember { mutableStateOf(Offset.Zero) }
    // In the screens' own pixels, which the backdrop was taken in ([DrawnAt]).
    val share = LocalDrawnAt.current
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInScreens(share) }
            .clip(shape),
    ) {
        val picture = backdrop?.blurred
        if (picture != null) {
            Box(Modifier.matchParentSize().background(backdrop.ground))
            Canvas(Modifier.matchParentSize()) {
                val left = (origin.x / SHRINK).toInt()
                val top = (origin.y / SHRINK).toInt()
                val w = (size.width / SHRINK).toInt().coerceAtMost(picture.width - left).coerceAtLeast(1)
                val h = (size.height / SHRINK).toInt().coerceAtMost(picture.height - top).coerceAtLeast(1)
                drawImage(
                    picture,
                    srcOffset = IntOffset(left.coerceIn(0, picture.width - 1), top.coerceIn(0, picture.height - 1)),
                    srcSize = IntSize(w, h),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Ink.Smoke)
                .border(1.px.coerceAtLeast(androidx.compose.ui.unit.Dp.Hairline), RIM, shape),
        )
        content()
    }
}

private val RIM = Brush.verticalGradient(0f to Ink.SmokeRimTop, 0.5f to Ink.SmokeRimMiddle, 1f to Ink.SmokeRimBottom)

/**
 * The light glass over screens that move under it (the mini player's card and pill): what the screens draw
 * under the panels, blurred each time it is drawn, instead of a capture taken once. A capture lags what it
 * covers by its render and blur, and is taken as the banner begins to slide, so the glass could show the card
 * before for as long as the next one stays up.
 *
 * The boxes run Android 9 to 11, which blur only from 12, so the blur is drawn smaller and back: the screens'
 * layer ([Backdrop.layer], live) is drawn under the panels into a layer a quarter of the size, that into one a
 * quarter again, and back up in two steps of four, each step drawn with bilinear filtering. It is drawn once
 * for every panel of the group ([liveGlass] on the box that holds them), clipped to the panels' shapes
 * ([LiveGlassPanel]): a layer the system meets twice in a frame is drawn again in full, and so is everything
 * that draws it, so each chain costs its three layers on every frame whatever moved. Measured on the Amiko with
 * home at the top and a station playing: 18.2 to 18.5 ms a frame without the blur, 21.4 to 22.3 ms with one
 * chain, and 21.8 ms with a chain each for the card and the pill.
 *
 * The panels hand over their coordinates, and the group works out their shapes when it draws, which is after
 * every layout: a panel placed before or after the group's box gives the same shapes. All of it runs on the
 * main thread.
 */
@Stable
class LiveGlass(internal val backdrop: Backdrop) {
    internal var group: LayoutCoordinates? = null
    internal val panels = mutableStateMapOf<Any, Pair<LayoutCoordinates, Float>>()
}

/**
 * Draws [glass]'s blur behind this box's content, clipped to its panels, while [live]. A hidden group draws no
 * layer, so the screens are walked once. What it costs: the screens' tree walked a second time each frame, and
 * the three layers of the chain drawn again on every frame.
 */
@Composable
fun Modifier.liveGlass(glass: LiveGlass, live: Boolean): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    // The group is drawn at [local] of the window's pixels, the backdrop layer at its own share.
    val local = LocalDrawnAt.current
    val chain = rememberBlurChain()
    return this
        .onGloballyPositioned {
            glass.group = it
            origin = it.positionInScreens(local)
        }
        .drawBehind {
            val group = glass.group ?: return@drawBehind
            if (!group.isAttached) return@drawBehind
            val shapes = Path()
            for ((panel, corner) in glass.panels.values) {
                if (panel.isAttached) shapes.addRoundRect(RoundRect(group.localBoundingBoxOf(panel, clipBounds = false), CornerRadius(corner)))
            }
            clipPath(shapes) {
                drawRect(glass.backdrop.ground, topLeft = Offset(-LIVE_MARGIN, -LIVE_MARGIN), size = Size(size.width + 2 * LIVE_MARGIN, size.height + 2 * LIVE_MARGIN))
                if (!live) return@clipPath
                drawLiveBlur(chain, glass.backdrop, origin, local)
            }
        }
}

/** The layers of one live blur: [DEPTH] halvings down and as many doublings back, less the last, which is drawn
 * straight onto the panel. */
class BlurChain(internal val down: List<GraphicsLayer>, internal val up: List<GraphicsLayer>)

@Composable
fun rememberBlurChain(): BlurChain {
    val down = List(DEPTH) { rememberGraphicsLayer() }
    val up = List(DEPTH - 1) { rememberGraphicsLayer() }
    return remember(down, up) {
        (down + up).forEach { it.compositingStrategy = LayerCompositing.Offscreen }
        BlurChain(down, up)
    }
}

/**
 * The screens under this box, blurred, drawn over the box and [LIVE_MARGIN] past each edge: the one blur every glass
 * panel uses, so that the mini player, the side menu and the settings look exactly the same.
 *
 * The screens' layer is drawn at half its size, that at half again, [DEPTH] times, and back up the same way, each
 * step drawn with bilinear filtering. Halving and doubling one step at a time is what makes it smooth: a chain
 * that goes down and up by four at once shows its pixels. Six steps blur about as much as a Gaussian of 35 px,
 * close to the desktop's 34 px. [origin] is where this box
 * stands in the screens' pixels, [local] the share of the window's pixels this box is drawn at.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiveBlur(chain: BlurChain, backdrop: Backdrop, origin: Offset, local: Float) {
    val wide = size.width + 2 * LIVE_MARGIN
    val high = size.height + 2 * LIVE_MARGIN
    fun at(level: Int): IntSize {
        val by = (1 shl level).toFloat()
        return IntSize(ceil(wide / by).toInt().coerceAtLeast(1), ceil(high / by).toInt().coerceAtLeast(1))
    }
    chain.down[0].record(at(1)) {
        drawRect(backdrop.ground)
        scale(0.5f, 0.5f, pivot = Offset.Zero) {
            translate(LIVE_MARGIN - origin.x, LIVE_MARGIN - origin.y) {
                val content = local / backdrop.share
                scale(content, content, pivot = Offset.Zero) { drawLayer(backdrop.layer) }
            }
        }
    }
    for (k in 1 until DEPTH) {
        val from = chain.down[k - 1]
        chain.down[k].record(at(k + 1)) { scale(0.5f, 0.5f, pivot = Offset.Zero) { drawLayer(from) } }
    }
    var last = chain.down[DEPTH - 1]
    for (k in 0 until DEPTH - 1) {
        val from = last
        val level = DEPTH - 1 - k
        chain.up[k].record(at(level)) { scale(2f, 2f, pivot = Offset.Zero) { drawLayer(from) } }
        last = chain.up[k]
    }
    val top = last
    translate(-LIVE_MARGIN, -LIVE_MARGIN) { scale(2f, 2f, pivot = Offset.Zero) { drawLayer(top) } }
}

/**
 * A light glass panel over the live screens, for a panel drawn outside the screens' layer (the side menu, the
 * settings): the screens under it blurred on every frame ([drawLiveBlur]), then `#26FFFFFF` and the rim, as on
 * [GlassPanel]. Inside the screens' layer it would draw the layer into itself; panels there use [GlassPanel].
 */
@Composable
fun LiveGlassCard(backdrop: Backdrop, corner: Dp, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(corner)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val local = LocalDrawnAt.current
    val chain = rememberBlurChain()
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInScreens(local) }
            .clip(shape)
            .drawBehind { drawLiveBlur(chain, backdrop, origin, local) },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(Ink.Smoke)
                .border(1.px.coerceAtLeast(androidx.compose.ui.unit.Dp.Hairline), RIM, shape),
        )
        content()
    }
}

/**
 * One panel of a [LiveGlass] group: the group's blur shows through its shape, then `#26FFFFFF` and the rim, as
 * on [GlassPanel]. It must sit inside the box that carries [liveGlass], within [LIVE_MARGIN] of its edges.
 */
@Composable
fun LiveGlassPanel(glass: LiveGlass, corner: Dp, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(corner)
    val key = remember { Any() }
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    DisposableEffect(glass, key) { onDispose { glass.panels.remove(key) } }
    Box(
        modifier
            .onGloballyPositioned {
                // Written once: the same coordinates stay valid while the panel is placed, and a write would draw
                // the group again.
                val held = glass.panels[key]
                if (held == null || held.first !== it || held.second != cornerPx) glass.panels[key] = it to cornerPx
            }
            .clip(shape),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(Ink.Smoke)
                .border(1.px.coerceAtLeast(androidx.compose.ui.unit.Dp.Hairline), RIM, shape),
        )
        content()
    }
}

/** How far past a box's edges the live glass reads the screens, in their pixels: about three times the blur's
 * reach, so the blur at a panel's edge is made of what lies beyond it, not of the layer's empty border. A panel may
 * hang over the group's box by less than this (the pill below the card). */
private const val LIVE_MARGIN = 128f

/** How many times the live blur halves the screens before growing them back: 1/64, about a Gaussian of 35 px. */
private const val DEPTH = 6

/** Laid over the glass of the side menu and the mini player, under their contents, so white text on them
 * stays readable over a bright backdrop. The desktop's glass has none. */
val GLASS_DIM = androidx.compose.ui.graphics.Color(0x40000000)

/** The backdrop is kept at this fraction of the screen. */
private const val SHRINK = 6
/** Box radius at the shrunk size; three passes give about a Gaussian of 3.5 px there, 21 at full size. The live
 * glass ([drawLiveBlur]) blurs more, about 35 px. */
private const val RADIUS = 3

/** The box radius at the shrunk size for a row's menu: three passes of 2 are about a Gaussian of 2.5 px there, 14 at
 * full size, so the screen under a menu stays recognisable. */
const val MENU_RADIUS = 2

/** A box radius at the shrunk size that blurs as much as the live glass ([drawLiveBlur]): three passes of 5 are
 * about a Gaussian of 5.5 px there, 33 at full size. The queue drawer's still of the room is blurred this much. */
const val DEEP_RADIUS = 5

/** The whole blurred backdrop, stretched over [modifier]'s box, for a layer that covers the screen. */
@Composable
fun GlassBackdrop(backdrop: Backdrop, modifier: Modifier) {
    val picture = backdrop.blurred ?: return
    Canvas(modifier) {
        drawRect(backdrop.ground)
        drawImage(
            picture,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(picture.width, picture.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}
