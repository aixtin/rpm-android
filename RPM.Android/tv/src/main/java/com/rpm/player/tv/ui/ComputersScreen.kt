package com.rpm.player.tv.ui

import com.rpm.player.core.library.SharedCollections
import com.rpm.player.core.cloud.Shelf
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpm.player.core.R
import com.rpm.player.core.home.ComputerView
import com.rpm.player.core.home.Computers
import com.rpm.player.core.home.Ending
import com.rpm.player.core.home.Pairing
import com.rpm.player.core.library.Face
import coil3.ImageLoader

/**
 * Choosing a library, laid out like the profile picker of a streaming app. "Choose library" is shown
 * at the app's largest text size over a row of the accounts, centred on the screen, on the background
 * the person chose for the collection screens. Each account shows its picture, its name under it, and
 * under that how it is shared. The person's own collection ("My collection") comes first. Then comes
 * each collection that another account shares with this one ("Cloud tracks only", with the supporter
 * mark when the owner is a supporter). A tick marks the library that is [open]. OK on an account opens
 * it ([onOpen]). Under the row are the computers on the network that share rpm and the computers this
 * TV has paired with. OK on a computer that is on the network and not paired starts pairing: the
 * computer shows a six-digit code, which is typed here with the TV's keyboard. The music of a paired
 * computer joins the person's own collection while the computer is on the network. Back leaves the
 * screen.
 */
@Composable
fun ComputersScreen(
    computers: Computers,
    shared: SharedCollections,
    /** How the person is shown; null until read. */
    own: Face?,
    images: ImageLoader,
    open: Shelf,
    backdrop: Backdrop,
    onOpen: (Shelf) -> Unit,
) {
    val list by computers.list.collectAsStateWithLifecycle()
    val pairing by computers.pairing.collectAsStateWithLifecycle()
    val sharedList by shared.list.collectAsStateWithLifecycle()
    val collections = remember(sharedList, open) { shared.rows(open) }
    val first = remember { FocusRequester() }
    val spot = remember { FocusSpot() }
    LaunchedEffect(Unit) {
        shared.refresh()
        backdrop.capture()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SpotScope(spot, Modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.Tv_ChooseLibrary), style = ui(44.ts, FontWeight.Bold), color = Ink.Words, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.s))
                Row(horizontalArrangement = Arrangement.spacedBy(24.s), verticalAlignment = Alignment.Top) {
                    LibraryTile(
                        picture = { FacePicture(own?.picture, own?.name, Fa.HOUSE, images, PICKER_FACE, 64.ts, Modifier) },
                        name = own?.name ?: stringResource(R.string.Share_MyLibrary),
                        line = stringResource(R.string.Share_MyLibrary),
                        supporter = false,
                        isOpen = open == Shelf.Own,
                        onClick = { if (open != Shelf.Own) onOpen(Shelf.Own) },
                        modifier = if (open == Shelf.Own) Modifier.focusRequester(first) else Modifier,
                    )
                    val unnamed = stringResource(R.string.Viz_MenuShared)
                    for (row in collections) {
                        val name = row.name ?: unnamed
                        LibraryTile(
                            picture = { FacePicture(row.face?.picture, name, Fa.LAPTOP, images, PICKER_FACE, 64.ts, Modifier) },
                            name = name,
                            line = stringResource(R.string.Rpm_Share_CloudOnly),
                            supporter = row.face?.supporter == true,
                            isOpen = open == row.shelf,
                            onClick = { if (open != row.shelf) onOpen(row.shelf) },
                            modifier = if (open == row.shelf) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.height(56.s))
                Text(stringResource(R.string.Tv_Menu_Computers), style = ui(20.ts, FontWeight.SemiBold), color = Ink.Artist)
                Spacer(Modifier.height(10.s))
                if (list.isEmpty()) {
                    Text(stringResource(R.string.Tv_Computers_None), style = ui(14.ts), color = Ink.Artist, textAlign = TextAlign.Center)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.s)) {
                        for (computer in list) {
                            ComputerRow(
                                computer = computer,
                                onClick = { if (!computer.paired) computer.here?.let(computers::pair) },
                                modifier = Modifier.width(320.s),
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    pairing?.let { PairingPanel(it, backdrop, onCode = computers::submit, onClose = computers::cancel) }
}

/** How large an account's picture is on the picker. */
private val PICKER_FACE = 160.s

/** One library on the picker: the account's [picture] ([PICKER_FACE], round), its name under it
 * (22·s SemiBold), then how it is shared ([line], 15·s, `#B3FFFFFF`) with a tick before it on the
 * open library, and the supporter mark under that when the owner is a supporter. It is `220·s` wide,
 * so a long name ends in an ellipsis and does not push the row apart. */
@Composable
private fun LibraryTile(picture: @Composable () -> Unit, name: String, line: String, supporter: Boolean, isOpen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    DeskButton(onClick = onClick, corner = 12.s, modifier = modifier.width(220.s).padding(vertical = 14.s)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            picture()
            Spacer(Modifier.height(14.s))
            Text(name, style = ui(22.ts, FontWeight.SemiBold), color = Ink.Words, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.s))
            Spacer(Modifier.height(4.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOpen) {
                    Glyph(Fa.CHECK, 13.ts, Ink.Words)
                    Spacer(Modifier.width(6.s))
                }
                Text(line, style = ui(15.ts), color = Ink.Artist, maxLines = 1)
            }
            if (supporter) SupporterMark(12f, Modifier.padding(top = 8.s))
        }
    }
}

/** One computer (MenuRow): a 44·s round mark with the laptop, the name (20·s SemiBold), where it is
 * (14·s, `#B3FFFFFF`), and a tick when it is paired and on the network. Padding is 14·s by 10·s,
 * corner 7·s. */
@Composable
private fun ComputerRow(computer: ComputerView, onClick: () -> Unit, modifier: Modifier) {
    DeskButton(onClick = onClick, corner = 7.s, contentAlignment = Alignment.CenterStart, modifier = modifier.fillMaxWidth().padding(horizontal = 14.s, vertical = 10.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.s).background(Ink.Smoke, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(Fa.LAPTOP, 18.ts, Ink.Card)
            }
            Spacer(Modifier.width(14.s))
            Column(Modifier.weight(1f)) {
                Text(computer.name, style = ui(20.ts, FontWeight.SemiBold), color = Ink.Words, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        when {
                            computer.paired && computer.here != null -> R.string.Tv_Computer_Connected
                            computer.paired -> R.string.Tv_Computer_Away
                            else -> R.string.Tv_Computer_Connect
                        },
                    ),
                    style = ui(14.ts),
                    color = Ink.Artist,
                    maxLines = 1,
                )
            }
            if (computer.paired && computer.here != null) {
                Spacer(Modifier.width(14.s))
                Glyph(Fa.CHECK, 16.ts, Ink.Words)
            }
        }
    }
}

/**
 * The pairing card, drawn like the desktop's centred panels (its settings panel): `620·s` of the
 * light glass over a `#66000000` ground, the message in 28·s SemiBold, then the code field or the OK
 * key. Back cancels a pairing that is in progress.
 */
@Composable
private fun PairingPanel(pairing: Pairing, backdrop: Backdrop, onCode: (String) -> Unit, onClose: () -> Unit) {
    var code by remember(pairing.name) { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val ok = remember { FocusRequester() }
    val spot = remember { FocusSpot() }
    BackHandler(enabled = LocalOnTop.current, onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
        GlassPanel(backdrop, 7.s, Modifier.width(620.s)) {
            SpotScope(spot, Modifier) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.s, top = 22.s, end = 20.s, bottom = 20.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.s),
                ) {
                    val message = when (pairing) {
                        is Pairing.Asking -> stringResource(R.string.Tv_Pair_Asking, pairing.name)
                        is Pairing.ShowingCode -> stringResource(R.string.Share_EnterCode, pairing.name)
                        is Pairing.Ended -> when (pairing.how) {
                            Ending.Accepted -> stringResource(R.string.Tv_Pair_Accepted, pairing.name)
                            Ending.Declined -> stringResource(R.string.Tv_Pair_Declined, pairing.name)
                            Ending.Expired -> stringResource(R.string.Tv_Pair_Expired)
                            Ending.TooManyTries -> stringResource(R.string.Share_TooManyTries)
                            Ending.Full -> stringResource(R.string.Share_Full, pairing.name)
                            Ending.Lost -> stringResource(R.string.Share_Lost, pairing.name)
                            Ending.Failed -> stringResource(R.string.Tv_Pair_Failed, pairing.name)
                        }
                    }
                    Text(message, style = ui(28.ts, FontWeight.SemiBold), color = Ink.Words, textAlign = TextAlign.Center)
                    if (pairing is Pairing.ShowingCode) {
                        val shape = RoundedCornerShape(12.s)
                        BasicTextField(
                            value = code,
                            onValueChange = { typed ->
                                val digits = typed.filter(Char::isDigit).take(6)
                                code = digits
                                // Six digits is the whole code. It is sent at once, and the field is cleared for another try.
                                if (digits.length == 6) {
                                    onCode(digits)
                                    code = ""
                                }
                            },
                            singleLine = true,
                            textStyle = ui(34.ts, FontWeight.SemiBold).copy(color = Ink.Words, textAlign = TextAlign.Center, letterSpacing = 0.3.em),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .focusRequester(field)
                                .deskFocus(12.s)
                                .width(300.s)
                                .background(Ink.Smoke, shape)
                                .border(1.px, Color(0x59FFFFFF), shape)
                                .padding(vertical = 12.s),
                        )
                        if (pairing.wrong) {
                            Text(stringResource(R.string.Share_CodeWrongTries, pairing.triesLeft.toString()), style = ui(16.ts), color = Color(0xFFFF8A80))
                        }
                    }
                    if (pairing is Pairing.Ended) {
                        val shape = RoundedCornerShape(7.s)
                        DeskButton(
                            onClick = onClose,
                            corner = 7.s,
                            modifier = Modifier.focusRequester(ok).width(200.s).height(60.s).background(Color(0x14FFFFFF), shape).border(1.px, Color(0xB3FFFFFF), shape),
                        ) {
                            Text(stringResource(R.string.Common_OK), style = ui(22.ts, FontWeight.SemiBold), color = Ink.Words)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(pairing::class) {
        runCatching {
            when (pairing) {
                is Pairing.ShowingCode -> field.requestFocus()
                is Pairing.Ended -> ok.requestFocus()
                is Pairing.Asking -> {}
            }
        }
    }
}
