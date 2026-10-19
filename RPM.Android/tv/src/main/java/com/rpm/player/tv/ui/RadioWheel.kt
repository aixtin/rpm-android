package com.rpm.player.tv.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.rpm.player.core.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/**
 * The radio wheel's state and keys. The wheel never takes the focus: the screen under it sends every key
 * here first ([onKey]), so nothing behind it moves, and the focus is still on the same item when the
 * wheel closes, as on the desktop. There is one state per opening. The screen makes it when a held Right opens
 * the wheel, so every opening starts faded out and on [start].
 */
@Stable
class RadioWheelState internal constructor(start: Int, private val count: Int, private val scope: CoroutineScope) {
    internal val turn = WheelTurn(start.coerceIn(0, (count - 1).coerceAtLeast(0)).toFloat())
    internal val fade = Animatable(0f)
    internal var closing by mutableStateOf(false)
        private set
    internal var onPick: (Int) -> Unit = {}
    internal var onClose: () -> Unit = {}

    /** Fades the wheel out over 110 ms, then runs [then]. A second close while fading does nothing. */
    internal fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            fade.animateTo(0f, tween(WHEEL_OUT_MS, easing = QuadInOut))
            then()
        }
    }

    /**
     * Up and Down turn the wheel (repeats included, so a held key keeps turning). OK picks. Left closes. A repeat
     * of Left, and Right itself, do nothing, because the held Right that opened the wheel is still
     * repeating. Back is not handled here: the wheel's BackHandler closes it. Every other key is consumed.
     */
    fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (event.action != KeyEvent.ACTION_DOWN || closing) return true
        val first = event.repeatCount == 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> turn.by(-1, count)
            KeyEvent.KEYCODE_DPAD_DOWN -> turn.by(1, count)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (first) {
                val picked = turn.target.coerceIn(0, count - 1)
                close { onPick(picked) }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (first) close { onClose() }
        }
        return true
    }
}

/**
 * The radio wheel (PegasusChrome.RadioWheel.cs, RadioWheel.cs): the screen blurred under `#33000000`,
 * and down the right a column of words turning past a cyan needle. The word on the needle is the one
 * OK picks; the others shrink, fade, blur and fall back to the right the further they are from it.
 *
 * Drawn as the desktop draws it with Skia, at the collection's scale. The needle is 110·s long and starts
 * 24·s from the right edge. It ends in a chevron and has a glow 9·s high behind it. Words are 58·s apart and
 * `28·s·(1 - 0.09·min(far, 4))` high, right-aligned 22·s before the needle's tip, with capitals centred on the
 * needle. Their alpha is `(1 - 0.2·far)·clamp(4.5 - far)`, their blur is `2.6·s·far` and they are moved to
 * the right by `15·s·far^1.6`.
 *
 * A turn runs 220 ms, easing out, from wherever the wheel is at that moment, so a fast run of turns adds
 * up and does not restart. The wheel does not wrap: a turn past either end leans every word 0.18 of a row at
 * once, and the lean decays by `0.002^dt`. It opens on the state's start entry with no turn, fading in
 * over 140 ms. The keys are handled by [RadioWheelState.onKey].
 */
@Composable
fun RadioWheel(state: RadioWheelState, entries: List<String>, backdrop: Backdrop, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val pick by rememberUpdatedState(onPick)
    val close by rememberUpdatedState(onClose)
    state.onPick = { pick(it) }
    state.onClose = { close() }
    val context = LocalContext.current
    val face = remember { ResourcesCompat.getFont(context, R.font.selawik_regular) }
    val turn = state.turn

    BackHandler { state.close { close() } }
    LaunchedEffect(state) {
        launch { backdrop.capture() }
        state.fade.animateTo(1f, tween(WHEEL_IN_MS, easing = QuadInOut))
    }
    LaunchedEffect(turn) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) turn.tick((now - last) / 1e9f)
                last = now
            }
        }
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = state.fade.value }) {
        GlassBackdrop(backdrop, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Color(0x33000000)))
        Canvas(Modifier.align(Alignment.CenterEnd).width(520.s).fillMaxHeight()) {
            val s = 1.s.toPx()
            val right = size.width - 24 * s
            val tip = right - 110 * s
            val mid = size.height / 2
            // The needle: its glow, its line, and the chevron at its tip.
            drawRoundRect(NEEDLE.copy(alpha = 0x33 / 255f), Offset(tip, mid - 4.5f * s), Size(right - tip, 9 * s), CornerRadius(4.5f * s))
            drawRect(NEEDLE, Offset(tip, mid - s), Size(right - tip, 2 * s))
            drawLine(NEEDLE, Offset(tip + 7 * s, mid - 6 * s), Offset(tip, mid), 2 * s, StrokeCap.Round)
            drawLine(NEEDLE, Offset(tip, mid), Offset(tip + 7 * s, mid + 6 * s), 2 * s, StrokeCap.Round)

            val textRight = tip - 22 * s
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                color = Color.White.toArgb()
                textAlign = Paint.Align.RIGHT
            }
            val bounds = android.graphics.Rect()
            val at = turn.drawnAt
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                entries.forEachIndexed { i, word ->
                    val away = i - at
                    val far = abs(away)
                    if (far > 4.5f) return@forEachIndexed
                    val alpha = (1 - 0.2f * far).coerceIn(0f, 1f) * (4.5f - far).coerceIn(0f, 1f)
                    if (alpha <= 0f) return@forEachIndexed
                    paint.textSize = 28 * s * (1 - 0.09f * minOf(far, 4f))
                    paint.alpha = (alpha * 255).toInt()
                    val blur = 2.6f * s * far
                    paint.maskFilter = if (blur > 0.3f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
                    // The cap height of this size, so the capitals sit centred on the needle.
                    paint.getTextBounds("H", 0, 1, bounds)
                    val cap = bounds.height().toFloat().takeIf { it > 0f } ?: (-paint.ascent() * 0.7f)
                    native.drawText(word, textRight + 15 * s * far.pow(1.6f), mid + away * ROW * s + cap / 2, paint)
                }
            }
        }
    }
}

/**
 * The wheel's position (RadioWheel.cs Turn and Tick): a 220 ms cubic ease-out from [from] to the whole
 * entry [target], retargeted from the current position on every turn, plus the lean past an end. State
 * reads, so the canvas redraws on each tick.
 */
internal class WheelTurn(start: Float) {
    private var from = start
    var target = start.toInt()
        private set
    private var t = 1f
    private var at by mutableFloatStateOf(start)
    private var lean by mutableFloatStateOf(0f)

    /** Where the words are drawn from: the turn's position plus the lean. */
    val drawnAt: Float get() = at + lean

    fun by(step: Int, count: Int) {
        val next = target + step
        if (next !in 0 until count) {
            // Past an end, every word leans at once and then drifts back.
            lean = LEAN * step
            return
        }
        from = at
        target = next
        t = 0f
    }

    fun tick(dt: Float) {
        if (t < 1f) {
            t = minOf(1f, t + dt * 1000f / TURN_MS)
            val eased = 1 - (1 - t).pow(3)
            at = from + (target - from) * eased
        }
        if (lean != 0f) {
            val left = lean * 0.002f.pow(dt)
            lean = if (abs(left) < 0.002f) 0f else left
        }
    }
}

private val NEEDLE = Color(0xFF04C8FF)
private const val ROW = 58f
private const val TURN_MS = 220
private const val LEAN = 0.18f
private const val WHEEL_IN_MS = 140
private const val WHEEL_OUT_MS = 110
