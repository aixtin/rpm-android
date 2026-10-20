package com.rpm.player.tv.ui

import com.rpm.player.core.playback.EXTRA_COVER_KEYS
import com.rpm.player.core.playback.TrackRef
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.Player
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.library.Cover

/**
 * One row of the queue as the controller holds it. [key] is the media id and which copy of it this is
 * (a track can be queued twice), so a row keeps its key when a move carries it past other tracks.
 */
private data class QueueRow(val key: String, val mediaId: String, val title: String, val durationMs: Long, val cover: Cover?)

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
        )
    }
}

/** Whether the controller still holds [row] at [index]. Every edit asks this at the moment of the key. */
private fun Player.holds(index: Int, row: QueueRow): Boolean =
    index in 0 until mediaItemCount && getMediaItemAt(index).mediaId == row.mediaId

/**
 * Pegasus's queue drawer (PegasusChrome.xaml, the drawer): a panel of the glass darkened `#B0000000` down
 * the left of the player, 0.36 of the screen wide. It holds the queue, with the playing row lit and focused
 * when the drawer opens. OK plays a row. A held OK or the Menu key opens the row's menu (Play, Play next, Move,
 * Remove from queue). Move lifts the row: Up and Down carry it one place, OK places it, and Back puts it back
 * where it started. Back closes the menu, then a move, then the drawer. Right also closes the drawer.
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
fun QueueDrawer(player: Player, images: ImageLoader, backdrop: Backdrop, open: Boolean, onClose: () -> Unit, onGone: () -> Unit) {
    var rows by remember { mutableStateOf(readQueue(player)) }
    var playing by remember { mutableStateOf(player.currentMediaItemIndex) }
    // The row being moved, the index it is at now and the index it started from. The row is null when nothing moves.
    var moving by remember { mutableStateOf<QueueRow?>(null) }
    var movingAt by remember { mutableIntStateOf(0) }
    var movedFrom by remember { mutableIntStateOf(0) }
    val menu = rememberRowMenu()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (playing - 2).coerceAtLeast(0))
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
        if (rows.size > 1) {
            add(MenuChoice(R.string.Viz_QueueMove, Fa.GRIP) {
                if (player.holds(i, row)) {
                    moving = row
                    movingAt = i
                    movedFrom = i
                }
            })
        }
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
        if (row != null) {
            if (movingAt != movedFrom && player.holds(movingAt, row) && movedFrom < player.mediaItemCount) {
                player.moveMediaItem(movingAt, movedFrom)
                focusOn(movedFrom)
            }
            moving = null
            reread()
        } else {
            onClose()
        }
    }

    LaunchedEffect(Unit) { backdrop.capture() }

    // Runs on opening (count 0), on each ask, and when the drawer opens again while it was sliding out.
    // It does not run while the drawer leaves.
    LaunchedEffect(focusAsks, open) {
        if (!open || rows.isEmpty()) return@LaunchedEffect
        val at = focusAt.coerceIn(0, rows.lastIndex)
        focusAt = at
        list.keepInView(at)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // As wide as the desktop's drawer: 0.36 of the screen, limited to 280 to 460 drawer units.
        val width = (maxWidth * 0.36f).coerceIn(280.ds, 460.ds)
        GlassPanel(
            backdrop = backdrop,
            corner = 0.px,
            modifier = Modifier
                .graphicsLayer {
                    translationX = -(1f - enter.value) * size.width
                    alpha = (enter.value * 1.4f).coerceAtMost(1f)
                }
                .width(width)
                .fillMaxHeight()
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
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
                },
        ) {
            // The drawer's own dark layer over the glass, its right edge, and a highlight down that edge.
            Box(Modifier.matchParentSize().background(Color(0xB0000000)))
            Box(Modifier.align(Alignment.CenterEnd).width(1.px).fillMaxHeight().background(Color(0x26FFFFFF)))
            Box(
                Modifier.align(Alignment.CenterEnd).width(1.px).fillMaxHeight()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0x26FFFFFF), 1f to Color.Transparent)),
            )
            SpotScope(spot, Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(top = 12.px, bottom = 12.px)) {
                    DrawerHead(onClose, Modifier.focusRequester(closeKey).focusProperties { canFocus = open })
                    LazyColumn(
                        state = list,
                        contentPadding = PaddingValues(start = 18.px, end = 18.px, top = 26.ds, bottom = 28.px),
                        verticalArrangement = Arrangement.spacedBy(3.ds),
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.key }) { i, row ->
                            // Follows the row when a move carries it, so the menu gives the focus back to the
                            // row it was opened on, wherever Play next moved it.
                            val self = remember { FocusRequester() }
                            QueueLine(
                                row = row,
                                isPlaying = i == playing,
                                isMoving = moving != null && i == movingAt,
                                images = images,
                                onClick = { if (moving == null && player.holds(i, row)) player.seekTo(i, 0) },
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

    RowMenu(menu)
}

/**
 * The drawer's head: "Now playing" (white SemiBold, 12 drawer units) and the round close key at the right. The
 * desktop's drawer has Playlists and Albums tabs beside it (PegasusChrome.Browse.cs); the television's holds the
 * queue alone, so it names that and nothing else.
 */
@Composable
private fun DrawerHead(onClose: () -> Unit, modifier: Modifier) {
    Row(Modifier.fillMaxWidth().padding(start = 8.px, end = 10.px, bottom = 6.px), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.Viz_BrowseQueue),
            style = ui(12.tds, FontWeight.SemiBold),
            color = Ink.Words,
            modifier = Modifier.weight(1f).padding(horizontal = 8.ds, vertical = 3.ds),
        )
        DeskButton(onClick = onClose, corner = 13.ds, modifier = modifier.size(26.ds)) {
            Glyph(Fa.CLOSE, 11.tds, Ink.Dim)
        }
    }
}

/** How long the drawer takes to slide in or out. The player's push of the picture takes the same time. */
const val DRAWER_GLIDE_MS = 250

/** Scrolls the list just far enough that the row at [index] is completely on screen. */
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
 * One row of the queue (PegasusChrome.xaml.cs, PaintQueueRow): the grip (12, at 0.42), the sleeve (34
 * square, corner 3, on `#22FFFFFF`), the title (14) with the E, and the length (12, at 0.55), all in
 * drawer units, padded 10 by 8 with corner 6. The playing row is lit `#1FFFFFFF` and SemiBold; the
 * others are at 0.70; the row being moved is at 0.86 with its grip whole.
 */
@Composable
private fun QueueLine(
    row: QueueRow,
    isPlaying: Boolean,
    isMoving: Boolean,
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
            Glyph(Fa.GRIP, 12.tds, Color.White.copy(alpha = if (isMoving) 1f else 0.42f))
            Spacer(Modifier.width(10.ds))
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
