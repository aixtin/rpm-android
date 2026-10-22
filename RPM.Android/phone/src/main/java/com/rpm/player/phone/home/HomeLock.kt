package com.rpm.player.phone.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpm.player.core.R
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.chromeGlass
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.pt
import com.rpm.player.phone.design.system

/**
 * What home shows a collection too small for it: what home is, how many records it has of the [want] it needs, how
 * many are still to go, and a key to the library to add some. It is the iPhone's `HomeLock`. The bar shows the
 * amount at a glance; the sentences under it give the numbers.
 */
@Composable
fun HomeLock(have: Int, want: Int, backdrop: Backdrop, onAddMusic: () -> Unit, modifier: Modifier = Modifier) {
    val toGo = maxOf(0, want - have)
    val fraction = if (want <= 0) 1f else (have.toFloat() / want).coerceIn(0f, 1f)
    Column(
        modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(stringResource(R.string.HomeLock_Kicker), style = display(12f).copy(color = Ink.white(0x99), letterSpacing = pt(2f)))
        BasicText(stringResource(R.string.Home_Heading), style = displayBold(26f).copy(color = Color.White))
        BasicText(
            stringResource(R.string.HomeLock_Body),
            Modifier.widthIn(max = 320.dp),
            style = system(14f).copy(color = Ink.secondary, textAlign = TextAlign.Center),
        )
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(220.dp).height(4.dp).clip(RoundedCornerShape(percent = 50)).background(Ink.glassWash)) {
                Box(Modifier.width(maxOf(2.dp, 220.dp * fraction)).fillMaxHeight().clip(RoundedCornerShape(percent = 50)).background(Ink.white(0xB8)))
            }
            BasicText(stringResource(R.string.HomeLock_Progress, have, want), style = system(12f).copy(color = Ink.secondary))
            BasicText(
                if (toGo == 1) stringResource(R.string.HomeLock_ToGoOne) else stringResource(R.string.HomeLock_ToGo, toGo),
                style = system(12f).copy(color = Ink.white(0x99)),
            )
        }
        val capsule = RoundedCornerShape(percent = 50)
        Box(
            Modifier
                .padding(top = 4.dp)
                .height(44.dp)
                .chromeGlass(backdrop, capsule)
                .clip(capsule)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onAddMusic)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(stringResource(R.string.HomeLock_AddMusic), style = system(15f, FontWeight.Medium).copy(color = Color.White))
        }
    }
}
