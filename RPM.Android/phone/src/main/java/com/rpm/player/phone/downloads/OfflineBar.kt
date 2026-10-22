package com.rpm.player.phone.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpm.player.core.R
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.display

/**
 * The one line the app shows about the network, as the iPhone's `OfflineBar` (RPMUI/OfflineBar.swift).
 *
 * **It floats over the content and does not push it down.** The wall stays where it is, drawn from the copy kept on
 * the phone; the bar explains why the covers have stopped loading. **It follows the settled reading** (`NetworkReach`
 * waits a moment before it says offline), so a change from Wi-Fi to cellular does not flash it. It never takes a tap
 * from the row under it: it has no click handler, so touches pass through.
 *
 * The iPhone draws it on its glass card. Here it is the card's flat fill and edge without the blur, which Android
 * would have to draw from a recording of the whole screen under it, on every screen including sign-in.
 *
 * @param detail a second line for screens where going offline changes what can be done: after sign-in, only
 * downloaded records play. Null over the sign-in form.
 */
@Composable
fun OfflineBar(showing: Boolean, detail: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = showing,
            enter = slideInVertically(tween(250, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(250, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(250)),
        ) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .clip(shape)
                    .background(Ink.frostless, shape)
                    .background(Ink.glassWash, shape)
                    .border(1.dp, Ink.cardBorder, shape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NoWifi(Ink.white(0xCC))
                    BasicText(stringResource(R.string.Rpm_Net_Offline), style = display(13f).copy(color = Ink.white(0xCC)))
                }
                if (detail != null) {
                    BasicText(detail, style = display(11f).copy(color = Ink.white(0x99), textAlign = TextAlign.Center))
                }
            }
        }
    }
}

/**
 * The iPhone's `wifi.slash` symbol, 12 points: the three bands of a Wi-Fi mark and its dot, struck through. Drawn,
 * because the icon font the app ships does not have one in its free cut.
 */
@Composable
private fun NoWifi(ink: Color) {
    Canvas(Modifier.size(14.dp, 12.dp)) {
        val stroke = 1.6.dp.toPx()
        val centre = Offset(size.width / 2, size.height * 0.92f)
        for (band in 1..3) {
            val r = size.height * (0.28f * band)
            drawArc(
                ink,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(centre.x - r, centre.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(ink, radius = stroke * 0.9f, center = Offset(centre.x, centre.y - stroke * 0.2f))
        drawLine(ink, Offset(size.width * 0.12f, 0f), Offset(size.width * 0.88f, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
