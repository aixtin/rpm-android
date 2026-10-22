package com.rpm.player.phone.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** What a key of a dialog does to the person's things, which decides how it is drawn, as SwiftUI's button roles. */
enum class KeyRole {
    /** An ordinary answer. */
    Plain,

    /** Leaves everything as it was. Drawn in the heavier weight, as iOS draws a cancel key. */
    Cancel,

    /** Throws something away. Drawn in [Ink.red], as iOS draws a destructive key. */
    Destructive,
}

/** One answer to a dialog. */
class DialogKey(val label: String, val role: KeyRole = KeyRole.Plain, val onPress: () -> Unit)

/**
 * A question that must be answered before anything else happens: the iPhone's system `alert`, in the app's glass. A
 * card 270 dp wide in the middle of a dimmed screen, the title at 17 semi-bold over the message at 13, and the answers
 * under a hairline, side by side when there are two, one under the other when there are more.
 *
 * As on iOS, a tap outside the card does nothing: the question is answered with a key. Back counts as the cancel key,
 * if there is one.
 */
@Composable
fun Alert(backdrop: Backdrop, title: String, message: String?, keys: List<DialogKey>) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
    keys.firstOrNull { it.role == KeyRole.Cancel }?.let { cancel -> BackHandler(onBack = cancel.onPress) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
            .background(ALERT_DIM)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            backdrop,
            Modifier
                .width(270.dp)
                .graphicsLayer {
                    val grow = 1.15f - 0.15f * shown.value
                    scaleX = grow
                    scaleY = grow
                },
            radius = 14.dp,
        ) {
            Column(Modifier.background(DIALOG_FILL)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 19.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(title, style = system(17f, FontWeight.SemiBold).copy(color = Color.White, textAlign = TextAlign.Center))
                    message?.let { BasicText(it, style = system(13f).copy(color = Ink.white(0xCC), textAlign = TextAlign.Center)) }
                }
                Hairline()
                if (keys.size == 2) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        AlertKey(keys[0])
                        Box(Modifier.width(HAIRLINE).fillMaxHeight().background(SEPARATOR))
                        AlertKey(keys[1])
                    }
                } else {
                    keys.forEachIndexed { at, key ->
                        if (at > 0) Hairline()
                        Row { AlertKey(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.AlertKey(key: DialogKey) {
    DialogKeyFace(key, Modifier.weight(1f).height(44.dp), size = 17f)
}

/**
 * The question asked at the foot of the screen: the iPhone's `confirmationDialog`, an action sheet, in the app's
 * glass. A panel with the title and the message in the quiet small type, then each answer at 20 on a 57 dp row, and
 * apart from it, a second panel with Cancel. It slides up from the foot of the screen.
 *
 * A tap outside the panels, or Back, is Cancel, as on iPhone.
 */
@Composable
fun ActionSheet(backdrop: Backdrop, title: String, message: String?, keys: List<DialogKey>, cancel: DialogKey) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
    // Every answer slides the sheet away first; the answer itself is carried out when it is gone.
    fun answer(key: DialogKey) {
        if (leaving) return
        leaving = true
        scope.launch {
            shown.animateTo(0f, tween(180))
            key.onPress()
        }
    }
    BackHandler { answer(cancel) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
            .background(SHEET_DIM)
            .clickable(remember { MutableInteractionSource() }, indication = null) { answer(cancel) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .graphicsLayer { translationY = (1f - shown.value) * size.height },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassCard(backdrop, radius = 14.dp) {
                Column(Modifier.background(DIALOG_FILL).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(title, style = system(13f, FontWeight.SemiBold).copy(color = Ink.white(0x99), textAlign = TextAlign.Center))
                        message?.let { BasicText(it, style = system(13f).copy(color = Ink.white(0x99), textAlign = TextAlign.Center)) }
                    }
                    for (key in keys) {
                        Hairline()
                        DialogKeyFace(key, Modifier.fillMaxWidth().height(57.dp), size = 20f) { answer(key) }
                    }
                }
            }
            GlassCard(backdrop, radius = 14.dp) {
                DialogKeyFace(cancel, Modifier.fillMaxWidth().height(57.dp).background(DIALOG_FILL), size = 20f) { answer(cancel) }
            }
        }
    }
}

/** A key's face: its label centred, lit while pressed, in the weight and colour its role gives it. */
@Composable
private fun DialogKeyFace(key: DialogKey, modifier: Modifier, size: Float, onPress: () -> Unit = key.onPress) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Box(
        modifier
            .background(if (pressed) Ink.pressedVeil else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            key.label,
            Modifier.padding(horizontal = 12.dp),
            maxLines = 1,
            style = system(size, if (key.role == KeyRole.Cancel) FontWeight.SemiBold else FontWeight.Normal)
                .copy(color = if (key.role == KeyRole.Destructive) Ink.red else Color.White, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(HAIRLINE).background(SEPARATOR))

private val HAIRLINE = 0.5.dp
private val SEPARATOR = Color.White.copy(alpha = 0x26 / 255f)

/** A darker fill over the card's frost, so the words of a question read over any cover behind it. */
private val DIALOG_FILL = Color(0x99151A24)

/** iOS dims the screen a little behind an alert, and a little more behind an action sheet. */
private val ALERT_DIM = Color.Black.copy(alpha = 0.3f)
private val SHEET_DIM = Color.Black.copy(alpha = 0.4f)
