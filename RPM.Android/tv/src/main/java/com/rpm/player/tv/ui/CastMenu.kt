package com.rpm.player.tv.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rpm.player.core.R
import com.rpm.player.core.cast.CastDevice
import com.rpm.player.core.cast.CastDevices
import com.rpm.player.core.cast.CastState
import com.rpm.player.core.cast.Casting

/**
 * The cast key in the player's row: Google's cast mark, a screen with arcs from its corner, drawn
 * from Material Icons (Apache 2.0) as the desktop's deck draws it (TransportDeck.xaml). The outline
 * is "cast". The filled screen in the desktop's cyan is "cast_connected", used while the sound goes
 * to a device.
 */
@Composable
fun CastKey(connected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.Rpm_Cast_PlayOn)
    val path = remember(connected) { PathParser().parsePathString(if (connected) CAST_CONNECTED else CAST).toPath() }
    val ink = if (connected) CAST_ON else Ink.Key
    DeskButton(onClick = onClick, corner = 18.s, modifier = modifier.size(36.s).semantics { contentDescription = label }) {
        Canvas(Modifier.size(16.s)) {
            val k = size.width / 24f
            scale(k, k, pivot = Offset.Zero) { drawPath(path, ink, style = Fill) }
        }
    }
}

/**
 * The cast menu's lines: This TV, then every device found (and the device being cast to, whatever
 * discovery reports). The device in use is ticked, and groups are named as groups. "Looking for
 * speakers" is shown while there are no devices. The last line is Look again, which browses afresh
 * and leaves the menu open. The menu is refreshed with these lines whenever the devices or the state
 * change, so a device found while the menu is open appears in it.
 */
fun castLines(context: Context, casting: Casting, state: CastState, found: List<CastDevice>): List<MenuLine> {
    val lines = mutableListOf<MenuLine>(
        MenuTick(context.getString(R.string.Tv_Cast_ThisTv), ticked = state is CastState.Off) { casting.stop() },
    )
    val devices = CastDevices.menu(found, state.device)
    if (devices.isEmpty()) {
        lines += MenuNote(context.getString(R.string.Rpm_Cast_Searching))
    } else {
        devices.forEach { device ->
            val name = if (device.isGroup) "${device.name} (${context.getString(R.string.Rpm_Cast_Group)})" else device.name
            lines += MenuTick(name, ticked = state.device?.id == device.id) { casting.castTo(device) }
        }
    }
    lines += MenuGap
    lines += MenuAction(R.string.Rpm_Cast_LookAgain) { casting.discovery.again() }
    return lines
}

/** The desktop's cyan for a live cast (TransportDeck.xaml, CastBrush). */
private val CAST_ON = Color(0xFF04C8FF)

private const val CAST = "M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z"
private const val CAST_CONNECTED = "M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm18-7H5v1.63c3.96 1.28 7.09 4.41 8.37 8.37H19V7zM1 10v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11zm20-7H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"
