package com.rpm.player.phone.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rpm.player.core.R
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.system

/**
 * The question asked before something is removed: SwiftUI's `confirmationDialog` with its title shown, which the
 * iPhone draws as an action sheet. The question in small grey type over one red action, and Cancel apart under it,
 * both rising from the bottom edge. A tap outside the sheet, or Back, is Cancel.
 *
 * It is a dialog window, so it covers the whole app wherever it is asked from (a key inside a list, a page) without
 * the page having to make room for it.
 */
@Composable
fun ConfirmSheet(question: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val shown = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { shown.targetState = true }
    // The sheet slides away first; the window closes when it has gone.
    var then by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(shown.isIdle, shown.currentState) {
        if (shown.isIdle && !shown.currentState) then?.let { after ->
            then = null
            after()
        }
    }
    val close = { after: () -> Unit ->
        then = after
        shown.targetState = false
    }

    Dialog(
        onDismissRequest = { close(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null) { close(onDismiss) },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = shown,
                enter = slideInVertically(tween(300)) { it },
                exit = slideOutVertically(tween(220)) { it },
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                        // A tap on the sheet itself is not a tap outside it.
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.fillMaxWidth().clip(SHAPE).background(FACE)) {
                        BasicText(
                            question,
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            style = system(13f, FontWeight.SemiBold).copy(color = Ink.white(0x8C), textAlign = TextAlign.Center),
                        )
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink.white(0x26)))
                        Choice(action, system(20f).copy(color = Ink.red)) { close(onConfirm) }
                    }
                    Box(Modifier.fillMaxWidth().clip(SHAPE).background(FACE_CANCEL)) {
                        Choice(stringResource(R.string.Common_Cancel), system(20f, FontWeight.SemiBold).copy(color = Color.White)) { close(onDismiss) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(57.dp)
            .background(if (pressed) Ink.white(0x1A) else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = style)
    }
}

private val SHAPE = RoundedCornerShape(14.dp)

/** The action sheet's dark faces, as iOS draws them in the dark appearance. The cancel key sits a step brighter. */
private val FACE = Color(0xF21C1C1E)
private val FACE_CANCEL = Color(0xFF2C2C2E)
