package com.rpm.player.tv.ui

/**
 * The height the screens are drawn at, chosen in the settings panel. [Automatic] is the output's own height
 * (for example 2160 on a Shield, 1080 on an Amiko). A different height is drawn by `SharpHost` on a virtual
 * display of that size: a higher one is sharper and costs more, a lower one is softer and costs less.
 */
enum class ScreenResolution(val height: Int?) {
    Automatic(null),
    P2160(2160),
    P1440(1440),
    P1080(1080),
    P720(720),
}
