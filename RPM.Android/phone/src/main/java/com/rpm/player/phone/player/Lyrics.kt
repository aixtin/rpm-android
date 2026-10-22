package com.rpm.player.phone.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpm.player.core.cloud.Shelf
import com.rpm.player.core.lyrics.LrcLib
import com.rpm.player.core.lyrics.LyricScore
import com.rpm.player.core.lyrics.LyricSheet
import com.rpm.player.core.lyrics.MeasuredLyrics
import com.rpm.player.core.playback.NowPlaying
import com.rpm.player.core.playback.TrackRef
import com.rpm.player.phone.design.system
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The words of what is playing: the sheet the owner chose on a desktop, kept in the cloud, or else the one LRCLIB gives.
 * It is the iPhone's `LyricsLookup` (RPMSession/LyricsLookup.swift).
 *
 * **The player asks only while it is shown**, so ordinary listening sends no record name to LRCLIB. It asks as it
 * appears, because the lyrics key needs to know whether there are lyrics before it is pressed.
 *
 * Where a desktop worked out how far the sheet is off the record, the sheet is moved by that much, so a line lights when
 * the record sings it ([MeasuredLyrics.offsetFor]). The TV does the same; the iPhone does not read the offset yet.
 */
@Stable
class LyricsLookup(private val lrcLib: LrcLib, private val measured: MeasuredLyrics) {
    /** The words, or [LyricSheet.EMPTY] where there are none. */
    var sheet by mutableStateOf(LyricSheet.EMPTY)
        private set

    /** Whether an answer is still coming. */
    var looking by mutableStateOf(false)
        private set

    /** The record the sheet belongs to: its media id, then the names and length sent to LRCLIB. */
    private var asked: String? = null

    /**
     * Reads the words for [mediaId], once. [seconds] is the catalogue's length, not the player's: LRCLIB matches on
     * length, and the player reports nothing until the track has started.
     */
    suspend fun read(mediaId: String?, artist: String?, title: String?, album: String?, seconds: Double) {
        val track = mediaId?.let(TrackRef::parse)
        if (track == null || title.isNullOrBlank()) {
            sheet = LyricSheet.EMPTY
            asked = null
            looking = false
            return
        }
        val key = "$mediaId|${artist.orEmpty()}|$title|${album.orEmpty()}|${seconds.toInt()}"
        if (key == asked) return
        // Set before the wait, so a second call while the first waits does not look up the same record twice.
        asked = key
        sheet = LyricSheet.EMPTY
        looking = true

        // The owner's own choice comes first. A track the cloud does not know, a sheet with no timings, or a failed
        // read all fall back to LRCLIB.
        var found = withContext(Dispatchers.IO) { measured.chosenSheet(track) } ?: LyricSheet.EMPTY
        if (asked != key) return
        if (!found.isSynced) found = withContext(Dispatchers.IO) { lrcLib.sheet(artist, title, album, seconds) }
        if (asked != key) return
        if (found.isSynced && track.shelf == Shelf.Own) {
            val answer = withContext(Dispatchers.IO) { measured.read(track) }
            if (asked != key) return
            found = MeasuredLyrics.shifted(found, MeasuredLyrics.offsetFor(answer, found))
        }
        sheet = found
        looking = false
    }
}

/** How many lines either side of the sung line are drawn at all. With [fade], this is the whole mask. */
private const val REACH = 3

/** The gap between lines, as a share of the type's own size. */
private const val LEADING = 0.62f

/**
 * The lyrics as they are sung, scrolling so the sung line stays in the middle. It is the iPhone's `LyricView`
 * (RPMUI/LyricView.swift): it draws a score ([LyricScore]), not a list of lines, so a break the writer did not mark is
 * still a break, and every opacity changes only when the playhead reaches a new piece.
 *
 * **The playhead is not read on every frame.** The piece being sung is worked out when the record reaches the next
 * piece's moment, by waiting for that moment, so the list recomposes once a line. Only the dots of a break, and of the
 * intro, follow the playhead, four times a second, as on iPhone.
 */
@Composable
internal fun LyricView(sheet: LyricSheet, now: NowPlaying) {
    val score = remember(sheet) { LyricScore.of(sheet) }
    val at by produceState(score.index(now.positionAt(SystemClock.uptimeMillis()) / 1000.0), score, now) {
        while (true) {
            val seconds = now.positionAt(SystemClock.uptimeMillis()) / 1000.0
            val index = score.index(seconds)
            value = index
            if (!now.advancing) break
            val next = score.parts.getOrNull((index ?: -1) + 1)?.seconds ?: break
            delay(((next - seconds) * 1000).roundToInt().toLong().coerceAtLeast(16))
        }
    }
    val list = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = (maxWidth.value * 0.055f).coerceIn(16f, 26f)
        val half = maxHeight / 2
        val heightPx = with(density) { maxHeight.toPx() }

        // The sung line goes to the middle: straight there when the view opens, and with an easing scroll as the song
        // moves on. A line far off screen (after a seek) is jumped to.
        LaunchedEffect(at, score) {
            val target = at ?: 0
            if (score.parts.isEmpty()) return@LaunchedEffect
            val seen = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (seen == null) {
                list.scrollToItem(target)
                val placed = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
                list.scrollBy(placed.size / 2f)
                return@LaunchedEffect
            }
            val centre = seen.offset + seen.size / 2f - list.layoutInfo.viewportStartOffset
            val delta = centre - heightPx / 2
            if (abs(delta) > 1f) list.animateScrollBy(delta, tween(450, easing = FastOutSlowInEasing))
        }

        LazyColumn(
            state = list,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = half),
            verticalArrangement = Arrangement.spacedBy((size * LEADING).dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(score.parts, key = { _, part -> part.id }, contentType = { _, part -> part.isRest }) { _, part ->
                ScoreRow(part, score, at, size, now)
            }
        }

        // The intro: nothing is sung yet, so the sheet is blank, and the title and artist show over it with the dots
        // filling towards the first line. It goes once, when the singing starts.
        AnimatedVisibility(at == null, enter = fadeIn(tween(350)), exit = fadeOut(tween(350)), modifier = Modifier.align(Alignment.Center)) {
            Opening(sheet, now, size)
        }
    }
}

/** One piece of the score: a line of words, or the dots of the rest that closes a stretch. */
@Composable
private fun ScoreRow(part: LyricScore.Part, score: LyricScore, at: Int?, size: Float, now: NowPlaying) {
    val here = at?.let { score.parts[it] }
    val distance = abs(part.id - (at ?: 0))
    val sung = part.id == at
    // While a rest plays, only its dots show. Only the stretch being sung shows: a reach on either side would pass
    // straight through a break and show the next verse during the solo.
    val resting = here?.isRest == true
    val ours = part.section == here?.section
    val wanted = when (part.piece) {
        is LyricScore.Piece.Rest -> if (ours) fade(distance, started = true) else 0f
        is LyricScore.Piece.Words -> if (ours && !resting) fade(distance, started = true) else 0f
    }
    val alpha by animateFloatAsState(wanted, tween(350, easing = FastOutSlowInEasing), label = "line")
    when (val piece = part.piece) {
        is LyricScore.Piece.Rest -> Box(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
            if (wanted > 0f || alpha > 0f) Dots(now) { seconds -> through(seconds, piece.from, piece.until) }
        }
        is LyricScore.Piece.Words -> BasicText(
            piece.line.text,
            Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
            // The same weight on every line, sung or not: a bolder sung line would wrap again and move the lines under
            // it. Only the brightness shows which line is sung.
            style = system(size, FontWeight.SemiBold).copy(color = if (sung) Color.White else LINE, textAlign = TextAlign.Center),
        )
    }
}

/** The colour of a line that is not being sung. */
private val LINE = Color(0xFFEAEAEA)

/** The opacity by distance from the sung line: 1, 0.42, 0.24, 0.12, then nothing. Nothing before the first line. */
private fun fade(distance: Int, started: Boolean): Float {
    if (!started) return 0f
    return when (distance) {
        0 -> 1f
        1 -> 0.42f
        2 -> 0.24f
        else -> if (distance <= REACH) 0.12f else 0f
    }
}

/** How far through a rest the record is at [seconds], from 0 to 1. A rest with no end never fills. */
private fun through(seconds: Double, from: Double, until: Double): Float {
    val run = until - from
    if (!run.isFinite() || run <= 0) return 0f
    return ((seconds - from) / run).coerceIn(0.0, 1.0).toFloat()
}

/** The screen before the first line: the title and artist, and how much of the intro is left. */
@Composable
private fun Opening(sheet: LyricSheet, now: NowPlaying, size: Float) {
    Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(now.title, maxLines = 3, style = system(size, FontWeight.SemiBold).copy(color = Color.White, textAlign = TextAlign.Center))
            BasicText(now.artist, maxLines = 3, style = system(size * 0.82f, FontWeight.Medium).copy(color = LINE.copy(alpha = 0.66f), textAlign = TextAlign.Center))
        }
        val first = sheet.lines.firstOrNull()?.seconds ?: 0.0
        Dots(now) { seconds -> if (first <= 0) 1f else (seconds / first).coerceIn(0.0, 1.0).toFloat() }
    }
}

/** The opacity of a dot before its turn. All three show from the start; the fill shows how many are reached. */
private const val DOT_REST = 0.16f

/**
 * The three dots of a break, filling in across it, each over its own third, so the three read as one bar filling. The
 * playhead is read four times a second, and the fill eases linearly between readings, as on iPhone, so it does not step.
 */
@Composable
private fun Dots(now: NowPlaying, share: (Double) -> Float) {
    var through by remember { mutableFloatStateOf(share(now.positionAt(SystemClock.uptimeMillis()) / 1000.0)) }
    LaunchedEffect(now) {
        while (true) {
            through = share(now.positionAt(SystemClock.uptimeMillis()) / 1000.0)
            if (!now.advancing) break
            delay(250)
        }
    }
    val shown by animateFloatAsState(through, tween(250, easing = LinearEasing), label = "dots")
    Canvas(Modifier.size(41.dp, 7.dp)) {
        val r = size.height / 2
        val step = (size.width - size.height) / 2
        for (dot in 0 until 3) {
            val part = (shown * 3 - dot).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = DOT_REST + (1 - DOT_REST) * part), r, androidx.compose.ui.geometry.Offset(r + step * dot, r))
        }
    }
}
