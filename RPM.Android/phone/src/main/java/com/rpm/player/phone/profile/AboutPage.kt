package com.rpm.player.phone.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rpm.player.core.R
import com.rpm.player.phone.design.GlassButton
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.header
import com.rpm.player.phone.design.kicker
import java.util.Calendar

/** The rpm site's pages, as the iPhone's `RpmSite`. */
internal object RpmSite {
    const val HOME = "https://rpmrpmrpm.com"
    const val SUPPORT = "https://rpmrpmrpm.com/support"
    const val PRIVACY = "https://rpmrpmrpm.com/privacy"
    const val TERMS = "https://rpmrpmrpm.com/terms"

    /** Where a person goes to become a supporter. Opened in the browser: support is not sold in the app. */
    const val BECOME_SUPPORTER = "https://rpmrpmrpm.com/become-supporter"
}

/**
 * What rpm is, raised over everything from the About card, as the iPhone's `AboutPage` sheet: the name, the tagline,
 * the version and the links to the site, the support page, the privacy policy and the terms.
 *
 * The iPhone's list of what other people made is generated from `scripts/credits.json`, which has no Android head
 * yet. The phone ships libraries the iPhone does not (Media3, Compose, OkHttp, Coil, FFmpeg), so the iPhone's list
 * would be a false statement here, and the list waits for that head.
 */
@Composable
fun AboutPage(version: String, onClose: () -> Unit) {
    val links = LocalUriHandler.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Ink.ground)) {
            // One key: Close.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.white(0x0A))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(onClick = onClose, stretches = false, height = 34.dp, radius = 9.dp, inset = 16.dp, size = 13f) { style ->
                    BasicText(stringResource(R.string.About_Close), style = style)
                }
                Spacer(Modifier.weight(1f))
                BasicText(stringResource(R.string.About_Title), style = kicker())
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Ink.white(0x12))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText("rpm", style = header(34f).copy(color = Ink.white(0xCC)))
                    BasicText(stringResource(R.string.About_Tagline), style = display(13f).copy(color = Ink.white(0xB3)))
                    val year = Calendar.getInstance().get(Calendar.YEAR)
                    BasicText(
                        "${stringResource(R.string.About_Version, version)}  ·  © $year rpm. ${stringResource(R.string.About_Rights)}",
                        Modifier.padding(top = 4.dp),
                        style = display(11f).copy(color = Ink.white(0x66)),
                    )
                    // One row where the four labels fit, two where they do not, as on iPhone.
                    FlowRow(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for ((label, url) in listOf(
                            R.string.About_Website to RpmSite.HOME,
                            R.string.About_Support to RpmSite.SUPPORT,
                            R.string.About_Privacy to RpmSite.PRIVACY,
                            R.string.About_Terms to RpmSite.TERMS,
                        )) {
                            GlassButton(onClick = { links.openUri(url) }, stretches = false, height = 30.dp, radius = 9.dp, inset = 12.dp, size = 12f) { style ->
                                BasicText(stringResource(label), maxLines = 1, style = style)
                            }
                        }
                    }
                }
            }
        }
    }
}
