package com.rpm.player.phone.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.rpm.player.core.art.ArtScene
import com.rpm.player.phone.art.Portraits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * An artist's photographs as a deck the person pages through on the stage, and the swipe that moves it: the iPhone's
 * `BackdropDeck` (RPMUI/BackdropDeck.swift).
 *
 * The crop is only arithmetic, and the person looking is the one who can tell the third photograph is the good one. A
 * swipe pages through them, and the pictures move together under the finger: **the gesture is the transition**, with
 * no snap back followed by a fade.
 *
 * **The whole deck is laid out**, not stacked when a drag begins: every photograph the artist has (at most [MOST]) is
 * fetched, cut for this frame and given a place, picture *n* sitting `n - showing` frames to the right of the one on
 * screen. A swipe moves one number, [travel], and nothing is decided while a finger is down. The position advances only
 * once the next picture is there, and the deck stops at both ends without wrapping.
 *
 * A drag moves the pictures on every frame, so only [StagePictures]'s layers read [travel]; the stage around it is not
 * composed again.
 */
@Stable
class BackdropDeck(private val scope: CoroutineScope, private val density: Float) {
    /** Every photograph that has come down, cut for this frame, by its place in the deck. */
    val pictures = mutableStateMapOf<Int, ImageBitmap>()

    /** Which of them is on screen. */
    var showing by mutableIntStateOf(0)
        private set

    /** How many the artist has, which is not how many have been fetched. */
    var count by mutableIntStateOf(0)
        private set

    /** How far the deck has been moved from where it sits, in pixels: one number, because they move as one. */
    private val carried = Animatable(0f)
    val travel: Float get() = carried.value

    /** The photograph that is up, or null while it is still coming down. */
    val picture: ImageBitmap? get() = pictures[showing]

    private var portraits: Portraits? = null
    private var artist = ""
    private var album = ""
    private var scene: ArtScene? = null

    /** Whose deck this is, case-folded, so one artist spelled two ways is one deck. */
    private var whose: String? = null
    private var filling: Job? = null
    private var moving: Job? = null

    /**
     * Puts an artist's photographs in, cut for [scene]. A different artist starts on their opening photograph; **the
     * same artist keeps the one that is up**, so playing through their records, or turning the phone, does not undo a
     * picture chosen by hand.
     */
    suspend fun load(portraits: Portraits, artist: String, album: String, scene: ArtScene) {
        val key = artist.lowercase()
        // A photograph cut for one shape is cut wrong for another.
        if (whose != key || this.scene != scene) {
            filling?.cancel()
            pictures.clear()
        }
        this.portraits = portraits
        this.artist = artist
        this.album = album
        this.scene = scene
        moving?.cancel()
        carried.snapTo(0f)
        val howMany = portraits.count(artist, album)
        // Settled on an artist only when there is something to settle on: a lookup that failed must not stop the
        // next one choosing.
        if (whose != key && howMany > 0) {
            whose = key
            showing = portraits.opening(artist, album)
        }
        count = minOf(howMany, MOST)
        fill()
    }

    /** Drops everything: art for a screen nobody is looking at only uses memory. */
    fun clear() {
        filling?.cancel()
        filling = null
        pictures.clear()
        count = 0
        whose = null
        scope.launch { carried.snapTo(0f) }
    }

    /** A finger on the deck, [moved] pixels across a frame [width] wide. Nothing is decided here. */
    fun dragged(moved: Float, width: Float) {
        if (count <= 0) return
        // Dragging left brings the next photograph in from the right.
        val step = if (moved < 0) 1 else -1
        // Where there is nothing to move to, the deck moves against a rubber band instead of into a gap.
        val to = if (pictures[showing + step] != null) moved.coerceIn(-width, width)
        else (moved * RUBBER_FOLLOW).coerceIn(-RUBBER_MAX * density, RUBBER_MAX * density)
        moving?.cancel()
        moving = scope.launch { carried.snapTo(to) }
    }

    /** The finger lifted after [moved] pixels at [velocity] pixels a second, across a frame [width] wide. */
    fun released(moved: Float, velocity: Float, width: Float) {
        if (count <= 0) return
        val step = if (moved < 0) 1 else -1
        val there = pictures[showing + step] != null
        val flicked = abs(velocity) >= FLICK_SPEED * density && abs(moved) >= FLICK_MIN_TRAVEL * density
        val far = abs(moved) >= width * COMMIT
        moving?.cancel()
        if (!there || !(flicked || far)) {
            moving = scope.launch { carried.animateTo(0f, SLIDE) }
            return
        }
        // **The deck changes first, and the slide catches up**: the next photograph becomes the one showing, and the
        // travel is set to where the finger left the pictures relative to it, then slides to nothing. One movement over
        // one distance, with nothing rearranged under a moving picture.
        showing += step
        moving = scope.launch {
            carried.snapTo(moved + step * width)
            carried.animateTo(0f, SLIDE)
        }
        land()
    }

    /** Arriving at a photograph: landing is choosing, so it is written down, and what is still missing is fetched. */
    private fun land() {
        val store = portraits ?: return
        val at = showing
        val who = artist
        val record = album
        scope.launch { store.leftOn(who, record, at) }
        fill()
    }

    /**
     * Brings down every photograph the artist has, nearest the one on screen first and forward before back, one at a
     * time, each shown as it arrives, so the first swipe can move as soon as there is a picture to move to.
     */
    private fun fill() {
        val store = portraits ?: return
        val frame = scene ?: return
        if (count <= 0 || filling?.isActive == true) return
        val mine = whose
        val from = showing
        val total = count
        filling = scope.launch {
            for (at in order(from, total)) {
                if (pictures[at] != null) continue
                val got = store.backdrop(artist, album, at, frame)
                // The music moved on: this photograph is of an artist nobody is listening to.
                if (mine != whose) return@launch
                if (got != null) pictures[at] = got.asImageBitmap()
            }
        }
    }

    companion object {
        /** How many photographs are kept: the desktop's preselector keeps eight. */
        const val MOST = 8

        /** How far a drag must carry the deck to count, as a share of the frame; a flick needs less. In dp and dp/s. */
        private const val COMMIT = 0.22f
        private const val FLICK_SPEED = 700f
        private const val FLICK_MIN_TRAVEL = 12f

        /** How far the deck follows a finger where there is nowhere to go, and at most how far (dp). */
        private const val RUBBER_FOLLOW = 0.35f
        private const val RUBBER_MAX = 24f

        /** SwiftUI's `spring(duration: 0.3)`: critically damped, settling in about 0.3 s. */
        private val SLIDE = spring<Float>(dampingRatio = 1f, stiffness = 438f)

        /** The deck's places, nearest [from] first, forward before back at each step. */
        fun order(from: Int, count: Int): List<Int> {
            if (count <= 0) return emptyList()
            val places = mutableListOf(from.coerceIn(0, count - 1))
            var step = 1
            while (places.size < count && step <= count) {
                for (at in listOf(from + step, from - step)) if (at in 0 until count && at !in places) places += at
                step++
            }
            return places
        }
    }
}

/**
 * The deck drawn: the photograph that is up and its neighbours in their places either side of the frame (`StagePictures`
 * on iPhone). Three at most, because a fourth cannot be on screen however far a finger carries the deck. The first
 * picture fades in over 0.45 s. Only the layers read the travel.
 */
@Composable
fun StagePictures(deck: BackdropDeck, width: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (deck.picture != null) 1f else 0f, tween(450, easing = EaseOut), label = "deck")
    Box(modifier.graphicsLayer { alpha = shown }) {
        val at = deck.showing
        for (place in at - 1..at + 1) {
            val picture = deck.pictures[place] ?: continue
            Image(
                picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = deck.travel + (place - deck.showing) * width },
            )
        }
    }
}
