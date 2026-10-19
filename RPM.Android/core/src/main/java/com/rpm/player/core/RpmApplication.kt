package com.rpm.player.core

import android.app.Application
import android.content.Context
import android.content.Intent

/**
 * The application object of an rpm app on Android. Each app (the TV, the phone) extends it, makes its
 * [Services] in [makeServices], and says in [sessionOpening] how the system's media surfaces open it.
 *
 * Abstract, so an app cannot be declared without answering both: the playback service lives in this
 * module and has no other way to know which app it is running in.
 */
abstract class RpmApplication : Application() {
    lateinit var services: Services
        private set

    override fun onCreate() {
        super.onCreate()
        services = makeServices()
    }

    /** Makes the app-wide objects. Called once, from [onCreate], on the main thread. */
    protected abstract fun makeServices(): Services

    /** How the launcher's now playing card and the media notification open this app. The playback service reads it
     * once, when it builds the media session. */
    abstract fun sessionOpening(): SessionOpening
}

/** How the system's media surfaces open the app, answered by each app ([RpmApplication.sessionOpening]). */
sealed interface SessionOpening {
    /** The session names [intent] as its activity, and a surface sends it: the app opens on its player. The phone's
     * notification does this from the system's own window, which Android lets start an activity. */
    data class Player(val intent: Intent) : SessionOpening

    /**
     * The session names no activity, and a surface opens the app's launcher entry itself. Android TV's now playing card
     * needs this: its service (`com.google.android.tvrecommendations`) has no window, and Android 9 to 11 refuse to bring
     * a background app to the front from an activity that the app named and such a service sent ("Background activity
     * start" in the log: the TV home stays in front). With no activity named, the service builds the launch itself, as
     * a system app, and that is allowed.
     */
    data object LauncherEntry : SessionOpening
}

val Context.services: Services get() = (applicationContext as RpmApplication).services
