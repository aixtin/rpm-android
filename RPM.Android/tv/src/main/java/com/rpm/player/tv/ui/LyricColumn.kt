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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
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
 * With [karaoke] the sung line lights word by word, and each word brightens as its turn comes. The timing is
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
    // Seconds into the song, updated once a frame. Only things that move within a piece read it: the dots and the karaoke line.
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
                            androidx.compose.foundation.text.BasicText(
                                text = sweepOf(piece.line.text, spans, now),
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
 * The sung line for karaoke: words already sung are white, the word being sung brightens over [LIGHT_S] seconds,
 * and the rest are [UNSUNG]. A word starts to light a little before its moment, so the light reaches full
 * brightness on the word and not after it.
 */
private fun sweepOf(text: String, spans: List<com.rpm.player.core.lyrics.WordSpan>, now: Double): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(color = UNSUNG), 0, text.length)
    for (span in spans) {
        val lit = ((now - (span.from - LIGHT_S / 2)) / LIGHT_S).toFloat().coerceIn(0f, 1f)
        if (lit > 0f) addStyle(SpanStyle(color = lerp(UNSUNG, Color.White, lit)), span.start, span.start + span.length)
    }
}

private val SUNG_NOT = Color(0xFFEAEAEA)

/** A word of the karaoke line not sung yet. */
private val UNSUNG = Color(0x73FFFFFF)

/** How long a word takes to brighten, in seconds, centred on its moment. */
private const val LIGHT_S = 0.24

/** How faint a dot is before its turn. */
private const val DOT_REST = 0.16f
