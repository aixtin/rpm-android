package com.rpm.player.core.art

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * How many output pixels there are for each pixel of the window. It is 1 where apps are drawn at the output's own
 * size. It is 2 on the Shield, which sends 3840 x 2160 to the television and lays apps out at 1920 x 1080. The TV
 * app draws its screens at the output's size where this is over 1 (the TV's `SharpHost`), and covers are requested
 * at a size that fills that ([coverEdgeFor]). Both use this one reading, so they always agree.
 */
object OutputPixels {
    fun share(context: Context): Float {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return 1f
        val mode = display.mode
        val metrics = context.resources.displayMetrics
        val logical = maxOf(metrics.widthPixels, metrics.heightPixels)
        val physical = maxOf(mode.physicalWidth, mode.physicalHeight)
        return if (logical <= 0 || physical <= logical) 1f else physical.toFloat() / logical
    }
}
