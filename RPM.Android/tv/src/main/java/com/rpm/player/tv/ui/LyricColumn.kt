package com.rpm.player.tv.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.rpm.player.core.lyrics.KaraokeLight
import com.rpm.player.core.lyrics.LyricScore
import com.rpm.player.core.lyrics.LyricSheet
import com.rpm.player.core.lyrics.LyricTiming
import com.rpm.player.core.lyrics.WordSpan
import com.rpm.player.core.playback.NowPlaying

/**
 * The lyrics as they are sung, kept on the middle of the column. It is the iPhone's `LyricView`
 * (RPMUI/LyricView.swift).
 *
 * The sheet is cut into stretches of words and rests ([LyricScore]). Only the stretch being sung is shown. Each
 * line fades by its distance from the sung line (1, 0.42, 0.24, 0.12, then nothing). The sung line is white and
 * the others are `#EAEAEA`. In a rest (between verses, or an instrumental that nobody marked) the words go and
 * three dots fill in across the rest. The lines of the next stretch appear only when it starts. Before the first
 * line (an instrumental intro) only the same dots are shown, counting in to it: the title and artist used to stand
 * there too, which read as part of the lyrics and repeated the lines under the record. While the next track's sheet
 * is still being read ([LyricSheet.EMPTY]) the dots stand faint, unfilled, so the column says lyrics are coming.
 *
 * With [karaoke] the sung line lights as it is sung: a soft edge of light wipes through each word, letter by
 * letter, as on the desktop's Pegasus ([SweptLine], [com.rpm.player.core.lyrics.KaraokeLight]). The timing is
 * taken from the moments that a desktop measured off the record, where it has them ([measured],
 * [com.rpm.player.core.lyrics.MeasuredLyrics]). Otherwise the line's time is shared between the words by
 * length ([LyricTiming], the desktop's sweep).
 *
 * The playhead is read once a frame, carried forward from the controller's last reading. The piece in front
 * changes only when the playhead reaches a new one.
 */
@Composable
fun LyricColumn(sheet: LyricSheet, nowPlaying: NowPlaying?, karaoke: Boolean, measured: List<com.rpm.player.core.lyrics.MeasuredLine>, modifier: Modifier) {
    val playing by rememberUpdatedState(nowPlaying)
    val score = remember(sheet) { LyricScore.of(sheet) }
    var at by remember(sheet) { mutableIntStateOf(-1) }
    // Seconds into the song, updated once a frame. Only things that move within a piece read it, the dots and the
    // karaoke line, and only when they are drawn, so a frame redraws them and recomposes nothing.
    var now by remember(sheet) { mutableDoubleStateOf(0.0) }
    // Each sung piece's measured words, where the record has been measured; worked out once per answer.
    val heard = remember(score, measured, karaoke) {
        if (!karaoke) emptyList() else score.parts.map { part ->
            (part.piece as? LyricScore.Piece.Words)?.let { w ->
                com.rpm.player.core.lyrics.MeasuredLyrics.forLine(measured, w.line.text, w.line.seconds)
            }
        }
    }
    val heardNow by rememberUpdatedState(heard)

    LaunchedEffect(score) {
        while (true) {
            withFrameMillis { }
            val seconds = (playing?.positionAt(SystemClock.uptimeMillis()) ?: 0L) / 1000.0
            now = seconds
            val piece = held(score.index(seconds) ?: -1, seconds, heardNow)
            if (piece != at) at = piece
        }
    }

    val list = rememberLazyListState()
    LaunchedEffect(at) { list.animateScrollToItem(at.coerceAtLeast(0)) }

    BoxWithConstraints(modifier) {
        val size = (maxHeight * 0.030f).coerceIn(15.px, 34.px)
        val lineHeight = size * 1.33f
        val here = score.parts.getOrNull(at)
        val resting = here?.isRest == true
        LazyColumn(
            state = list,
            userScrollEnabled = false,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(size * 0.62f),
            // The item scrolled to starts half a line above the middle, so a one-line lyric is centred.
            contentPadding = PaddingValues(top = maxHeight / 2 - lineHeight / 2, bottom = maxHeight / 2),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(score.parts, key = { it.id }) { part ->
                val ours = here != null && part.section == here.section
                val distance = kotlin.math.abs(part.id - at)
                when (val piece = part.piece) {
                    is LyricScore.Piece.Rest -> {
                        val alpha by animateFloatAsState(if (ours) fade(distance) else 0f, tween(350), label = "rest")
                        Box(Modifier.fillMaxWidth().padding(vertical = size * 0.2f).graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
                            Dots(through = { through(now, piece.from, piece.until) }, dot = size * 0.32f)
                        }
                    }
                    is LyricScore.Piece.Words -> {
                        val sung = part.id == at
                        val alpha by animateFloatAsState(if (ours && !resting) fade(distance) else 0f, tween(350), label = "line")
                        val style = ui(size.value.sp, FontWeight.SemiBold)
                        if (sung && karaoke) {
                            val next = score.parts.getOrNull(part.id + 1)?.seconds ?: Double.POSITIVE_INFINITY
                            // Use the measured timing where a desktop measured this line, else share the time across it.
                            val spans = remember(part.id, measured) {
                                com.rpm.player.core.lyrics.MeasuredLyrics.forLine(measured, piece.line.text, piece.line.seconds)
                                    ?: LyricTiming.sweep(piece.line.text, piece.line.seconds, next)
                            }
                            SweptLine(
                                text = piece.line.text,
                                spans = spans,
                                now = { now },
                                style = style.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
                            )
                        } else {
                            Text(
                                text = piece.line.text,
                                style = style,
                                color = if (sung) Color.White else SUNG_NOT,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
                            )
                        }
                    }
                }
            }
        }
        // The intro: nothing is being sung yet, so only the dots, counting in to the first line, as in a rest. A sheet
        // not read yet has no first line, and its dots stay faint.
        val opening by animateFloatAsState(if (at < 0) 1f else 0f, tween(350), label = "opening")
        if (opening > 0f) {
            Box(Modifier.align(Alignment.Center).graphicsLayer { alpha = opening }) {
                val first = sheet.lines.firstOrNull()?.seconds
                Dots(
                    through = { if (first == null) 0f else if (first <= 0.0) 1f else (now / first).toFloat().coerceIn(0f, 1f) },
                    dot = size * 0.32f,
                )
            }
        }
    }
}

/**
 * The piece to show at [seconds], given the piece that the time stamps say ([piece]). This is the previous piece
 * while its measured words are still being sung and the words of [piece] have not started. A stamp often comes
 * before the last word of the line before it, and moving on at the stamp would leave that word unlit.
 */
private fun held(piece: Int, seconds: Double, heard: List<List<WordSpan>?>): Int {
    if (piece < 1) return piece
    val before = heard.getOrNull(piece - 1) ?: return piece
    if (before.isEmpty() || before.last().to <= seconds) return piece
    val starts = heard.getOrNull(piece)?.firstOrNull()?.from
    return if (starts != null && starts <= seconds) piece else piece - 1
}

/** The opacity for a line, by its distance from the sung piece. */
private fun fade(distance: Int): Float = when (distance) {
    0 -> 1f
    1 -> 0.42f
    2 -> 0.24f
    3 -> 0.12f
    else -> 0f
}

/** How far through a rest the playhead is, from 0 to 1. A rest with no end never fills. */
private fun through(now: Double, from: Double, until: Double): Float {
    val run = until - from
    if (!run.isFinite() || run <= 0) return 0f
    return ((now - from) / run).toFloat().coerceIn(0f, 1f)
}

/** The three dots of a break, filling in across it. Each dot owns a third of the break, so together they fill like
 * one bar. A dot is faint before its turn ([DOT_REST]). [through] is read when the dots are drawn, once a frame. */
@Composable
private fun Dots(through: () -> Float, dot: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(dot * 1.4f)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(dot)
                    .graphicsLayer {
                        val share = (through() * 3 - i).coerceIn(0f, 1f)
                        alpha = DOT_REST + (1 - DOT_REST) * share
                    }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/**
 * The sung line for karaoke, lit as the desktop's Pegasus lights it: a soft edge of light wipes through each word
 * as it is sung, from [KaraokeLight.REST] up to white, letter by letter ([KaraokeLight]).
 *
 * It is drawn, not composed. [BasicText] lays the line out once and draws nothing itself. Each frame the draw reads
 * [now], works out every letter's strength and draws the laid-out text once per row, with a horizontal gradient
 * whose stops sit on the letters' centres. So nothing recomposes or re-lays-out while the line is sung (an
 * AnnotatedString per frame did both), and the Amiko's Mali draws one shaded text pass per row, with no offscreen
 * layer. The gradient also blends between letters, so the edge is smoother than the desktop's one colour a letter.
 */
@Composable
private fun SweptLine(text: String, spans: List<WordSpan>, now: () -> Double, style: TextStyle, modifier: Modifier) {
    val sweep = remember(text, spans) { Sweep(spans) }
    BasicText(
        text = text,
        style = style,
        onTextLayout = sweep::laid,
        modifier = modifier.drawWithContent { sweep.draw(this, now()) },
    )
}

/**
 * The geometry and the brushes of one [SweptLine]. Where each letter sits is read off the layout once, when it is
 * laid out; a frame only works out strengths and, when one has moved, builds a brush per row. A frame where nothing
 * moved (a rest between words, a held note, a line done) reuses the last brushes and allocates nothing.
 */
private class Sweep(private val spans: List<WordSpan>) {
    private val strength = FloatArray(KaraokeLight.letters(spans))
    private val drawn = FloatArray(strength.size) { -1f }
    private var layout: TextLayoutResult? = null
    private var x = FloatArray(0)
    // The letter after the last one on each row. Letters are in text order, so each row's are a run.
    private var ends = IntArray(0)
    private var brushes = arrayOfNulls<Brush>(0)

    fun laid(result: TextLayoutResult) {
        if (result === layout) return
        layout = result
        val length = result.layoutInput.text.length
        x = FloatArray(strength.size)
        ends = IntArray(result.lineCount)
        var k = 0
        for (word in spans) for (c in 0 until word.length) {
            val at = (word.start + c).coerceIn(0, maxOf(0, length - 1))
            val box = result.getBoundingBox(at)
            x[k] = (box.left + box.right) / 2
            val row = result.getLineForOffset(at)
            for (r in row until ends.size) ends[r] = k + 1
            k++
        }
        brushes = arrayOfNulls(result.lineCount)
        drawn.fill(-1f)
    }

    fun draw(scope: DrawScope, now: Double) = with(scope) {
        val layout = layout ?: return@with
        KaraokeLight.light(spans, now, strength)
        val moved = !strength.contentEquals(drawn)
        if (moved) strength.copyInto(drawn)
        var first = 0
        for (r in 0 until layout.lineCount) {
            val end = ends.getOrElse(r) { strength.size }
            val brush = (if (moved) null else brushes[r]) ?: brushFor(first, end).also { brushes[r] = it }
            first = end
            if (layout.lineCount == 1) {
                drawText(layout, brush)
            } else {
                // Each row has its own gradient, so it is drawn clipped to its own band. The bands meet, and the
                // outer ones run on past the box, so no ascender or descender is cut.
                val top = if (r == 0) -size.height else layout.getLineTop(r)
                val bottom = if (r == layout.lineCount - 1) size.height * 2 else layout.getLineTop(r + 1)
                clipRect(left = -size.width, top = top, right = size.width * 2, bottom = bottom) { drawText(layout, brush) }
            }
        }
    }

    /** The brush for the letters [first] until [end], one row: a gradient through their centres, keeping only the
     * letters where the strength turns, or one colour when the row is all one strength. Before the first centre and
     * after the last the gradient holds its end colours. A right-to-left row runs its gradient the other way. */
    private fun brushFor(first: Int, end: Int): Brush {
        if (end <= first) return SolidColor(Color.White)
        val from = x[first]
        val until = x[end - 1]
        if (from == until || (first + 1 until end).all { strength[it] == strength[first] }) {
            return SolidColor(Color.White.copy(alpha = strength[first]))
        }
        val stops = ArrayList<Pair<Float, Color>>()
        var last = 0f
        for (i in first until end) {
            val turns = i == first || i == end - 1 || strength[i] != strength[i - 1] || strength[i] != strength[i + 1]
            if (!turns) continue
            last = ((x[i] - from) / (until - from)).coerceIn(last, 1f)
            stops += last to Color.White.copy(alpha = strength[i])
        }
        return Brush.horizontalGradient(*stops.toTypedArray(), startX = from, endX = until)
    }
}

private val SUNG_NOT = Color(0xFFEAEAEA)

/** How faint a dot is before its turn. */
private const val DOT_REST = 0.16f
