package com.rpm.player.phone

import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.compose.runtime.mutableIntStateOf
import com.rpm.player.core.playback.Sitting
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rpm.player.core.playback.PlaybackService
import com.rpm.player.core.services
import com.rpm.player.phone.shell.Root

class MainActivity : ComponentActivity() {
    private val app get() = application as PhoneApplication

    /** The playback service's controller while the activity is started, null until it has connected. The
     * TV app works the same way: a tap before it connects plays nothing. */
    private val controller = mutableStateOf<MediaController?>(null)
    private var connecting: ListenableFuture<MediaController>? = null

    /** Where the session says this sitting begins in the queue ([Sitting]), from its extras: read when the controller
     * connects and each time the session publishes them again. */
    private val sittingFrom = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge with light icons on clear bars: the app is dark throughout, as the iPhone app is, and
        // every screen pads itself for the bars (targetSdk 36 allows nothing else).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { Root(services, app.settings, app.signIn, controller.value, sittingFrom.intValue) }
    }

    override fun onStart() {
        super.onStart()
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .setListener(
                object : MediaController.Listener {
                    override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                        // Only the connected controller's news: one from an earlier start says nothing about now.
                        if (controller === this@MainActivity.controller.value) sittingFrom.intValue = extras.getInt(Sitting.EXTRA_FROM)
                    }
                },
            )
            .buildAsync()
        connecting = future
        future.addListener(
            {
                // A future from an earlier start that finishes late is not this start's controller.
                if (connecting === future && !future.isCancelled) {
                    controller.value = runCatching { future.get() }.getOrNull()
                    sittingFrom.intValue = controller.value?.sessionExtras?.getInt(Sitting.EXTRA_FROM) ?: 0
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    override fun onStop() {
        controller.value = null
        connecting?.let(MediaController::releaseFuture)
        connecting = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Back from the browser a sign-in opened, closed or finished: see SignIn.backFromBrowser.
        app.signIn.backFromBrowser()
    }

    companion object {
        /** Set on the intent the media session opens: show the player (`RpmApplication.sessionOpening`). */
        const val EXTRA_SHOW_PLAYER = "rpm.showPlayer"
    }
}
