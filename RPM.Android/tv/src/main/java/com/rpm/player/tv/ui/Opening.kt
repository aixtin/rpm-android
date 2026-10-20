package com.rpm.player.tv.ui

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.view.Choreographer
import android.os.HandlerThread
import android.os.Handler
import android.graphics.Shader
import android.graphics.PorterDuffXfermode
import android.graphics.PorterDuff
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.LinearGradient
import android.graphics.Canvas
import android.graphics.Bitmap
import android.annotation.SuppressLint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.size
import android.os.SystemClock
import com.rpm.player.core.library.QuickPick
import com.rpm.player.core.library.LibraryState
import com.rpm.player.core.library.BannerCard
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import coil3.request.ImageRequest
import coil3.ImageLoader
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rpm.player.core.R

/**
 * The opening screen: the rpm wordmark on black, dimmed, with a glowing
 * band that sweeps across it from left to right, lighting the letters it passes, once a second, over and
 * over, until the app is ready to show home.
 *
 * The dimmed wordmark is drawn here, where the window's background draws it before the app's first frame
 * (opening_window.xml), so nothing moves when that frame comes. The band is drawn over it by
 * [SweepSurface], on a thread of its own: home's first composition holds the main thread for seconds on a
 * low-end TV box (over 200 frames skipped), and a sweep drawn by the main thread would stand still for all
 * of it.
 *
 * [ready] hands over at the end of a pass, never in the middle of one: once it is true, the band is faded
 * out and [onSwept] called at the next moment a pass ends, counted from when the sweep started, with the
 * band clear of the letters. The caller fades the rest out with it.
 */
@Composable
fun OpeningSplash(
    ready: Boolean,
    /** How many tracks have come from the cloud so far, while the library is read from the cloud with nothing
     * cached on the TV ([com.rpm.player.core.library.LibraryState.Loading]); 0 otherwise. */
    reading: Int,
    onSwept: () -> Unit,
    modifier: Modifier,
) {
    val handOver by rememberUpdatedState(ready)
    val swept by rememberUpdatedState(onSwept)
    val sweep = remember { arrayOfNulls<SweepSurface>(1) }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.wordmark_dim), contentDescription = null, modifier = Modifier.size(WORDMARK_WIDE, WORDMARK_HIGH))
        AndroidView(
            factory = { context -> SweepSurface(context).also { sweep[0] = it } },
            modifier = Modifier.size(WORDMARK_WIDE, WORDMARK_HIGH),
        )
        // A first launch, or one with no library kept on the TV, reads the whole library from the cloud before
        // home can show, 15 to 20 seconds for 23,000 tracks on a low-end TV box; the line under the logo says so,
        // without a count of the tracks. A launch from the cache never shows it: the library publishes the count only
        // when nothing is cached.
        val lineIn by animateFloatAsState(if (reading > 0) 1f else 0f, tween(READING_FADE_MS), label = "reading")
        if (reading > 0 || lineIn > 0f) {
            Text(
                stringResource(R.string.Rpm_Library_Reading),
                style = ui(18.ts),
                color = Color(0x99FFFFFF),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = WORDMARK_HIGH / 2 + READING_GAP)
                    .graphicsLayer { alpha = lineIn },
            )
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { handOver }.first { it }
        val surface = sweep[0]
        if (surface != null) {
            val elapsed = SystemClock.uptimeMillis() - surface.began
            delay(SWEEP_MS - elapsed % SWEEP_MS)
            surface.fadeOut()
        }
        swept()
    }
}

/**
 * The opening's band of light: the white wordmark, kept only under a gradient that rides across it once a
 * second, drawn into a surface over the window by a thread of its own and paced by that thread's own
 * Choreographer. Nothing it draws waits for the main thread.
 *
 * The band is [BAND_OF_WIDTH] of the wordmark wide, and its light falls from the middle to nothing at either
 * edge along a smootherstep, with no corner anywhere for the eye to find. It draws no dimmed wordmark of its
 * own: that is under it, in the window ([OpeningSplash]).
 */
@SuppressLint("ViewConstructor")
class SweepSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    /** When the sweep started, on the uptime clock: a pass ends every [SWEEP_MS] from here. */
    val began: Long = SystemClock.uptimeMillis()

    /** When the band began to fade out, or 0 while it has not. Written on the main thread, read on the
     * drawing one. */
    @Volatile private var fadeFrom = 0L

    private var drawing: HandlerThread? = null
    private var painter: Painter? = null

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    /** Fades the band out over [OPENING_FADE_MS], as the screen under it fades. */
    fun fadeOut() {
        fadeFrom = SystemClock.uptimeMillis()
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        stop()
        if (width <= 0 || height <= 0) return
        val mark = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        context.getDrawable(R.drawable.wordmark)?.apply {
            setBounds(0, 0, width, height)
            draw(Canvas(mark))
        }
        val thread = HandlerThread("rpm-opening").apply { start() }
        val drawer = Painter(holder, mark, width.toFloat(), height.toFloat())
        drawing = thread
        painter = drawer
        Handler(thread.looper).post { drawer.start() }
    }

    /** The surface is released as soon as this returns, so no frame may still be on its way to it. */
    override fun surfaceDestroyed(holder: SurfaceHolder) = stop()

    /**
     * Stops the drawing and waits for the thread to end, frame in hand included. The wait has no short
     * limit: a frame posted to a surface that is released under it aborts the app on the render thread
     * (EGL_BAD_ALLOC in swapBuffers, seen on a low-end TV box with a 200 ms limit). A frame takes
     * milliseconds; [STOP_WAIT_MS] only keeps a stuck thread from holding the main one for ever.
     */
    private fun stop() {
        painter?.live = false
        painter = null
        val thread = drawing ?: return
        drawing = null
        thread.quitSafely()
        thread.join(STOP_WAIT_MS)
    }

    private inner class Painter(
        private val holder: SurfaceHolder,
        private val mark: Bitmap,
        private val wide: Float,
        private val high: Float,
    ) : Choreographer.FrameCallback {
        private val choreographer = Choreographer.getInstance()
        private val markPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val bandPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = LinearGradient(0f, 0f, BAND_OF_WIDTH * wide, 0f, BAND_COLOURS, BAND_STOPS, Shader.TileMode.CLAMP)
        }
        private val place = Matrix()

        /** False once the surface is going: no frame is begun after that. */
        @Volatile var live = true

        fun start() = choreographer.postFrameCallback(this)

        override fun doFrame(frameTimeNanos: Long) {
            if (!live) return
            val now = SystemClock.uptimeMillis()
            val from = fadeFrom
            val shown = if (from == 0L) 1f else (1f - (now - from) / OPENING_FADE_MS.toFloat()).coerceIn(0f, 1f)
            val pass = QuadInOut.transform(((now - began) % SWEEP_MS) / SWEEP_MS.toFloat())
            if (!draw(pass, shown)) return
            if (shown > 0f) choreographer.postFrameCallback(this)
        }

        /**
         * One frame: cleared, the white wordmark, then everything outside the band taken away. False when the
         * surface has gone.
         *
         * Drawn on a software canvas, 260 by 100 pixels a frame. A hardware one (lockHardwareCanvas) gives the
         * surface a renderer of its own on the app's render thread, and on a low-end TV box that thread then aborts
         * the app with EGL_BAD_ALLOC within seconds of launching, in about one launch in three.
         */
        private fun draw(pass: Float, shown: Float): Boolean {
            val canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            } ?: return false
            try {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                markPaint.alpha = (255 * shown).toInt()
                canvas.drawBitmap(mark, 0f, 0f, markPaint)
                // The band rides from wholly before the first letter to wholly after the last.
                val band = BAND_OF_WIDTH * wide
                place.setTranslate(-band + pass * (wide + band), 0f)
                bandPaint.shader.setLocalMatrix(place)
                canvas.drawRect(0f, 0f, wide, high, bandPaint)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            return true
        }
    }

    private companion object {
        /** The longest the main thread waits for the drawing thread to end when the surface goes. */
        const val STOP_WAIT_MS = 3_000L
    }
}

/** The band's width, as a share of the wordmark's. */
private const val BAND_OF_WIDTH = 0.6f

/** The band's light across its width: 1 - smootherstep(distance from the middle), white at the middle and
 * nothing at either edge, flat at all three. */
private val BAND_STOPS = FloatArray(17) { it / 16f }
private val BAND_COLOURS = IntArray(BAND_STOPS.size) { i ->
    val x = kotlin.math.abs(2 * BAND_STOPS[i] - 1)
    val light = 1 - x * x * x * (x * (x * 6 - 15) + 10)
    android.graphics.Color.argb((255 * light).toInt(), 255, 255, 255)
}

/**
 * Fetches the pictures home shows first into the image loader, so home appears with them in rather than
 * filling in after the opening screen has gone (the desktop's splash waits for the banner's and the lists'
 * pictures the same way, HomeView.IsReady):
 * the first three banner cards, the first twelve of Quick selection, the first six lists. At
 * [PICTURE_EDGE] pixels, which the loader reuses for the smaller boxes home draws them in. A picture that
 * fails is passed over; the caller bounds the wait.
 */
suspend fun fetchHomePictures(ready: LibraryState.Ready, images: ImageLoader, context: Context) = coroutineScope {
    val collection = ready.collection
    val home = ready.home
    val models = buildList<Any> {
        home.banner.take(3).forEach { card ->
            when (card) {
                is BannerCard.OfAlbum -> collection.cover(card.album)?.let(::add)
                is BannerCard.OfList -> collection.cover(card.list)?.let(::add)
                is BannerCard.OfStation -> Unit
            }
        }
        home.quick.take(12).forEach { pick ->
            when (pick) {
                is QuickPick.Song -> pick.album?.let(collection::cover)?.let(::add)
                is QuickPick.Radio -> pick.album?.let(collection::cover)?.let(::add)
                is QuickPick.OfAlbum -> collection.cover(pick.album)?.let(::add)
                is QuickPick.OfList -> collection.cover(pick.list)?.let(::add)
                is QuickPick.OfStation -> Unit
            }
        }
        home.madeForYou.take(6).forEach { list -> collection.cover(list)?.let(::add) }
    }.distinct()
    models.map { model ->
        async { runCatching { images.execute(ImageRequest.Builder(context).data(model).size(PICTURE_EDGE).build()) } }
    }.awaitAll()
}

/** Where the opening screen says when it handed over and why, for `adb logcat -s rpm.opening`. */
const val OPENING_LOG = "rpm.opening"

/** Milliseconds since the process started, for the opening's log lines. */
fun sinceStart(): Long = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()

/** How long the opening screen waits for home's first pictures before it shows home anyway. */
const val OPENING_PICTURES_MS = 3_000L

/** The size the first pictures are fetched at: a home card's square at 1080, rounded up. */
private const val PICTURE_EDGE = 400

/** The wordmark's size: 260 wide at 1080, as in the sketch, and its own height for that. The same 130 by
 * 50 dp as wordmark_dim.xml and opening_window.xml, which hold the dim (0.32). */
private val WORDMARK_WIDE = 260.px
private val WORDMARK_HIGH = 100.px

/** How far under the logo's middle the count line stands, past the logo's lower half. */
private val READING_GAP = 40.px

/** How long the count line takes to come up. */
private const val READING_FADE_MS = 300

/** One pass of the band across the wordmark. */
const val SWEEP_MS = 1000L

/** How long the opening screen takes to fade away once the app is ready. */
const val OPENING_FADE_MS = 600

/**
 * The sound the app makes when it opens: the desktop's `rpm-open` (AppSound.PlayOpen), the site's mp3 of it,
 * started as home fades in. At [SOFTER] of full, as the desktop plays it under the player's level, and
 * stopped as soon as music starts ([stop]): whoever puts a record on has said what the speakers are for.
 * Played once per process, through its own MediaPlayer, sharing nothing with the playback service's
 * player. Best effort: a sound that cannot be prepared is not played and not reported.
 *
 * Not played over music that is already playing: the app opened again while its own queue plays, or another
 * app's music.
 * Asked of the system when the opening hands over, which answers whether or not this activity's controller
 * has connected to the playback service yet.
 */
class OpeningSound(private val context: Context) {
    private var player: MediaPlayer? = null
    private var played = false

    fun play() {
        if (played) return
        played = true
        if (context.getSystemService(AudioManager::class.java)?.isMusicActive == true) return
        val sound = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                context.resources.openRawResourceFd(R.raw.rpm_open).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setVolume(SOFTER, SOFTER)
                setOnPreparedListener { if (player === it) it.start() }
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); true }
                prepareAsync()
            }
        }.getOrNull()
        player = sound
    }

    fun stop() {
        val sound = player ?: return
        player = null
        runCatching { sound.stop() }
        sound.release()
    }

    private companion object {
        /** The desktop's `Softer`: a flourish, under the music's level. */
        const val SOFTER = 0.7f
    }
}
