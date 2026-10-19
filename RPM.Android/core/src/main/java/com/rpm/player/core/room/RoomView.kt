package com.rpm.player.core.room

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Size
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rpm.player.core.playback.AudioTap

/** How the room is drawn on this device. [scale] is the share of the screen the room is drawn at
 * before it is scaled up; [measure] writes frame timing to logcat (`rpm.room`). A debuggable build
 * takes both from the launch intent, so they can be changed without a rebuild:
 * `adb shell am start -n com.rpm.player.tv/.MainActivity --ef rpm.roomScale 0.5 --ez rpm.roomMeasure true`. */
object RoomTuning {
    /** An eighth. On the Amiko (Mali-G31 MP2) the room alone drew at 60 frames a second at a quarter, 48
     * at a third, 28 at a half and 8.6 at full size. With home's moves running, the quarter added 21 to
     * 25 ms of GPU waiting to every frame and the eighth added 10. The room is soft enough that an
     * eighth shows no loss. */
    @Volatile var scale = 0.125f
    @Volatile var measure = false
}

/**
 * The room as a composable: a `GLSurfaceView` that draws one frame each time the display shows one,
 * while the screen is resumed, and stops when it pauses or leaves. A surface view sits behind the
 * window, so whatever is composed over it shows the room wherever it draws nothing.
 *
 * Unless [sharp], the surface is the room's own small size and the system stretches it to the screen, so
 * the room costs one small pass and the composing of one more layer. The grain and the spectrum need
 * the screen's own pixels, so the player asks for a [sharp] surface, the screen's size.
 *
 * Drawn on request from the display's frame clock, not continuously. A still room is asked only when
 * it has something new to show. Drawn continuously, the GL thread queues frames ahead of the display,
 * and on the Shield the GPU works through those while the window's own frames wait behind them (home
 * ran at 8 frames a second). Asked once per frame, the room is at most one frame ahead, and a request
 * made while it is still drawing is folded into the next.
 */
@Composable
fun RoomView(tap: AudioTap, target: RoomTarget, sharp: Boolean, onDrawn: () -> Unit, modifier: Modifier) {
    val view = remember { arrayOfNulls<GLSurfaceView>(1) }
    val metrics = LocalContext.current.resources.displayMetrics
    val screen = remember(metrics.widthPixels, metrics.heightPixels) { Size(metrics.widthPixels, metrics.heightPixels) }
    val small = remember(screen) { Size((screen.width * RoomTuning.scale).roundToInt().coerceAtLeast(16), (screen.height * RoomTuning.scale).roundToInt().coerceAtLeast(9)) }
    val renderer = remember { RoomRenderer(tap, target, RoomTuning.scale, screen, RoomTuning.measure, dim = 1f) }
    // Told on the main thread once the first frame is drawn. A still room may draw no second frame, so the
    // caller waits its own frames for this one to reach the display before it takes away what stood in
    // for it ([RoomPicture]).
    // A post that lands after this view has left the screen is dropped: told late, the caller would take
    // the next surface for drawn before it had drawn anything. Both run on the main thread.
    val told by rememberUpdatedState(onDrawn)
    val here = remember { AtomicBoolean(true) }
    val counted = remember {
        val frames = AtomicInteger(0)
        val main = Handler(Looper.getMainLooper())
        object : GLSurfaceView.Renderer by renderer {
            override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
                renderer.onDrawFrame(gl)
                if (frames.incrementAndGet() == 1) main.post { if (here.get()) told() }
            }
        }
    }
    // A still room with nothing new to show is not asked for a frame.
    val pacer = remember { RoomPacer { if (renderer.wantsFrame()) view[0]?.requestRender() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    AndroidView(
        modifier = modifier,
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(3)
                setEGLConfigChooser(8, 8, 8, 0, 0, 0)
                preserveEGLContextOnPause = true
                setRenderer(counted)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                view[0] = this
            }
        },
        // A change of size makes a new surface, drawn on the next frame asked for.
        update = { v ->
            val size = if (sharp) screen else small
            v.holder.setFixedSize(size.width, size.height)
        },
    )

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    view[0]?.onResume()
                    pacer.start()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    pacer.stop()
                    view[0]?.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            here.set(false)
            lifecycle.removeObserver(observer)
            pacer.stop()
            view[0]?.onPause()
        }
    }
}

/**
 * Asks for one room frame on every display frame, from the main thread's `Choreographer`, between
 * [start] and [stop]. Both are called on the main thread; a frame callback already posted when [stop]
 * runs is removed, so nothing is asked after it.
 */
private class RoomPacer(private val ask: () -> Unit) : Choreographer.FrameCallback {
    private var running = false

    fun start() {
        if (running) return
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        ask()
        Choreographer.getInstance().postFrameCallback(this)
    }
}
