package com.rpm.player.phone.radio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rpm.player.core.R
import com.rpm.player.core.radio.Icy
import com.rpm.player.core.radio.RadioAds
import com.rpm.player.core.radio.RadioGenres
import com.rpm.player.core.radio.RadioRepository
import com.rpm.player.core.radio.RadioStation
import com.rpm.player.core.radio.StationLogo
import com.rpm.player.core.radio.StationOrder
import com.rpm.player.core.radio.Tuner
import com.rpm.player.core.radio.TunerList
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.GlassCard
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.PageTitle
import com.rpm.player.phone.design.SearchBox
import com.rpm.player.phone.design.backdropSource
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.dissolvesAtTop
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.kicker
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.design.scrolled
import com.rpm.player.phone.design.system
import com.rpm.player.phone.library.FOOT
import com.rpm.player.phone.ui.RecordSpinner
import com.rpm.player.phone.ui.VinylGround
import java.util.Locale

/** Which of the filter row's menus is open. */
private enum class Picking { Genre, Country, Order }

/** The orders that the sort key offers, with their names (`RadioPage.named` on iPhone). */
private val ORDERS = listOf(
    StationOrder.Listed to R.string.Radio_SortListed,
    StationOrder.NameUp to R.string.Radio_SortNameUp,
    StationOrder.NameDown to R.string.Radio_SortNameDown,
    StationOrder.Votes to R.string.Radio_SortVotes,
    StationOrder.Quality to R.string.Radio_SortQuality,
)

/**
 * Internet radio. It matches the iPhone's `RadioPage` (RPMUI/RadioPage.swift). From the top: the dial, then the
 * stations it can reach. The dial and the list are one list of stations. So dragging the needle and tapping a
 * row tune the same station. Below the dial is the search box, and a row of filters: the genre, the country,
 * the order, and the saved stations only.
 *
 * The question sent to the station directory, the answer, and the list built from it are in `:core`'s [Tuner].
 * The TV app's tuner reads the same code. The tuner waits until the typing pauses. It asks one question at a
 * time, and keeps answers for half an hour. The phone starts with every country, as the iPhone app does
 * ([com.rpm.player.core.radio.TunerOpensOn]).
 *
 * Not yet implemented: a station's logo on its own background, where the logo needs one (the iPhone's `StationFace`).
 *
 * @param tuned the id of the station that is playing. The row's mark and the needle follow it.
 * @param said what that station announces now.
 */
@Composable
fun RadioPage(
    radio: RadioRepository,
    images: ImageLoader,
    tuned: String?,
    said: Icy.Said?,
    bottomRoom: Dp,
    onTune: (RadioStation) -> Unit,
) {
    val tuner = radio.tuner
    val settings by tuner.settings.collectAsStateWithLifecycle()
    val answer by tuner.answer.collectAsStateWithLifecycle()
    val countries by tuner.countries.collectAsStateWithLifecycle()
    val saved by radio.favorites.stations.collectAsStateWithLifecycle()
    val rules by radio.rules.collectAsStateWithLifecycle()
    val page = rememberBackdrop()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var picking by remember { mutableStateOf<Picking?>(null) }
    BackHandler(enabled = picking != null) { picking = null }

    LaunchedEffect(Unit) {
        tuner.ensureLoaded()
        radio.refresh()
    }
    val list = remember(settings, answer, saved, rules) { Tuner.listOf(settings, answer, saved, rules) }
    val announced = when (said) {
        is Icy.Said.Song -> said.artist?.let { "$it · ${said.title}" } ?: said.title
        Icy.Said.AdBreak -> stringResource(R.string.Radio_AdBreak)
        null -> null
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(page)) {
            VinylGround()
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PageTitle(stringResource(R.string.Rpm_Tab_Radio))
                    BasicText(stringResource(R.string.Radio_Subtitle), style = kicker())
                }
                RadioDial(list.stations, tuned, announced, onTune, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
                Controls(
                    text = settings.text,
                    onText = { typed -> tuner.update { it.copy(text = typed) } },
                    focus = focus,
                    genre = settings.sieve.genre,
                    country = countries.firstOrNull { it.code == settings.country }?.name,
                    sorted = settings.order != StationOrder.Listed,
                    savedOnly = settings.favoritesOnly,
                    onPick = { focusManager.clearFocus(); picking = it },
                    onSaved = { tuner.update { it.copy(favoritesOnly = !it.favoritesOnly) } },
                )
                if (list.directoryDown) Trouble()
                Box(Modifier.weight(1f).imePadding()) {
                    Stations(list, images, rules.logoStamp, tuned, saved, bottomRoom, onTune, onSave = radio.favorites::toggle)
                }
            }
        }
        when (picking) {
            Picking.Genre -> Choices(
                page,
                listOf(null to stringResource(R.string.Radio_AllGenres)) + RadioGenres.all.map { it to it },
                settings.sieve.genre,
                onPick = { genre -> tuner.update { it.copy(sieve = it.sieve.copy(genre = genre)) }; picking = null },
                onClose = { picking = null },
                bottomRoom = bottomRoom,
            )
            Picking.Country -> Choices(
                page,
                listOf(null to stringResource(R.string.Radio_AllCountries)) + countries.map { it.code to it.name },
                settings.country,
                onPick = { code -> tuner.update { it.copy(country = code) }; picking = null },
                onClose = { picking = null },
                bottomRoom = bottomRoom,
            )
            Picking.Order -> Choices(
                page,
                ORDERS.map { (order, label) -> order to stringResource(label) },
                settings.order,
                onPick = { order -> tuner.update { it.copy(order = order ?: StationOrder.Listed) }; picking = null },
                onClose = { picking = null },
                bottomRoom = bottomRoom,
            )
            null -> Unit
        }
    }
}

/** The search box on a line of its own, and under it the genre, the country, the order and the saved stations. */
@Composable
private fun Controls(
    text: String,
    onText: (String) -> Unit,
    focus: FocusRequester,
    genre: String?,
    country: String?,
    sorted: Boolean,
    savedOnly: Boolean,
    onPick: (Picking) -> Unit,
    onSaved: () -> Unit,
) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchBox(text, onText, stringResource(R.string.Radio_SearchPlaceholder), focus, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chooser(genre ?: stringResource(R.string.Radio_AllGenres), Modifier.weight(1f)) { onPick(Picking.Genre) }
            Chooser(country ?: stringResource(R.string.Radio_AllCountries), Modifier.weight(1f)) { onPick(Picking.Country) }
            Key(Glyph.sortAmount, on = sorted) { onPick(Picking.Order) }
            Key(Glyph.heart, on = savedOnly, onClick = onSaved)
        }
    }
}

/** One of the two boxes that open a menu. It shows what is chosen, and an up-and-down mark. */
@Composable
private fun Chooser(said: String, modifier: Modifier, onClick: () -> Unit) {
    val well = RoundedCornerShape(8.dp)
    Row(
        modifier
            .height(40.dp)
            .clip(well)
            .background(Ink.white(0x14))
            .border(1.dp, Ink.white(0x1A), well)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.DropdownList, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(said, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(13f).copy(color = Ink.white(0xE6)))
        BasicText(Glyph.sort, style = icon(10f).copy(color = Ink.white(0xE6)))
    }
}

/**
 * A square key at the end of the filter row. It is cyan while it is active, so the user can see that the list
 * is narrowed or reordered.
 */
@Composable
private fun Key(glyph: String, on: Boolean, onClick: () -> Unit) {
    val well = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(44.dp, 40.dp)
            .clip(well)
            .background(if (on) Ink.cyan.copy(alpha = 0x1F / 255f) else Ink.white(0x14))
            .border(1.dp, if (on) Ink.cyan.copy(alpha = 0x55 / 255f) else Ink.white(0x1A), well)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = icon(14f).copy(color = if (on) Ink.cyan else Ink.white(0x88)))
    }
}

/** Explains why the list is short. It is shown in its own row above the list, not over the first station. */
@Composable
private fun Trouble() {
    val well = RoundedCornerShape(8.dp)
    BasicText(
        stringResource(R.string.Radio_DirectoryDown),
        Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(well)
            .background(Color.Black.copy(alpha = 0x22 / 255f))
            .border(1.dp, Ink.cardBorder, well)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = display(12f).copy(color = Ink.white(0xAA)),
    )
}

@Composable
private fun Stations(
    list: TunerList,
    images: ImageLoader,
    logoStamp: String,
    tuned: String?,
    saved: List<RadioStation>,
    bottomRoom: Dp,
    onTune: (RadioStation) -> Unit,
    onSave: (RadioStation) -> Unit,
) {
    if (list.stations.isEmpty()) {
        // Centred in the space the chrome leaves, so it is not behind the mini player.
        Column(Modifier.fillMaxSize().padding(bottom = bottomRoom), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            if (list.asking) {
                RecordSpinner(44.dp)
            } else {
                BasicText(Glyph.radio, style = icon(40f).copy(color = Ink.white(0x3A)))
                val nothing = when (list.empty) {
                    TunerList.Empty.NoFavorites -> R.string.Radio_NoFavorites
                    TunerList.Empty.NoFilterMatch -> R.string.Radio_NoFilterMatch
                    else -> R.string.Radio_NoResults
                }
                BasicText(stringResource(nothing), Modifier.widthIn(max = 300.dp), style = display(14f).copy(color = Ink.white(0x88), textAlign = TextAlign.Center))
            }
        }
        return
    }
    val rows = rememberLazyListState()
    val focus = LocalFocusManager.current
    LaunchedEffect(rows.isScrollInProgress) { if (rows.isScrollInProgress) focus.clearFocus() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = rows,
            modifier = Modifier.fillMaxSize().dissolvesAtTop(30.dp) { rows.scrolled() },
            contentPadding = PaddingValues(bottom = FOOT + bottomRoom),
        ) {
            items(list.stations, key = { it.uuid }) { station ->
                StationRow(station, images, logoStamp, playing = tuned != null && station.answersTo(tuned), isSaved = saved.any { it.uuid.equals(station.uuid, ignoreCase = true) }, onTune, onSave)
            }
        }
        // The last answer stays listed while a new one is being asked for. The spinner shows that more is coming.
        if (list.asking) RecordSpinner(34.dp, Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
    }
}

/**
 * One station: the playing mark in the margin, its logo, its name with the AD mark if it has ads, a line about
 * it, and the save key.
 */
@Composable
private fun StationRow(
    station: RadioStation,
    images: ImageLoader,
    logoStamp: String,
    playing: Boolean,
    isSaved: Boolean,
    onTune: (RadioStation) -> Unit,
    onSave: (RadioStation) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onTune(station) }
            .padding(start = 6.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(50)).background(if (playing) Color.White else Color.Transparent))
        Logo(station, images, logoStamp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(station.display, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(14f).copy(color = Color.White))
                if (RadioAds.carriesAds(station.tags, station.name, station.homepage)) AdsBadge()
            }
            BasicText(said(station), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(11f).copy(color = Ink.white(0x88)))
        }
        Box(
            Modifier.size(40.dp, 44.dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onSave(station) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(Glyph.heart, style = icon(14f).copy(color = if (isSaved) Ink.cyan else Ink.white(0x44)))
        }
    }
}

/** Example: "Netherlands · Jazz, Soul · MP3 128k". It shows the country, up to three genres, and the quality. */
private fun said(station: RadioStation): String {
    val parts = ArrayList<String>()
    station.countryCode?.let { com.rpm.player.core.radio.countryName(it, Locale.getDefault()) }?.let(parts::add)
        ?: station.country?.takeIf { it.isNotBlank() }?.let(parts::add)
    com.rpm.player.core.radio.RadioNames.genres(station.shownTags).take(3).takeIf { it.isNotEmpty() }?.let { parts += it.joinToString(", ") }
    station.quality?.let(parts::add)
    return parts.joinToString("  ·  ")
}

/**
 * The station's own logo in a 40 dp well. The antenna icon is shown when there is no logo, or when the logo
 * link no longer works.
 */
@Composable
private fun Logo(station: RadioStation, images: ImageLoader, logoStamp: String) {
    val well = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(40.dp).clip(well).background(Color.Black.copy(alpha = 0x22 / 255f)).border(1.dp, Ink.cardBorder, well),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(Glyph.radio, style = icon(15f).copy(color = Ink.white(0x3A)))
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(StationLogo.of(station, logoStamp)).build(),
            contentDescription = null,
            imageLoader = images,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(3.dp),
        )
    }
}

/** The "AD" mark, cut out of a small pale tile. It is shown after the name of a station that carries advertising. */
@Composable
private fun AdsBadge() {
    val said = stringResource(R.string.Radio_CarriesAds)
    Box(
        Modifier.size(19.dp, 13.dp).clip(RoundedCornerShape(2.5.dp)).background(Ink.white(0x8C)).semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("AD", style = system(9f, FontWeight.Bold).copy(color = Ink.ground))
    }
}

/**
 * A menu of choices, as glass over the page. The choice in use is ticked. The menu scrolls when it is long, as
 * the country list does.
 */
@Composable
private fun <T> Choices(backdrop: Backdrop, choices: List<Pair<T?, String>>, chosen: T?, onPick: (T?) -> Unit, onClose: () -> Unit, bottomRoom: Dp) {
    // Centred in the space that the status bar and the chrome leave. So the bottom of the menu is not under the mini
    // player.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .statusBarsPadding()
            .padding(top = 12.dp, bottom = bottomRoom + 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(backdrop, Modifier.widthIn(max = 300.dp).padding(horizontal = 32.dp), radius = 14.dp) {
            LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                items(choices) { (choice, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onPick(choice) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = system(15f).copy(color = Color.White))
                        if (choice == chosen) {
                            Spacer(Modifier.width(8.dp))
                            BasicText(Glyph.check, style = icon(13f).copy(color = Color.White))
                        }
                    }
                }
            }
        }
    }
}
