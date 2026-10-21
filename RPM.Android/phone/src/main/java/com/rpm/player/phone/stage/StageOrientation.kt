package com.rpm.player.phone.stage

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Which ways up the app goes, and who decides. The iPhone's `StageOrientation` (RPMUI/StageOrientation.swift).
 *
 * **Landscape is the stage, and the stage is a record on a deck.** Every other screen is drawn for a phone held
 * upright, and the one screen that wants the phone turned exists only while something is loaded. So turning the
 * phone in the library with nothing playing leaves the library alone, and stopping the music turns a sideways
 * phone back. The manifest names no orientation; this asks for one at run time, as the iPhone's app delegate
 * answers `supportedInterfaceOrientationsFor`.
 *
 * While something is loaded the activity follows the person's own rotation setting
 * ([ActivityInfo.SCREEN_ORIENTATION_USER]): with auto-rotate on it follows the phone, and with it off it stays the
 * way the person locked it, as an iPhone with its rotation lock on does. Upside-down is left out, as on iPhone.
 */
object StageOrientation {
    /**
     * Set by the stage's portrait key ([requestPortrait]): the person asked for the library back without turning
     * the phone upright. Portrait is held until the phone is seen upright, so the sensor does not turn the app
     * straight back to the stage it was just taken out of. The iPhone's `requestGeometryUpdate(.portrait)` behaves
     * the same: it lasts until the phone is next turned.
     */
    private var portraitAsked = false

    private var loaded = false
    private var watching: OrientationEventListener? = null

    /**
     * Says whether there is a record on the deck. Portrait is asked for on the way down, not only allowed, so a
     * phone lying sideways when the music stops is turned back to a screen drawn for it.
     */
    fun allow(activity: Activity, landscape: Boolean) {
        loaded = landscape
        if (!landscape) {
            portraitAsked = false
            stopWatching()
        }
        apply(activity)
    }

    /**
     * Back to portrait, from the stage's key. Tilting the phone upright does this too; this is the way out for
     * somebody who wants the library back without turning the phone.
     */
    fun requestPortrait(activity: Activity) {
        portraitAsked = true
        apply(activity)
        startWatching(activity)
    }

    private fun apply(activity: Activity) {
        val wanted = if (loaded && !portraitAsked) ActivityInfo.SCREEN_ORIENTATION_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (activity.requestedOrientation != wanted) activity.requestedOrientation = wanted
    }

    /**
     * Lets go of an asked-for portrait once the phone is held upright, so the next turn opens the stage again. With
     * auto-rotate off there is nothing to watch: the asked-for portrait then lasts until the music stops, because
     * letting go would turn the app straight back to the rotation the person locked.
     */
    private fun startWatching(activity: Activity) {
        stopWatching()
        val autoRotate = Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        if (!autoRotate) return
        val app = activity.applicationContext
        watching = object : OrientationEventListener(app) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                if (degrees < UPRIGHT || degrees > 360 - UPRIGHT) {
                    portraitAsked = false
                    stopWatching()
                    apply(activity)
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    private fun stopWatching() {
        watching?.disable()
        watching = null
    }

    /** How near upright, in degrees either side, the phone has to be held to count as turned back. */
    private const val UPRIGHT = 25
}

/**
 * Applies [StageOrientation.allow] whenever [loaded] changes, and on the first composition. **The first call
 * matters most**: an app opened on a phone that is already lying sideways with nothing playing has to be asked
 * for portrait, and the iPhone's `allow` is unguarded on its first call for the same reason.
 */
@Composable
fun StageOrientationFollows(loaded: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    LaunchedEffect(loaded) { StageOrientation.allow(activity, loaded) }
}

/**
 * Whether the screen is turned sideways: wider than it is tall, or, on a square screen such as the Titan's, turned a
 * quarter from its natural way up. The iPhone reads its vertical size class, which is compact in landscape on every
 * iPhone. Android's own `Configuration.orientation` cannot be used on a square screen: it compares the width with
 * the height left after the system bars, so an upright square phone reads as landscape.
 *
 * Read again on every configuration change, which a rotation is (the activity handles it without being recreated).
 */
@Composable
fun rememberTurned(): Boolean {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) { turned(context) }
}

@Suppress("DEPRECATION")
private fun turned(context: Context): Boolean {
    val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val display = manager.defaultDisplay
    val size = Point().also { display.getRealSize(it) }
    val rotation = display.rotation
    val quarter = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    return when {
        size.x > size.y -> true
        size.x < size.y -> false
        else -> quarter
    }
}

/**
 * Hides the status and navigation bars while it is composed, and brings them back when it leaves. The stage fills
 * the whole screen, as the iPhone shows no status bar in landscape. A swipe from an edge shows the bars for a
 * moment, so the system is still reachable.
 */
@Composable
fun HideSystemBars() {
    val view: View = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** The activity a composable is shown in, unwrapping the context wrappers Compose may put around it. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
