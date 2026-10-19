package com.rpm.player.tv.ui

import android.view.View
import android.view.Window
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The window the screens are drawn in: the activity's window, or, where the screens are drawn at the output's
 * own pixels (`SharpHost`), the window of the virtual display. Its frames are the screens' frames
 * ([StatsOverlay]). Provided by `MainActivity`.
 */
val LocalScreensWindow = staticCompositionLocalOf<Window> { error("No screens window provided") }

/**
 * A view in the window that the on-screen keyboard opens over. Its insets say whether the keyboard is up. It is
 * always the activity's view, because that window has the focus, even where the screens are on a virtual
 * display. Read this, not [LocalView], for the keyboard's insets. Provided by `MainActivity`.
 */
val LocalKeyboardView = staticCompositionLocalOf<View> { error("No keyboard view provided") }
