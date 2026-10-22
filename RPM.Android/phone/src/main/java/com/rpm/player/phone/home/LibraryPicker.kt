package com.rpm.player.phone.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.rpm.player.core.R
import com.rpm.player.core.cloud.Shelf
import com.rpm.player.core.library.Face
import com.rpm.player.core.library.SharedCollection
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.GlassCard
import com.rpm.player.phone.design.GlassKey
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.kicker
import com.rpm.player.phone.design.system

/** The switch key's glyph: two people (`\u{f5db}` on iPhone). */
private const val PEOPLE = ""

/** A row's height, and the most the rows take before they scroll (`ShortScroll(each: 54, upTo: 300)`). */
private val ROW = 54.dp
private val ROWS_AT_MOST = 300.dp

/** The tile that shows whose collection a row is, and the pixels its photograph is asked at (three times the tile). */
private val TILE = 38.dp
private const val TILE_PX = 120

/**
 * One collection the app can show: the person's own, or one somebody shares with them. [isOpen] marks the one on
 * screen. It is the iPhone's `ShelfRow`.
 */
data class ShelfRow(val shelf: Shelf, val name: String, val face: Face?, val isOpen: Boolean) {
    val isOwn: Boolean get() = shelf == Shelf.Own

    /** The first letter of the owner's name, on another's row only: the person's own row is named "My library". */
    val initial: String
        get() = if (isOwn) "" else name.firstOrNull()?.takeIf { it.isLetterOrDigit() }?.uppercase().orEmpty()

    /** The supporter mark beside a name, never on the person's own row. */
    val wearsMark: Boolean get() = !isOwn && face?.supporter == true

    companion object {
        /**
         * The rows, in the iPhone's order (`SharedShelves.rows`): the person's own first, then each shared collection,
         * oldest share first. [shared] already keeps the open collection listed when its share is gone, so there is
         * always a way back.
         */
        fun of(shared: List<SharedCollection>, viewing: Shelf, own: Face?, ownName: String, unknownName: String): List<ShelfRow> =
            listOf(ShelfRow(Shelf.Own, ownName, own, isOpen = viewing == Shelf.Own)) +
                shared.map { ShelfRow(it.shelf, it.name ?: unknownName, it.face, isOpen = it.shelf == viewing) }
    }
}

/**
 * The key on home's title line that chooses whose collection the app shows (`HomePage.switcher` on iPhone). It is on
 * home, not in the settings: a borrowed collection with no obvious way out can make the person think their own records
 * are gone. It is drawn only when there is more than one collection to choose from.
 */
@Composable
fun SwitchKey(backdrop: Backdrop, onSwitch: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.Rpm_Share_Pick)
    GlassKey(
        backdrop,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSwitch()
        },
        modifier = modifier.semantics { contentDescription = label },
    ) {
        BasicText(PEOPLE, style = icon(16f).copy(color = Ink.white(0xB8)))
    }
}

/**
 * Chooses which collection the app shows: the person's own, or one another person shares with them. It is the iPhone's
 * `LibraryPicker`: a glass card over the whole app, the rows with whose they are, the open one marked, and a line
 * that says where sharing is set up, because a phone cannot set it up itself. A tap outside closes it.
 */
@Composable
fun LibraryPicker(rows: List<ShelfRow>, images: ImageLoader, backdrop: Backdrop, onOpen: (Shelf) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    val shown by animateFloatAsState(if (opened) 1f else 0f, tween(180), label = "picker")
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .alpha(shown)
            .background(Color.Black.copy(alpha = 0x99 / 255f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            backdrop,
            Modifier
                .width(minOf(320.dp, maxWidth - 32.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            radius = 18.dp,
        ) {
            Column {
                Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        BasicText(stringResource(R.string.Rpm_Share_Title), style = displayBold(19f).copy(color = Color.White))
                        BasicText(
                            if (rows.size == 1) stringResource(R.string.Rpm_Share_NobodyShares) else stringResource(R.string.Rpm_Share_SharedWithYouCount, rows.size - 1),
                            style = kicker(),
                        )
                    }
                    val close = stringResource(R.string.RecogResult_Close)
                    GlassKey(backdrop, onClose, Modifier.semantics { contentDescription = close }) {
                        BasicText(Glyph.close, style = icon(15f).copy(color = Ink.white(0xB8)))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.white(0x1A)))
                Column(Modifier.heightIn(max = ROWS_AT_MOST).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    for (row in rows) Line(row, images) { onOpen(row.shelf) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.white(0x1A)))
                BasicText(stringResource(R.string.Rpm_Share_Hint), Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), style = kicker())
            }
        }
    }
}

@Composable
private fun Line(row: ShelfRow, images: ImageLoader, onClick: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW)
            .background(if (pressed) Ink.white(0x24) else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PeerTile(row, images)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(row.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(15f).copy(color = Color.White))
                if (row.wearsMark) SupporterMark()
            }
            // A phone reaches only what the other person uploaded, not files that are only on their disk.
            if (!row.isOwn) {
                BasicText(stringResource(R.string.Rpm_Share_CloudOnly), maxLines = 1, style = display(11f).copy(color = Ink.white(0x66)))
            }
        }
        if (row.isOpen) {
            BasicText(stringResource(R.string.Rpm_Share_Opened), style = display(11f).copy(color = Ink.white(0x88)))
        } else {
            BasicText(Glyph.into, style = icon(12f).copy(color = Ink.white(0x77)))
        }
    }
}

/**
 * Whose collection a row is: their photograph where the account has one, else the first letter of their name, else a
 * glyph (a house on the person's own row, a person on another's).
 */
@Composable
private fun PeerTile(row: ShelfRow, images: ImageLoader) {
    val well = RoundedCornerShape(8.dp)
    Box(Modifier.size(TILE).clip(well).background(Ink.white(0x18)), contentAlignment = Alignment.Center) {
        when {
            row.initial.isEmpty() -> BasicText(if (row.isOwn) Glyph.home else Glyph.profile, style = icon(14f).copy(color = Ink.white(0xD9)))
            else -> BasicText(row.initial, style = display(15f).copy(color = Ink.white(0xF2)))
        }
        row.face?.picture?.let { picture ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(picture).size(Size(TILE_PX, TILE_PX)).build(),
                contentDescription = null,
                imageLoader = images,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The supporter's gold pill beside a name: a heart and the word (`SupporterMark` and `SupporterMetal` on iPhone). */
@Composable
private fun SupporterMark() {
    val pill = RoundedCornerShape(9.dp)
    val face = Brush.linearGradient(
        0f to Color(0x3A9A6A18),
        0.30f to Color(0x46EFAF12),
        0.42f to Color(0x72EBD07A),
        0.52f to Color(0x46EFAF12),
        1f to Color(0x389A6A18),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f),
    )
    val rim = Brush.linearGradient(
        0f to Color(0x66B98B1E),
        0.45f to Color(0xCCEBD07A),
        1f to Color(0x66B98B1E),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f),
    )
    val ink = Color(0xFFF2DC9A)
    Row(
        Modifier.clip(pill).background(face).border(1.dp, rim, pill).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(Glyph.heart, style = icon(9f).copy(color = ink))
        BasicText(stringResource(R.string.Rpm_Profile_PlanSupporter), style = system(10f).copy(color = ink))
    }
}
