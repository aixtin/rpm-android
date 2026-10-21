package com.rpm.player.tv.ui

import com.rpm.player.core.playback.EXTRA_COVER_KEYS
import com.rpm.player.core.playback.QueuePart
import com.rpm.player.core.playback.QueueParts
import com.rpm.player.core.playback.TrackRef
import com.rpm.player.core.playback.isUpNext
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.library.Cover
import com.rpm.player.core.graphics.boxBlur
import com.rpm.player.core.room.PLAYER_BRIGHTNESS
import com.rpm.player.core.room.RoomPalette
import com.rpm.player.core.room.RoomStills
import com.rpm.player.core.room.RoomStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * One row of the queue as the controller holds it. [key] is the media id and which copy of it this is
 * (a track can be queued twice), so a row keeps its key when a move carries it past other tracks. [upNext] says
 * the person added it with Play next or Add to queue; the mark is the item's own, so it moves with the row.
 */
private data class QueueRow(val key: String, val mediaId: String, val title: String, val durationMs: Long, val cover: Cover?, val upNext: Boolean)

private fun readQueue(player: Player): List<QueueRow> {
    val copies = HashMap<String, Int>()
    return (0 until player.mediaItemCount).map { i ->
        val item = player.getMediaItemAt(i)
        val metadata = item.mediaMetadata
        val copy = copies.merge(item.mediaId, 1, Int::plus)!!
        QueueRow(
            key = "${item.mediaId}#$copy",
            mediaId = item.mediaId,
            title = metadata.title?.toString().orEmpty(),
            durationMs = metadata.durationMs ?: 0,
            cover = TrackRef.parse(item.mediaId)?.let { ref ->
                metadata.extras?.getStringArray(EXTRA_COVER_KEYS)?.takeIf { it.isNotEmpty() }?.let { Cover(ref.shelf, it.toList(), stamp = 0) }
            },
            upNext = item.isUpNext,
        )
    }
}

/** Whether the controller still holds [row] at [index]. Every edit asks this at the moment of the key. */
private fun Player.holds(index: Int, row: QueueRow): Boolean =
    index in 0 until mediaItemCount && getMediaItemAt(index).mediaId == row.mediaId

/**
 * What the drawer's glass blurs: the player's picture (the record, the lines, the keys), recorded where it is drawn
 * ([backdropSource] on the pushed box, while the drawer is up). Its ground is clear, so where the picture is empty
 * the blurred still of the room under it ([DrawerRoom]) shows through.
 */
@Composable
fun rememberDrawerPicture(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer).apply { ground = Color.Transparent } }
}

/**
 * The room under the drawer's glass, frosted: a still of the player's room ([RoomStills]), shrunk to a sixth and
 * blurred once as deep as the live glass ([DEEP_RADIUS], about the side menu's 35 px), drawn opaque in the card
 * under the blurred picture. The room itself is a surface behind the window, which no layer can read on Android 9
 * to 11, so through the drawer's translucent smoke it showed sharp and moving; on home the glass is frosted because
 * the screens there stand on a still picture of the room, which can be blurred. The still does not move with the
 * room, which a blur this deep hides.
 *
 * Held by the player, not the drawer, so a drawer opened again over the same room has it at once. It is made again
 * only when what it shows changes ([Look]): another track's colours, another style, the spread view's dimmed left.
 * Making it costs one picture of the room in the stills' own GL context, off the main thread, as home's still does;
 * drawing it costs one small bitmap scaled over the card, on frames the drawer draws anyway.
 */
@Stable
class DrawerRoom(private val stills: RoomStills) {
    /** What the still is of: the room's [palette], [style] and [bare] ([com.rpm.player.core.room.RoomTarget]). */
    data class Look(val palette: RoomPalette, val style: RoomStyle, val bare: Float)

    /** The blurred still at a sixth of the player's size; null before the first. Main thread. */
    var blurred by mutableStateOf<ImageBitmap?>(null)
        private set
    private var madeFor: Pair<Look, IntSize>? = null

    /** Makes the still for [look] at [width] by [height], the player's pixels, unless it is made already. The last
     * still stays up until the new one is made. Cancelling it leaves the last still in place. */
    suspend fun make(look: Look, width: Int, height: Int) {
        val key = look to IntSize(width, height)
        if (width <= 0 || height <= 0 || madeFor == key) return
        // Under the player the room is drawn at PLAYER_BRIGHTNESS. The picture setting's light is not put in: the
        // player's layer lays it on everything drawn in it, this still too ([DrawnAt]).
        val full = stills.picture(look.palette, look.style, width, height, dim = PLAYER_BRIGHTNESS, bare = look.bare) ?: return
        blurred = withContext(Dispatchers.Default) {
            val small = android.graphics.Bitmap.createScaledBitmap(full, (width / ROOM_SHRINK).coerceAtLeast(1), (height / ROOM_SHRINK).coerceAtLeast(1), true)
            full.recycle()
            boxBlur(small, DEEP_RADIUS)
            small.asImageBitmap()
        }
        madeFor = key
    }
}

/** The drawer's still of the room is kept at this share of the player's size, as the panels' backdrop is. */
private const val ROOM_SHRINK = 6

/**
 * Pegasus's queue drawer (PegasusChrome.xaml, the drawer), as a card of the side menu's smoked glass ([SideMenu]:
 * corner 7·s, 10·s in from the left, top and bottom, [Ink.Smoke] and its rim, dimmed by [GLASS_DIM]) down the left of
 * the player, 0.36 of the screen wide. The room stays where it is behind it, frosted: the room is a surface behind
 * the window, which the glass cannot blur, so the card covers it with a blurred still of it ([room], [roomLook]),
 * and the picture pushed aside ([picture]) is blurred over that on every frame ([drawLiveBlur]), as the side menu
 * blurs the screens.
 *
 * It holds the queue, with the playing row lit and focused when the drawer opens. OK plays a row. A held OK or the
 * Menu key opens the row's menu (Play, Play next, Move, Remove from queue). Move lifts the row: Up and Down carry it
 * one place, OK places it, and Back puts it back where it started.
 *
 * The rearrange key beside the close key turns rearranging on: the rows show their grips, and OK on a row lifts it
 * as Move does, so a remote can reorder the queue with OK, Up, Down and OK again. The key stays lit while it is on;
 * pressing it again, or Back, turns it off. A held OK still opens the row's menu. Back closes the menu, then a move,
 * then rearranging, then the drawer. Right also closes the drawer.
 *
 * The songs the person added with Play next or Add to queue ([com.rpm.player.core.playback.EXTRA_UP_NEXT]) stand
 * under an "Up next" heading, in their true place in the queue, and a smaller heading marks where the list that was
 * playing goes on after them ([QueueParts]). The headings take no focus.
 *
 * The rows are read from the controller again after every edit made here and on every player event. The
 * controller's queue can change between a read and an edit, so every edit first checks
 * that the row is still at the index it acts on.
 *
 * It slides in from the left while [open] and slides back out once it is closed, over [DRAWER_GLIDE_MS] both ways.
 * While it slides out nothing in it can take the focus, and [onGone] says the slide has ended, so the player can
 * remove it. If it is opened again while it is leaving, it slides back in from where it is and gives the focus
 * to its row.
 */
@Composable
fun QueueDrawer(player: Player, images: ImageLoader, picture: Backdrop, room: DrawerRoom, roomLook: DrawerRoom.Look, open: Boolean, onClose: () -> Unit, onGone: () -> Unit) {
    var rows by remember { mutableStateOf(readQueue(player)) }
    var playing by remember { mutableStateOf(player.currentMediaItemIndex) }
    // The songs and the headings over the added ones, in the order they are listed.
    val parts = remember(rows) { QueueParts.of(rows.map { it.upNext }) }
    // The row being moved, the index it is at now and the index it started from. The row is null when nothing moves.
    var moving by remember { mutableStateOf<QueueRow?>(null) }
    var movingAt by remember { mutableIntStateOf(0) }
    var movedFrom by remember { mutableIntStateOf(0) }
    // Whether OK lifts a row rather than playing it, and the grips are shown.
    var rearranging by remember { mutableStateOf(false) }
    // The line that says how a song is moved: it slides in when rearranging is turned on and goes again at the next key.
    var hinting by remember { mutableStateOf(false) }
    val menu = rememberRowMenu()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (QueueParts.placeOf(parts, playing) - 2).coerceAtLeast(0))
    // The row the drawer gives the focus to, and a count that asks for it again each time it is set. The row is:
    // the playing row on opening, the moving row at each step, the next row after a removal.
    var focusAt by remember { mutableIntStateOf(playing.coerceAtLeast(0)) }
    var focusAsks by remember { mutableIntStateOf(0) }
    val focusRow = remember { FocusRequester() }

    fun focusOn(index: Int) {
        focusAt = index
        focusAsks += 1
    }

    // MediaController applies an edit to its own copy of the queue before the call returns, so reading
    // straight after an edit already shows it.
    fun reread() {
        val next = readQueue(player)
        // A queue that changes length under a move or an open menu was changed by something other than
        // this drawer. The move ends where it is and the menu closes.
        if (next.size != rows.size) {
            moving = null
            menu.close()
        }
        rows = next
        playing = player.currentMediaItemIndex
    }

    fun step(by: Int) {
        val row = moving ?: return
        val at = movingAt
        if (!player.holds(at, row)) {
            moving = null
            return
        }
        val to = at + by
        if (to !in 0 until player.mediaItemCount) return
        player.moveMediaItem(at, to)
        movingAt = to
        reread()
        focusOn(to)
    }

    // Lifts the row at [i] for Up and Down to carry: the menu's Move, and OK while rearranging.
    fun lift(i: Int, row: QueueRow) {
        if (rows.size > 1 && player.holds(i, row)) {
            moving = row
            movingAt = i
            movedFrom = i
        }
    }

    // Each entry checks again, at the moment it is picked, that the row is still where it was when the menu opened.
    // The glyphs are the desktop's queue menu's (PlaylistView: play, the minus for Remove) and its song menu's step
    // forward for Play next. The desktop has no Move entry. It has the grip that the moving row shows here.
    fun choicesFor(i: Int, row: QueueRow): List<MenuChoice> = buildList {
        add(MenuChoice(R.string.AlbumMenu_Play, Fa.PLAY) { if (player.holds(i, row)) player.seekTo(i, 0) })
        // Not on the playing row or the one already next.
        if (i != playing && i != playing + 1) {
            add(MenuChoice(R.string.Viz_QueuePlayNext, Fa.STEP_FORWARD) {
                val current = player.currentMediaItemIndex
                if (player.holds(i, row) && i != current && i != current + 1) {
                    // Taking a row from above the playing one moves the playing one up a place.
                    player.moveMediaItem(i, if (i < current) current else current + 1)
                    reread()
                }
            })
        }
        if (rows.size > 1) add(MenuChoice(R.string.Viz_QueueMove, Fa.GRIP) { lift(i, row) })
        add(MenuChoice(R.string.Viz_QueueRemove, Fa.MINUS) {
            if (player.holds(i, row)) {
                player.removeMediaItem(i)
                reread()
                // The removed row cannot take the focus back. The row now in its place gets it.
                focusOn(i)
            }
        })
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = reread()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Disabled while the drawer slides out: a Back pressed then belongs to the player under it.
    BackHandler(enabled = open && !menu.isOpen) {
        val row = moving
        when {
            row != null -> {
                if (movingAt != movedFrom && player.holds(movingAt, row) && movedFrom < player.mediaItemCount) {
                    player.moveMediaItem(movingAt, movedFrom)
                    focusOn(movedFrom)
                }
                moving = null
                reread()
            }
            rearranging -> {
                rearranging = false
                hinting = false
            }
            else -> onClose()
        }
    }

    // Runs on opening (count 0), on each ask, and when the drawer opens again while it was sliding out.
    // It does not run while the drawer leaves.
    LaunchedEffect(focusAsks, open) {
        if (!open || rows.isEmpty()) return@LaunchedEffect
        val at = focusAt.coerceIn(0, rows.lastIndex)
        focusAt = at
        val place = QueueParts.placeOf(parts, at).coerceAtLeast(0)
        // A heading over the row comes into view with it.
        if (place > 0 && parts[place - 1] !is QueuePart.Song) list.keepInView(place - 1)
        list.keepInView(place)
        runCatching { focusRow.requestFocus() }
    }

    val spot = remember { FocusSpot() }
    val closeKey = remember { FocusRequester() }
    // Slides in from one drawer width to the left and fades in, as the picture is pushed aside (PaneGlide), and slides
    // back out the same way once it is closed. The player keeps it composed until [onGone].
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    val gone by rememberUpdatedState(onGone)
    LaunchedEffect(open) {
        enter.animateTo(if (open) 1f else 0f, androidx.compose.animation.core.tween(DRAWER_GLIDE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        if (!open) gone()
    }
    val chain = rememberBlurChain()
    // The still fades in over the sharp room when it is made after the drawer has begun to slide in: on the first
    // opening, or on a new track's colours. Drawn at once when it is ready.
    val stillIn = remember { androidx.compose.animation.core.Animatable(if (room.blurred != null) 1f else 0f) }
    LaunchedEffect(room.blurred != null) {
        if (room.blurred != null) stillIn.animateTo(1f, androidx.compose.animation.core.tween(ROOM_FADE_MS))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The player's own size, in the pixels it is drawn at, which the card's offsets below are in too.
        val playerWide = constraints.maxWidth
        val playerHigh = constraints.maxHeight
        LaunchedEffect(roomLook, playerWide, playerHigh) { room.make(roomLook, playerWide, playerHigh) }
        // As wide as the desktop's drawer: 0.36 of the screen, limited to 280 to 460 drawer units, the side menu's
        // margin included, so the picture pushed by half of it ([PICTURE_PUSH]) is centred in what is left.
        val width = (maxWidth * 0.36f).coerceIn(280.ds, 460.ds)
        val shape = RoundedCornerShape(DRAWER_CORNER)
        Box(
            Modifier
                .graphicsLayer {
                    translationX = -(1f - enter.value) * size.width
                    alpha = (enter.value * 1.4f).coerceAtMost(1f)
                }
                .width(width)
                .fillMaxHeight()
                .padding(start = DRAWER_MARGIN, top = DRAWER_MARGIN, bottom = DRAWER_MARGIN)
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    // The rearrange key turns the hint on as its OK comes up, so the next key down is a new press.
                    if (hinting && key.action == KeyEvent.ACTION_DOWN) hinting = false
                    if (key.keyCode == KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                    if (moving == null) {
                        // Right with nothing further right closes the drawer, as it closes the side menu.
                        if (key.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && key.action == KeyEvent.ACTION_DOWN) {
                            onClose()
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    // While a row moves, the move handles every key except Back.
                    if (key.action == KeyEvent.ACTION_DOWN) {
                        when (key.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> step(-1)
                            KeyEvent.KEYCODE_DPAD_DOWN -> step(+1)
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                                if (key.repeatCount == 0) moving = null
                        }
                    }
                    true
                }
                .clip(shape)
                // The room's blurred still, then the picture under the card, blurred, both where the card stands in
                // the player: the card is laid out at the margin and slid by its layer, which they must follow, so it
                // reads the slide itself and is drawn again on every frame of it. The picture is recorded at the
                // player's own origin, as the drawer is, and the still is the player's size.
                .drawBehind {
                    val margin = DRAWER_MARGIN.toPx()
                    val slide = -(1f - enter.value) * (size.width + margin)
                    room.blurred?.let { still ->
                        drawImage(
                            still,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(still.width, still.height),
                            dstOffset = IntOffset(-(margin + slide).roundToInt(), -margin.roundToInt()),
                            dstSize = IntSize(playerWide, playerHigh),
                            alpha = stillIn.value,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    drawLiveBlur(chain, picture, Offset(margin + slide, margin), local = 1f)
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    // Frosted rather than smoked: the room behind is soft already, so a blur alone showed no glass.
                    // A milky tint and a grain make it read as frosted glass; the dim is lighter than the side
                    // menu's so the frost shows, and the white words stay readable on it.
                    .background(DRAWER_FROST)
                    .background(DRAWER_DIM)
                    .frosted()
                    .border(1.px, DRAWER_RIM, shape),
            )
            SpotScope(spot, Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(start = 16.s, top = 20.s, end = 16.s)) {
                    DrawerHead(
                        rearranging = rearranging,
                        hinting = hinting,
                        onRearrange = {
                            rearranging = !rearranging
                            hinting = rearranging
                        },
                        onClose = onClose,
                        keys = Modifier.focusProperties { canFocus = open },
                        closeKey = Modifier.focusRequester(closeKey),
                    )
                    LazyColumn(
                        state = list,
                        contentPadding = PaddingValues(top = 14.ds, bottom = 28.px),
                        verticalArrangement = Arrangement.spacedBy(3.ds),
                    ) {
                        items(
                            parts,
                            key = { part ->
                                when (part) {
                                    is QueuePart.Song -> rows[part.at].key
                                    is QueuePart.UpNextHeading -> "up next over " + rows[part.before].key
                                    is QueuePart.ResumeHeading -> "then " + rows[part.before].key
                                }
                            },
                            contentType = { it::class },
                        ) { part ->
                            when (part) {
                                is QueuePart.UpNextHeading -> Text(
                                    stringResource(R.string.Viz_Queue),
                                    style = ui(26.ts, FontWeight.SemiBold),
                                    color = Ink.Words,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 10.ds, top = 16.ds, bottom = 4.ds),
                                )
                                is QueuePart.ResumeHeading -> Text(
                                    stringResource(R.string.Tv_QueueAfterUpNext),
                                    style = ui(26.ts, FontWeight.SemiBold),
                                    color = Ink.Artist,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 10.ds, top = 14.ds, bottom = 2.ds),
                                )
                                is QueuePart.Song -> {
                                    val i = part.at
                                    val row = rows[i]
                                    // Follows the row when a move carries it, so the menu gives the focus back to the
                                    // row it was opened on, wherever Play next moved it.
                                    val self = remember { FocusRequester() }
                                    QueueLine(
                                        row = row,
                                        isPlaying = i == playing,
                                        isMoving = moving != null && i == movingAt,
                                        grip = rearranging,
                                        images = images,
                                        onClick = {
                                            when {
                                                moving != null -> Unit
                                                rearranging -> lift(i, row)
                                                player.holds(i, row) -> player.seekTo(i, 0)
                                            }
                                        },
                                        onMenu = { if (moving == null) menu.open(row.title, choicesFor(i, row), returnTo = self) },
                                        modifier = Modifier
                                            .focusRequester(self)
                                            // A drawer that slides out has no focus stops: Left from the player's keys must not land in it.
                                            .focusProperties { canFocus = open }
                                            .then(if (i == 0) Modifier.focusProperties { up = closeKey } else Modifier)
                                            .then(if (i == focusAt) Modifier.focusRequester(focusRow) else Modifier),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // The row menu reads the drawer's focus spot, not the screens': it is the drawer's row that opened it, so that row
    // is drawn bare under it and kept sharp in its own bounds ([FocusSpot.quiet]). It is not inside the card, which
    // clips to the drawer, but over the whole player, in a scope of its own on the same spot.
    SpotScope(spot, Modifier.fillMaxSize()) { RowMenu(menu) }
}

/**
 * The drawer's head, as the side menu's: "Now playing" (26·s SemiBold, the settings panel's title size) and, at the
 * right, the rearrange key and the close key, boxed as the side menu's keys are (52·s). The rearrange key is lit
 * while rearranging is on, and a line under the title then says how a song is moved. The desktop's drawer has
 * Playlists and Albums tabs beside the title (PegasusChrome.Browse.cs); the television's holds the queue alone, so
 * it names that and nothing else.
 */
@Composable
private fun DrawerHead(rearranging: Boolean, hinting: Boolean, onRearrange: () -> Unit, onClose: () -> Unit, keys: Modifier, closeKey: Modifier) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.Viz_BrowseQueue),
                style = ui(26.ts, FontWeight.SemiBold),
                color = Ink.Words,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.ds, end = 10.s),
            )
            val rearrangeLabel = stringResource(R.string.Rpm_Queue_Rearrange)
            DrawerKey(
                on = rearranging,
                onClick = onRearrange,
                modifier = keys.size(52.s).semantics { contentDescription = rearrangeLabel },
            ) {
                Glyph(Fa.ARROWS_V, 20.ts, if (rearranging) Ink.Words else Ink.Key)
            }
            Spacer(Modifier.width(8.s))
            val closeLabel = stringResource(R.string.Viz_QueueClose)
            DrawerKey(on = false, onClick = onClose, modifier = keys.then(closeKey).size(52.s).semantics { contentDescription = closeLabel }) {
                Glyph(Fa.CLOSE, 20.ts, Ink.Key)
            }
        }
        // Slides down from under the title and fades in; on the next key it slides back up as it fades.
        AnimatedVisibility(
            visible = hinting,
            enter = expandVertically(tween(HINT_MS), expandFrom = Alignment.Top) +
                slideInVertically(tween(HINT_MS)) { -it / 2 } + fadeIn(tween(HINT_MS)),
            exit = shrinkVertically(tween(HINT_MS), shrinkTowards = Alignment.Top) +
                slideOutVertically(tween(HINT_MS)) { -it / 2 } + fadeOut(tween(HINT_MS)),
        ) {
            Text(
                stringResource(R.string.Tv_QueueRearrangeHint),
                style = ui(15.ts),
                color = Ink.Artist,
                modifier = Modifier.padding(start = 10.ds, top = 8.s),
            )
        }
    }
}

/**
 * The side menu's boxed key (PegasusMenuButton: `#14FFFFFF` with a 1 px `#B3FFFFFF` rim, corner 7·s, `#33FFFFFF`
 * when focused). A key that is [on] is filled `#4DFFFFFF` with the lit rim, so a mode it turns on shows from across
 * the room whether or not the focus is on it.
 */
@Composable
private fun DrawerKey(on: Boolean, onClick: () -> Unit, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(7.s)
    var lit by remember { mutableStateOf(false) }
    DeskButton(
        onClick = onClick,
        corner = 7.s,
        modifier = modifier
            .onFocusChanged { lit = it.isFocused }
            .background(
                when {
                    on -> KEY_ON
                    lit -> KEY_LIT
                    else -> KEY_FILL
                },
                shape,
            )
            .border(1.px, if (on || lit) KEY_RIM_LIT else KEY_RIM, shape),
        content = content,
    )
}

private val KEY_FILL = Color(0x14FFFFFF)
private val KEY_LIT = Color(0x33FFFFFF)
private val KEY_ON = Color(0x4DFFFFFF)
private val KEY_RIM = Color(0xB3FFFFFF)
private val KEY_RIM_LIT = Color(0xE6FFFFFF)

/** The side menu's glass: its corner, its margin from the screen's edges, and the light glass's rim
 * (LightSmokeRimBrush), lighter at the top and the bottom than in the middle. */
private val DRAWER_CORNER = 7.s
private val DRAWER_MARGIN = 10.s
private val DRAWER_FROST = Color(0x33FFFFFF)
private val DRAWER_DIM = Color(0x1A000000)
private val DRAWER_RIM = Brush.verticalGradient(0f to Ink.SmokeRimTop, 0.5f to Ink.SmokeRimMiddle, 1f to Ink.SmokeRimBottom)

/** How long the drawer takes to slide in or out. The player's push of the picture takes the same time. */
const val DRAWER_GLIDE_MS = 250

/** How long the rearrange hint and the rows' grips take to slide in or out. */
private const val HINT_MS = 260

/** How long the room's blurred still takes to fade in over the sharp room when it comes after the drawer. */
private const val ROOM_FADE_MS = 200

/** Scrolls the list just far enough that the item at [index] is completely on screen. */
private suspend fun LazyListState.keepInView(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    when {
        item == null -> scrollToItem(index)
        item.offset < info.viewportStartOffset -> animateScrollBy((item.offset - info.viewportStartOffset).toFloat())
        item.offset + item.size > info.viewportEndOffset - info.afterContentPadding ->
            animateScrollBy((item.offset + item.size - info.viewportEndOffset + info.afterContentPadding).toFloat())
    }
}

/**
 * One row of the queue (PegasusChrome.xaml.cs, PaintQueueRow): the grip (12, at 0.42) while [grip] is shown, the
 * sleeve (34 square, corner 3, on `#22FFFFFF`), the title (14) with the E, and the length (12, at 0.55), all in
 * drawer units, padded 10 by 8 with corner 6. The playing row is lit `#1FFFFFFF` and SemiBold; the
 * others are at 0.70; the row being moved is at 0.86 with its grip whole, shown whether or not [grip] is.
 */
@Composable
private fun QueueLine(
    row: QueueRow,
    isPlaying: Boolean,
    isMoving: Boolean,
    grip: Boolean,
    images: ImageLoader,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(6.ds)
    DeskButton(
        onClick = onClick,
        corner = 6.ds,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .heldOkMenu(onMenu)
            .background(if (isPlaying) Color(0x1FFFFFFF) else Color.Transparent, shape)
            .padding(horizontal = 10.ds, vertical = 8.ds),
    ) {
        Row(
            Modifier.alpha(
                when {
                    isMoving -> 0.86f
                    isPlaying -> 1f
                    else -> 0.70f
                },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The grip opens a space at the row's left and fades in as it slides into it, and leaves the same way.
            AnimatedVisibility(
                visible = grip || isMoving,
                enter = expandHorizontally(tween(HINT_MS), expandFrom = Alignment.Start) +
                    slideInHorizontally(tween(HINT_MS)) { -it } + fadeIn(tween(HINT_MS)),
                exit = shrinkHorizontally(tween(HINT_MS), shrinkTowards = Alignment.Start) +
                    slideOutHorizontally(tween(HINT_MS)) { -it } + fadeOut(tween(HINT_MS)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Fa.GRIP, 12.tds, Color.White.copy(alpha = if (isMoving) 1f else 0.42f))
                    Spacer(Modifier.width(10.ds))
                }
            }
            Box(Modifier.size(34.ds).clip(RoundedCornerShape(3.ds)).background(Ink.Well)) {
                if (row.cover != null) {
                    CoverImage(row.cover, images, ContentScale.Crop, Modifier.fillMaxSize(), onShown = {})
                }
            }
            Spacer(Modifier.width(10.ds))
            Text(
                row.title,
                style = ui(14.tds, if (isPlaying) FontWeight.SemiBold else FontWeight.Normal),
                color = Ink.Words,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.durationMs > 0) {
                Spacer(Modifier.width(8.ds))
                Text(clockOf(row.durationMs), style = ui(12.tds), color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}
