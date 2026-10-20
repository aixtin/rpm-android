package com.rpm.player.tv.ui

import kotlinx.coroutines.delay
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.runtime.withFrameNanos
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** How long OK is held before a row's menu opens, while the key is still down, as on the desktop. */
const val HOLD_MS = 500L

/**
 * A row's menu on a held OK or on the Menu key. The hold is decided on a repeat of the press, using the
 * key's own clock, so a press that is released sooner is an ordinary OK.
 *
 * The press that opened the menu is identified by its down time, and the rest of that press reaching the row
 * is swallowed. The menu swallows what reaches it ([RowMenu]). A flag that is set on opening and cleared on
 * the release would not work: the release goes to the menu, which has the focus by then, so the flag would
 * stay set and swallow the row's next OK.
 */
fun Modifier.heldOkMenu(onMenu: () -> Unit): Modifier = composed {
    var opener by remember { mutableLongStateOf(NO_PRESS) }
    onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        val ok = key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || key.keyCode == KeyEvent.KEYCODE_ENTER ||
            key.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        when {
            key.downTime == opener -> true
            key.keyCode == KeyEvent.KEYCODE_MENU && key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0 -> {
                opener = key.downTime
                onMenu()
                true
            }
            ok && key.action == KeyEvent.ACTION_DOWN && key.repeatCount > 0 -> {
                if (key.eventTime - key.downTime >= HOLD_MS) {
                    opener = key.downTime
                    onMenu()
                }
                true
            }
            else -> false
        }
    }
}

private const val NO_PRESS = -1L

/**
 * One line of a menu. An entry may carry a Font Awesome [glyph], drawn before its words as the desktop's menus
 * draw their FontIcons (AlbumMenuFactory, TrackMenuFactory), in the solid face unless [outline]: the desktop's
 * one outline glyph is the heart of something not yet a favourite. A menu with a glyph on any line keeps the
 * glyphs' column on every line, so the words of all its lines start at one edge, as WinUI's do.
 */
sealed interface MenuLine

/** An entry, by its string. */
data class MenuChoice(@StringRes val label: Int, val glyph: Char? = null, val outline: Boolean = false, val act: () -> Unit) : MenuLine

/** An entry by its text, ticked when it is the one in force (WinUI's ToggleMenuFlyoutItem). */
data class MenuTick(val text: String, val ticked: Boolean, val act: () -> Unit) : MenuLine

/** An entry that opens more entries in the menu's place (WinUI's MenuFlyoutSubItem). Back returns. */
data class MenuSub(@StringRes val label: Int, val lines: List<MenuLine>, val glyph: Char? = null) : MenuLine

/** An entry that asks for a name in the menu's place: a field with [hint] for the TV's keyboard, and a key
 * labelled [confirm] that hands the trimmed name to [act] and closes the menu. Back returns. */
data class MenuName(@StringRes val label: Int, @StringRes val hint: Int, @StringRes val confirm: Int, val glyph: Char? = null, val act: (String) -> Unit) : MenuLine

/** An entry that asks for a number of stars in the menu's place: [label] as a heading, five stars set from
 * [stars] that Left and Right change, and the keys [cancel] and [confirm]. [confirm] hands the stars (0 for
 * none) to [act] and closes the menu. [cancel] closes it. Back returns to the menu. */
data class MenuStars(@StringRes val label: Int, val stars: Int, @StringRes val cancel: Int, @StringRes val confirm: Int, val glyph: Char? = null, val act: (Int) -> Unit) : MenuLine

/** A rule between entries. */
data object MenuGap : MenuLine

/** Words between entries that are not an entry: the remote skips over them (for example the cast menu's
 * "Looking for speakers"). */
data class MenuNote(val text: String) : MenuLine

/** An entry that does its work and leaves the menu open, for a menu whose lines change while it is open
 * ([RowMenuState.refresh]), for example the cast menu's Look again. */
data class MenuAction(@StringRes val label: Int, val glyph: Char? = null, val act: () -> Unit) : MenuLine

/** Whether the remote can stop on a line. */
private val MenuLine.isEntry: Boolean get() = this !is MenuGap && this !is MenuNote

/** The glyph drawn before a line's words, if it has one ([MenuLine]). A tick's column is its own. */
private val MenuLine.glyph: Char? get() = when (this) {
    is MenuChoice -> glyph
    is MenuSub -> glyph
    is MenuName -> glyph
    is MenuStars -> glyph
    is MenuAction -> glyph
    is MenuTick, is MenuNote, MenuGap -> null
}

/**
 * Whether a row's menu is open, what it offers, and the row that gets the focus back when it closes.
 * [open] takes that row's [FocusRequester]. Without it, a menu that leaves the screen hands the focus to
 * the window's root, where the arrows move nothing and OK presses nothing.
 */
@Stable
class RowMenuState {
    /** What the menu shows: [lines], or, where [naming] is set, the name field of that entry instead. */
    internal class Opened(val title: String, lines: List<MenuLine>, val returnTo: FocusRequester, val parent: Opened?, val naming: MenuName? = null, val rating: MenuStars? = null) {
        var lines by mutableStateOf(lines)
    }

    internal var opened by mutableStateOf<Opened?>(null)
        private set

    /** The menu that closed last. It is drawn while it fades out ([RowMenu]). Null once it has gone, or when
     * another menu opens. */
    internal var closed by mutableStateOf<Opened?>(null)
        private set
    internal var returnTo: FocusRequester? = null
        private set
    /** Counts closings, so each closing asks for the focus back once. Until the next closing it also numbers
     * the opening that is on screen. */
    internal var closes by mutableIntStateOf(0)
        private set

    val isOpen: Boolean get() = opened != null

    fun open(title: String, choices: List<MenuLine>, returnTo: FocusRequester) {
        closed = null
        opened = Opened(title, choices, returnTo, parent = null)
    }

    /** Puts [choices] in place of the open menu's lines. The entry that the remote is on stays selected if it is
     * still listed. Does nothing when the menu is closed or a submenu or a name field is showing. */
    fun refresh(choices: List<MenuLine>) {
        val now = opened ?: return
        if (now.parent != null || now.naming != null || now.rating != null) return
        now.lines = choices
    }

    /** Shows [sub]'s entries in the menu's place. */
    /** Shows [name]'s field in the menu's place. */
    internal fun openName(name: MenuName) {
        val was = opened ?: return
        opened = Opened(was.title, emptyList(), was.returnTo, parent = was, naming = name)
    }

    /** Shows [stars]'s card in the menu's place. */
    internal fun openStars(stars: MenuStars) {
        val was = opened ?: return
        opened = Opened(was.title, emptyList(), was.returnTo, parent = was, rating = stars)
    }

    internal fun openSub(sub: MenuSub) {
        val was = opened ?: return
        opened = Opened(was.title, sub.lines, was.returnTo, parent = was)
    }

    /** Back: from a submenu, returns to the menu it came from. From a menu, closes it. */
    internal fun back() {
        val was = opened ?: return
        if (was.parent != null) opened = was.parent else close()
    }

    fun close() {
        val was = opened ?: return
        returnTo = was.returnTo
        closed = was
        opened = null
        closes += 1
    }

    /** Forgets [menu] once its fade out has ended. It is compared by identity, so a fade that ends after another
     * menu has opened and closed does not forget the newer menu. */
    internal fun gone(menu: Opened) {
        if (closed === menu) closed = null
    }
}

@Composable
fun rememberRowMenu(): RowMenuState = remember { RowMenuState() }

/** What a row's menu is laid over: the screens' picture that it blurs, and the mini player that it covers.
 * The app provides it around the screens. */
class MenuGlass(val backdrop: Backdrop, val mini: MiniPlayerState)

val LocalMenuGlass = staticCompositionLocalOf<MenuGlass?> { null }

/**
 * A row's menu, drawn as the album search is: the screen is blurred under `#33000000` except for the row that
 * the menu was opened on, which stays sharp. Under the row there is a card of the light glass, dimmed, with its
 * entries at the track list's size ([PAGE_TEXT]). The mini player is covered while the menu is up. Back closes
 * the menu, or returns from a submenu. An entry closes the menu and then does its work.
 *
 * The blur is the screen as it was when the menu opened. It is captured into the menu's own picture before any
 * of the menu is drawn, so it never shows the menu itself, and the panels' shared picture (the album and
 * radio searches') is left as it was. Nothing is drawn for the few frames that the capture takes. A screen
 * that could not be captured is only dimmed, never shown blurred from another moment.
 *
 * The rest of the press that opened the menu is swallowed here. Its repeats and its release reach the menu,
 * which has the focus by then, and without this a held OK would pick the first entry when it is let go. A key
 * belongs to that press if it went down before the menu was shown.
 *
 * It is composed whether or not it is open, so that it can give the focus back to its row after it has left
 * the screen. A row that the entry removed cannot take the focus. The caller then gives the focus to another
 * row from the entry's work.
 *
 * A close fades the menu out over [MENU_OUT_MS], as the side menu does. The focus is given back to the row as
 * the fade starts, and nothing in the fading menu can take it. The fading menu is the same composition as the
 * open one (keyed by the opening, not by the state), so it keeps its picture and its row and starts nothing
 * again. The Back handlers of its cards are off during the fade, so a Back in those 110 ms reaches the screen
 * under it.
 */
@Composable
fun RowMenu(state: RowMenuState) {
    LaunchedEffect(state.closes) {
        if (state.closes > 0) runCatching { state.returnTo?.requestFocus() }
    }
    val opened = state.opened
    val leaving = opened == null
    val shown = opened ?: state.closed ?: return
    // An opening is numbered by the closings before it. The one fading out is the one before the last closing.
    val opening = if (leaving) state.closes - 1 else state.closes
    key(opening) { MenuOpening(state, shown, opening, leaving) }
}

/** One opening of a row's menu, from the press to the end of its fade out ([RowMenu]). */
@Composable
private fun MenuOpening(state: RowMenuState, opened: RowMenuState.Opened, opening: Int, leaving: Boolean) {
    val glass = LocalMenuGlass.current
    val owner = LocalFocusSpot.current
    // One opening lasts from the press to the end of its fade out. A submenu shown in the menu's place is the same
    // opening and keeps its picture and its row.
    // Where the focus was when the menu opened: the row that the menu is about.
    val anchor = remember(opening) { owner?.target?.rect }
    val picture = remember(opening) { glass?.let { Backdrop(it.backdrop.layer).apply { ground = it.backdrop.ground } } }
    var taken by remember(opening) { mutableStateOf<Boolean?>(if (picture == null) false else null) }
    LaunchedEffect(opening) {
        if (picture != null) taken = picture.capture()
    }
    if (glass != null) {
        DisposableEffect(opening) {
            glass.mini.cover()
            onDispose { glass.mini.uncover() }
        }
    }
    val fade = remember(opening) { Animatable(0f) }
    // Fades in once the screen has been captured. Fades out from its current level when the menu closes, and is
    // then forgotten. This is before the early return below, so a menu closed before its picture was captured is
    // still forgotten.
    LaunchedEffect(opening, leaving, taken != null) {
        if (leaving) {
            fade.animateTo(0f, tween(MENU_OUT_MS))
            state.gone(opened)
        } else if (taken != null) {
            fade.animateTo(1f, tween(MENU_FADE_MS))
        }
    }
    val blurred = taken ?: return
    val shownAt = remember(opening) { SystemClock.uptimeMillis() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // In the screens' own pixels, as the focus spot's target is ([DrawnAt]).
    val share = LocalDrawnAt.current
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInScreens(share) }
            .graphicsLayer { alpha = fade.value }
            .onPreviewKeyEvent { it.nativeKeyEvent.downTime < shownAt },
    ) {
        val hole = anchor?.translate(-origin.x, -origin.y)
        MenuShade(picture?.takeIf { blurred }, hole, Modifier.fillMaxSize())
        key(opened) {
            val naming = opened.naming
            val rating = opened.rating
            when {
                naming != null -> NameCard(naming, hole, picture?.takeIf { blurred }, leaving = leaving, onBack = state::back, onClose = state::close)
                rating != null -> StarsCard(rating, hole, picture?.takeIf { blurred }, leaving = leaving, onBack = state::back, onClose = state::close)
                else -> MenuCard(
                    opened.lines, hole, picture?.takeIf { blurred }, leaving = leaving,
                    onOpenSub = state::openSub, onOpenName = state::openName, onOpenStars = state::openStars, onBack = state::back, onClose = state::close,
                )
            }
        }
    }
}

/** The screen behind the menu: its blurred picture and `#33000000`, or the shade alone. It covers everything
 * except [hole], where the screen itself shows through. */
@Composable
private fun MenuShade(picture: Backdrop?, hole: Rect?, modifier: Modifier) {
    val image = picture?.blurred
    val ground = picture?.ground ?: Color.Black
    Canvas(modifier) {
        fun DrawScope.shade() {
            if (image != null) {
                drawRect(ground)
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
            drawRect(Color(0x33000000))
        }
        if (hole == null) {
            shade()
        } else {
            clipRect(hole.left, hole.top, hole.right, hole.bottom, ClipOp.Difference) { shade() }
        }
    }
}

@Composable
private fun MenuCard(
    lines: List<MenuLine>,
    anchor: Rect?,
    picture: Backdrop?,
    leaving: Boolean,
    onOpenSub: (MenuSub) -> Unit,
    onOpenName: (MenuName) -> Unit,
    onOpenStars: (MenuStars) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    // Read by the focus requests below, which outlive the composition that started them.
    val leavingNow by rememberUpdatedState(leaving)
    val spot = remember { FocusSpot() }
    // A menu that ticks one entry keeps a column for the tick on every entry, as WinUI's does.
    val ticks = lines.any { it is MenuTick }
    // Likewise a menu with a glyph on any entry keeps the glyphs' column on every entry ([MenuLine]).
    val icons = lines.any { it.glyph != null }
    // Off while the menu fades out: a Back pressed then belongs to the screen under it.
    BackHandler(enabled = !leaving && LocalOnTop.current, onBack = onBack)
    // Whether the focus is anywhere in this card. The request below is repeated until this is true.
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Opening a submenu replaces the card, and with it the entry that had the focus. Compose then moves
        // the focus to the row under the menu, which can happen after the first request here has already
        // succeeded. So the focus is requested again on the next frames until the card holds it.
        repeat(FOCUS_ASKS) {
            if (!holding && !leavingNow) runCatching { first.requestFocus() }
            withFrameNanos { }
        }
    }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            GlassPanel(
                picture,
                MENU_CORNER,
                Modifier
                    .onFocusChanged { holding = it.hasFocus }
                    .widthIn(min = MENU_LEAST, max = MENU_MOST)
                    .width(IntrinsicSize.Max),
            ) {
                Box(Modifier.matchParentSize().background(GLASS_DIM))
                SpotScope(spot, Modifier) {
                    // A menu taller than the screen (for example the tuner's countries) scrolls. The entry that
                    // the remote moves to is brought into view.
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(MENU_INSET),
                        verticalArrangement = Arrangement.spacedBy(2.s),
                    ) {
                        val firstEntry = lines.indexOfFirst { it.isEntry }
                        val lastEntry = lines.indexOfLast { it.isEntry }
                        lines.forEachIndexed { i, line -> key(keyOf(line, i)) {
                            // The screen's rows stay focusable under the shade, so the remote is kept in the
                            // card: no Left or Right, and nothing before the first entry or after the last.
                            val focus = Modifier
                                .then(if (i == firstEntry) Modifier.focusRequester(first) else Modifier)
                                .focusProperties {
                                    // A menu that is fading out accepts no focus, so the row it gave the focus back to keeps it.
                                    canFocus = !leaving
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                    if (i == firstEntry) up = FocusRequester.Cancel
                                    if (i == lastEntry) down = FocusRequester.Cancel
                                }
                            when (line) {
                                is MenuChoice -> MenuEntryRow(stringResource(line.label), ticked = if (ticks) false else null, icons = icons, glyph = line.glyph, outline = line.outline, more = false, onClick = {
                                    onClose()
                                    line.act()
                                }, modifier = focus)
                                is MenuTick -> MenuEntryRow(line.text, ticked = line.ticked, icons = icons, more = false, onClick = {
                                    onClose()
                                    line.act()
                                }, modifier = focus)
                                is MenuSub -> MenuEntryRow(stringResource(line.label), ticked = if (ticks) false else null, icons = icons, glyph = line.glyph, more = true, onClick = { onOpenSub(line) }, modifier = focus)
                                is MenuName -> MenuEntryRow(stringResource(line.label), ticked = if (ticks) false else null, icons = icons, glyph = line.glyph, more = false, onClick = { onOpenName(line) }, modifier = focus)
                                is MenuStars -> MenuEntryRow(stringResource(line.label), ticked = if (ticks) false else null, icons = icons, glyph = line.glyph, more = false, onClick = { onOpenStars(line) }, modifier = focus)
                                is MenuAction -> MenuEntryRow(stringResource(line.label), ticked = if (ticks) false else null, icons = icons, glyph = line.glyph, more = false, onClick = line.act, modifier = focus)
                                is MenuNote -> Text(
                                    line.text,
                                    style = ui(PAGE_TEXT),
                                    color = Ink.Dim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 16.s + (if (ticks) TICK_WIDE else 0.s) + (if (icons) ICON_WIDE + ICON_GAP else 0.s), end = 16.s, top = 9.s, bottom = 9.s),
                                )
                                MenuGap -> Box(Modifier.padding(horizontal = 10.s, vertical = 4.s).fillMaxWidth().height(1.px).background(Color(0x26FFFFFF)))
                            }
                        } }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val gap = MENU_GAP.roundToPx()
        val card = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = roomBeside(anchor, gap, constraints.maxHeight).coerceAtLeast(0)))
        val spot = spotBeside(anchor, gap, MENU_EDGE.roundToPx(), constraints.maxWidth, constraints.maxHeight, card.width, card.height)
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(spot) }
    }
}

/**
 * A name asked for in the menu's place ([MenuName]): the entry's words as a heading, a field for the TV's
 * keyboard with the hint in it, and the key that takes the name, all at [PAGE_TEXT], in the menu's card.
 * The keyboard comes up with the field. Its own Done key takes the name. An empty name is not taken.
 *
 * The TV's keyboard covers the screen and takes the remote's first Back itself, and when it closes it takes
 * the view's focus with it (the album search behaves the same way). So the closing of the keyboard is read
 * from the window's insets. The focus is then put on the key if a name has been typed, or back on the field if
 * not, and is requested again on the next frames until the card holds it. Down from the field goes to the
 * key, and Up from the key goes to the field. Back, with the keyboard away, returns to the menu this came from.
 */
@Composable
private fun NameCard(naming: MenuName, anchor: Rect?, picture: Backdrop?, leaving: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val take = remember { FocusRequester() }
    val spot = remember { FocusSpot() }
    val keyboard = LocalSoftwareKeyboardController.current
    // A view of the activity's window, which the keyboard opens over, even where the screens are on a virtual display.
    val view = LocalKeyboardView.current
    var holding by remember { mutableStateOf(false) }
    // Read by the focus requests and the keyboard watch below, which outlive the composition that started them.
    val leavingNow by rememberUpdatedState(leaving)
    // Requests the focus for [target] on this frame and the next few until the card holds the focus.
    var asks by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(asks) {
        val wanted = target ?: return@LaunchedEffect
        repeat(FOCUS_ASKS) {
            if (!holding && !leavingNow) runCatching { wanted.requestFocus() }
            withFrameNanos { }
        }
    }
    fun focus(on: FocusRequester) {
        target = on
        asks += 1
    }
    fun submit() {
        val chosen = name.trim()
        if (chosen.isEmpty()) return
        keyboard?.hide()
        onClose()
        naming.act(chosen)
    }
    BackHandler(enabled = !leaving && LocalOnTop.current, onBack = onBack)
    LaunchedEffect(Unit) {
        focus(field)
        withFrameNanos { }
        keyboard?.show()
    }
    LaunchedEffect(Unit) {
        var seen = false
        while (true) {
            val up = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            // A name that is taken closes the menu and hides the keyboard. The card fading out does not request the focus.
            if (seen && !up && !leavingNow) {
                holding = false
                focus(if (name.isNotBlank()) take else field)
            }
            seen = up
            delay(KEYBOARD_POLL_MS)
        }
    }
    val shape = RoundedCornerShape(8.px)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            GlassPanel(
                picture,
                MENU_CORNER,
                Modifier
                    .onFocusChanged { holding = it.hasFocus }
                    .width(NAME_WIDE),
            ) {
                Box(Modifier.matchParentSize().background(GLASS_DIM))
                SpotScope(spot, Modifier) {
                    Column(Modifier.padding(16.s), verticalArrangement = Arrangement.spacedBy(12.s)) {
                        Text(stringResource(naming.label), style = ui(PAGE_TEXT, FontWeight.SemiBold), color = Ink.Words, modifier = Modifier.padding(horizontal = 4.s))
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(NAME_LONGEST) },
                            singleLine = true,
                            textStyle = ui(PAGE_TEXT).copy(color = Ink.Words),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(field)
                                .deskFocus(8.px)
                                .focusProperties {
                                    canFocus = !leaving
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                    up = FocusRequester.Cancel
                                    down = take
                                },
                            decorationBox = { inner ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x1FFFFFFF), shape)
                                        .border(1.px, Color(0x26FFFFFF), shape)
                                        .padding(horizontal = 14.s, vertical = 10.s),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (name.isEmpty()) Text(stringResource(naming.hint), style = ui(PAGE_TEXT), color = Color(0x8CFFFFFF), maxLines = 1)
                                    inner()
                                }
                            },
                        )
                        MenuEntryRow(
                            stringResource(naming.confirm),
                            ticked = null,
                            more = false,
                            onClick = { submit() },
                            modifier = Modifier
                                .focusRequester(take)
                                .focusProperties {
                                    canFocus = !leaving
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                    up = field
                                    down = FocusRequester.Cancel
                                },
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val gap = MENU_GAP.roundToPx()
        val card = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = roomBeside(anchor, gap, constraints.maxHeight).coerceAtLeast(0)))
        val spot = spotBeside(anchor, gap, MENU_EDGE.roundToPx(), constraints.maxWidth, constraints.maxHeight, card.width, card.height)
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(spot) }
    }
}

/**
 * Stars asked for in the menu's place ([MenuStars]), as a small card: the entry's words as a heading, five stars,
 * and under them the keys to cancel and to confirm, at [PAGE_TEXT]. The focus starts on the stars, where Left and
 * Right remove or add one star (none to five). Down goes to the confirm key. Cancel closes the menu, as an
 * entry does. Back returns to the menu this came from.
 */
@Composable
private fun StarsCard(rating: MenuStars, anchor: Rect?, picture: Backdrop?, leaving: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    var stars by remember { mutableIntStateOf(rating.stars.coerceIn(0, STARS)) }
    val row = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    val confirm = remember { FocusRequester() }
    val spot = remember { FocusSpot() }
    var holding by remember { mutableStateOf(false) }
    var onStars by remember { mutableStateOf(false) }
    val leavingNow by rememberUpdatedState(leaving)
    BackHandler(enabled = !leaving && LocalOnTop.current, onBack = onBack)
    LaunchedEffect(Unit) {
        // Requested again on the next frames, as for a submenu's card, because this card replaced the menu entry that had the focus.
        repeat(FOCUS_ASKS) {
            if (!holding && !leavingNow) runCatching { row.requestFocus() }
            withFrameNanos { }
        }
    }
    val shape = RoundedCornerShape(8.px)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            GlassPanel(
                picture,
                MENU_CORNER,
                Modifier
                    .onFocusChanged { holding = it.hasFocus }
                    .width(STARS_WIDE),
            ) {
                Box(Modifier.matchParentSize().background(GLASS_DIM))
                SpotScope(spot, Modifier) {
                    Column(Modifier.padding(16.s), verticalArrangement = Arrangement.spacedBy(12.s), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(rating.label), style = ui(PAGE_TEXT, FontWeight.SemiBold), color = Ink.Words)
                        DeskButton(
                            onClick = { runCatching { confirm.requestFocus() } },
                            corner = 8.px,
                            modifier = Modifier
                                .focusRequester(row)
                                .onFocusChanged { onStars = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    val key = event.nativeKeyEvent
                                    if (key.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                    when (key.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> { stars = (stars - 1).coerceAtLeast(0); true }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> { stars = (stars + 1).coerceAtMost(STARS); true }
                                        else -> false
                                    }
                                }
                                .focusProperties {
                                    canFocus = !leaving
                                    up = FocusRequester.Cancel
                                    down = confirm
                                }
                                .background(if (onStars) Color(0x33FFFFFF) else Color.Transparent, shape)
                                .padding(horizontal = 16.s, vertical = 10.s),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.s)) {
                                repeat(STARS) { i ->
                                    val lit = i < stars
                                    Glyph(Fa.STAR, 30.ts, if (lit) Ink.Words else Color(0x66FFFFFF), regular = !lit)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.s)) {
                            StarsKey(stringResource(rating.cancel), Modifier.weight(1f).focusRequester(cancel).focusProperties {
                                canFocus = !leaving
                                up = row
                                left = FocusRequester.Cancel
                                right = confirm
                                down = FocusRequester.Cancel
                            }) { onClose() }
                            StarsKey(stringResource(rating.confirm), Modifier.weight(1f).focusRequester(confirm).focusProperties {
                                canFocus = !leaving
                                up = row
                                left = cancel
                                right = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }) {
                                onClose()
                                rating.act(stars)
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val gap = MENU_GAP.roundToPx()
        val card = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = roomBeside(anchor, gap, constraints.maxHeight).coerceAtLeast(0)))
        val spot = spotBeside(anchor, gap, MENU_EDGE.roundToPx(), constraints.maxWidth, constraints.maxHeight, card.width, card.height)
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(spot) }
    }
}

/** A key of the stars card: its words centred. It is lit as a menu entry is while the remote is on it. */
@Composable
private fun StarsKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    var lit by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.px)
    DeskButton(
        onClick = onClick,
        corner = 8.px,
        modifier = modifier
            .onFocusChanged { lit = it.isFocused }
            .background(if (lit) Color(0x33FFFFFF) else Color(0x14FFFFFF), shape)
            .padding(horizontal = 16.s, vertical = 9.s),
    ) {
        Text(label, style = ui(PAGE_TEXT), color = Ink.Words, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val STARS = 5

/** How wide the stars card is: five stars at 30·ts with room either side. */
private val STARS_WIDE = 380.s

/** The tallest a card may be beside [row]: the larger of the room under the row and the room over it, so a long
 * menu scrolls there instead of covering the row it belongs to (for example a menu of fourteen playlists).
 * Without a row, it is the height of the screen. */
private fun roomBeside(row: Rect?, gap: Int, height: Int): Int =
    if (row == null) height - gap * 2 else maxOf(height - row.bottom.toInt() - gap * 2, row.top.toInt() - gap * 2)

/** Where a card of [w] by [h] goes: under [row] where it fits, else over it. It starts at the row's left edge,
 * or ends at the row's right edge where starting at the left would run past [edge] from the side of the screen
 * (for example a key in the top right corner, such as the order key of the artists list). It is never nearer
 * to either side than [edge]. Without a row it is centred. Because it is measured to [roomBeside], it fits on
 * one side or the other. */
private fun spotBeside(row: Rect?, gap: Int, edge: Int, width: Int, height: Int, w: Int, h: Int): IntOffset {
    val start = when {
        row == null -> (width - w) / 2
        row.left.toInt() + w <= width - edge -> row.left.toInt()
        else -> row.right.toInt() - w
    }
    val x = start.coerceIn(edge, (width - w - edge).coerceAtLeast(edge))
    val y = when {
        row == null -> (height - h) / 2
        row.bottom.toInt() + gap + h <= height - gap -> row.bottom.toInt() + gap
        else -> (row.top.toInt() - gap - h).coerceAtLeast(gap)
    }
    return IntOffset(x, y)
}

/** What a line is identified by while the lines of its menu change, so the entry that the remote is on stays
 * the same entry. */
private fun keyOf(line: MenuLine, at: Int): Any = when (line) {
    is MenuChoice -> line.label
    is MenuTick -> line.text
    is MenuSub -> line.label
    is MenuName -> line.label
    is MenuStars -> line.label
    is MenuAction -> line.label
    is MenuNote -> line.text
    MenuGap -> at
}

/** How wide the name card is: room for a name of a few words at [PAGE_TEXT]. */
private val NAME_WIDE = 460.s

/** The longest name the API takes for a playlist (`CloudController.PutPlaylist`). */
private const val NAME_LONGEST = 200

/** How often the closing of the keyboard is checked while a name card is up. The album search uses the same value. */
private const val KEYBOARD_POLL_MS = 150L

/**
 * One entry, lit as a search result is (`#33FFFFFF`, corner 8) while the remote is on it: its words
 * at [PAGE_TEXT], the size of a track's title in the list, with padding of 16·s by 9·s. [ticked] is null in a
 * menu without ticks, otherwise it says whether the tick of this entry shows. [icons] keeps the glyphs' column,
 * after the tick's, where [glyph] is drawn if this entry has one, in the regular face if [outline]. [more]
 * draws the chevron of a submenu.
 *
 * The desktop draws a menu's FontIcon in a 16 by 16 box at the size of the entry's words (FaMenuIcon in
 * Generic.xaml), 12 before its words. Here that is scaled by the words' own size, [PAGE_TEXT] against 12.
 */
@Composable
private fun MenuEntryRow(
    label: String,
    ticked: Boolean?,
    more: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icons: Boolean = false,
    glyph: Char? = null,
    outline: Boolean = false,
) {
    var lit by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.px)
    DeskButton(
        onClick = onClick,
        corner = 8.px,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .onFocusChanged { lit = it.isFocused }
            .fillMaxWidth()
            .background(if (lit) Color(0x33FFFFFF) else Color.Transparent, shape)
            .padding(horizontal = 16.s, vertical = 9.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ticked != null) {
                Box(Modifier.width(TICK_WIDE)) { if (ticked) Glyph(Fa.CHECK, 15.ts, Ink.Words) }
            }
            if (icons) {
                Box(Modifier.width(ICON_WIDE), contentAlignment = Alignment.Center) { if (glyph != null) Glyph(glyph, ICON_SIZE, Ink.Words, regular = outline) }
                Spacer(Modifier.width(ICON_GAP))
            }
            Text(label, style = ui(PAGE_TEXT), color = Ink.Words, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (more) {
                Spacer(Modifier.width(24.s))
                Glyph(Fa.CHEVRON_RIGHT, 13.ts, Ink.Dim)
            }
        }
    }
}

private val MENU_CORNER = 12.s

/** The tick's column, in a menu that ticks an entry. */
private val TICK_WIDE = 30.s

/** The glyphs' column and the room after it, in a menu with glyphs: the desktop's 16 and 12 at its words'
 * 12, scaled to [PAGE_TEXT]. The glyph is a little under the words' size, as Font Awesome's run larger than
 * Selawik's letters at one size. */
private val ICON_WIDE = 26.s
private val ICON_GAP = 18.s
private val ICON_SIZE = 17.ts
private val MENU_INSET = 8.s
private val MENU_GAP = 8.px

/** The least room a card keeps from either side of the screen. It equals the screens' own inset. */
private val MENU_EDGE = 34.px
private val MENU_LEAST = 240.s
private val MENU_MOST = 640.s

/** The menu and its shade fade in together over this time. */
private const val MENU_FADE_MS = 160

/** The menu and its shade fade out together over this time. The side menu closes in the same time. */
private const val MENU_OUT_MS = 110

/** How many frames a new card requests the focus, until it holds it. */
private const val FOCUS_ASKS = 4
