package com.rpm.player.tv.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.rpm.player.core.R
import java.io.File

/** What the stats overlay shows ([StatsOverlay]). [StatsMeter] works it out once a second. */
data class Stats(
    /** Frames the window drew in the last second. */
    val fps: Int,
    /** Of those, the ones that took longer than one display frame. */
    val late: Int,
    /** The longest of them, in milliseconds. */
    val slowestMs: Int,
    /** Frames drawn, and late, since the overlay was shown. */
    val frames: Long,
    val lateFrames: Long,
    /** This app's CPU time over the last second, as a share of all the device's cores. */
    val cpuPercent: Int,
    /** This app's memory: all of it (PSS), the Java heap, the native heap and graphics; and the device's free
     * memory. All in MB. PSS and graphics are read every [StatsMeter.MEMORY_EVERY_MS]. */
    val totalMb: Int,
    val javaMb: Int,
    val nativeMb: Int,
    val graphicsMb: Int,
    val freeMb: Int,
)

/**
 * Measures the app while the overlay is up: the window's frames, from its frame timings (the whole time of a
 * frame, from input to the GPU hand-off, as `dumpsys gfxinfo` counts it), the process's CPU time from
 * `/proc/self/stat`, and its memory. The counting runs on a thread of its own, `rpm.stats`. [onStats] is
 * called on the main thread once a second. The overlay itself is redrawn once a second, which is one frame
 * a second that the counts include.
 */
class StatsMeter(private val window: Window, context: Context, private val onStats: (Stats) -> Unit) {
    private val activities = context.getSystemService(ActivityManager::class.java)
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val thread = HandlerThread("rpm.stats").apply { start() }
    private val counting = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val intervalNanos = (1e9 / (window.decorView.display?.refreshRate ?: 60f).coerceAtLeast(1f)).toLong()

    // Written and read on the counting thread only.
    private var second = 0
    private var secondLate = 0
    private var secondSlowest = 0L
    private var frames = 0L
    private var lateFrames = 0L
    private var cpuBefore = cpuTicks()
    private var cpuAt = SystemClock.uptimeMillis()
    private var memoryAt = 0L
    private var totalMb = 0
    private var graphicsMb = 0

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        // Frames whose timings were dropped because this thread fell behind are counted but not judged.
        second += 1 + dropped
        frames += 1 + dropped
        if (total > intervalNanos) {
            secondLate++
            lateFrames++
        }
        if (total > secondSlowest) secondSlowest = total
    }

    private val tick = object : Runnable {
        override fun run() {
            val stats = measure()
            main.post { onStats(stats) }
            counting.postDelayed(this, 1_000)
        }
    }

    fun start() {
        window.addOnFrameMetricsAvailableListener(listener, counting)
        counting.postDelayed(tick, 1_000)
    }

    fun stop() {
        runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
        counting.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    private fun measure(): Stats {
        val now = SystemClock.uptimeMillis()
        val cpu = cpuTicks()
        val seconds = (now - cpuAt).coerceAtLeast(1) / 1000.0
        val cpuPercent = ((cpu - cpuBefore) / (seconds * TICKS_PER_SECOND * cores) * 100).toInt().coerceIn(0, 100)
        cpuBefore = cpu
        cpuAt = now
        if (now - memoryAt >= MEMORY_EVERY_MS) {
            memoryAt = now
            // This takes tens of milliseconds on this thread, because it walks the process's memory maps.
            val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            totalMb = info.totalPss / 1024
            graphicsMb = (info.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0) / 1024
        }
        val runtime = Runtime.getRuntime()
        val device = ActivityManager.MemoryInfo().also { activities?.getMemoryInfo(it) }
        val stats = Stats(
            fps = second,
            late = secondLate,
            slowestMs = (secondSlowest / 1_000_000).toInt(),
            frames = frames,
            lateFrames = lateFrames,
            cpuPercent = cpuPercent,
            totalMb = totalMb,
            javaMb = ((runtime.totalMemory() - runtime.freeMemory()) / MB).toInt(),
            nativeMb = (Debug.getNativeHeapAllocatedSize() / MB).toInt(),
            graphicsMb = graphicsMb,
            freeMb = (device.availMem / MB).toInt(),
        )
        second = 0
        secondLate = 0
        secondSlowest = 0
        return stats
    }

    /** The process's user and system CPU time in clock ticks, fields 14 and 15 of `/proc/self/stat`. They are
     * read after the name, which is in parentheses and may contain spaces. Returns 0 where it cannot be read. */
    private fun cpuTicks(): Long = runCatching {
        val fields = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
        // After the name, the fields start at the third. utime is the 14th field, so it is the 12th here.
        fields[11].toLong() + fields[12].toLong()
    }.getOrDefault(0L)

    companion object {
        const val MEMORY_EVERY_MS = 5_000L
        private const val TICKS_PER_SECOND = 100.0
        private const val MB = 1024L * 1024L
    }
}

/**
 * The stats overlay: four lines at the top left of the screen, in a fixed-width font on `#B3000000`. The
 * numbers come from [StatsMeter] once a second: frames in the last second (how many, how many late, the
 * slowest), frames since the overlay was shown, the app's CPU and its memory. It is switched on in the settings
 * panel ([PlayerSettings.showStats]).
 */
@Composable
fun StatsOverlay(modifier: Modifier) {
    val context = LocalContext.current
    val window = LocalScreensWindow.current
    var stats by remember { mutableStateOf<Stats?>(null) }
    DisposableEffect(window) {
        val meter = StatsMeter(window, context) { stats = it }
        meter.start()
        onDispose { meter.stop() }
    }
    val shown = stats ?: return
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp, color = Color.White)
    val share = if (shown.frames > 0) (shown.lateFrames * 100 / shown.frames).toString() else "0"
    Column(modifier.background(Color(0xB3000000), RoundedCornerShape(4.px)).padding(horizontal = 10.px, vertical = 6.px)) {
        Text(stringResource(R.string.Tv_StatsNow, shown.fps.toString(), shown.late.toString(), shown.slowestMs.toString()), style = style)
        Text(stringResource(R.string.Tv_StatsSince, shown.frames.toString(), shown.lateFrames.toString(), share), style = style)
        Text(stringResource(R.string.Tv_StatsCpu, shown.cpuPercent.toString()), style = style)
        Text(
            stringResource(R.string.Tv_StatsMemory, shown.totalMb.toString(), shown.javaMb.toString(), shown.nativeMb.toString(), shown.graphicsMb.toString(), shown.freeMb.toString()),
            style = style,
        )
    }
}
