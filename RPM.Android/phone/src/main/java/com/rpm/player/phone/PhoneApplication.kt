package com.rpm.player.phone

import android.content.Context
import android.content.Intent
import com.rpm.player.core.RpmApplication
import com.rpm.player.core.Services
import com.rpm.player.core.SessionOpening
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rpm.player.core.library.AlbumOrdering
import com.rpm.player.core.library.ArtistOrdering
import com.rpm.player.core.library.ArtistTrackGrouping
import com.rpm.player.core.playback.LevelMode
import com.rpm.player.core.playback.Levelling
import com.rpm.player.phone.art.Logos
import com.rpm.player.phone.art.Portraits
import com.rpm.player.phone.library.LibraryMode
import com.rpm.player.phone.signin.SignIn

class PhoneApplication : RpmApplication() {
    /**
     * The phone's settings. Made in [makeServices] before the services, because the services need its levelling
     * setting.
     */
    lateinit var settings: PhoneSettings
        private set

    /**
     * The sign-in flow. It is held here, not in the activity, because the activity can be destroyed
     * while the browser is open.
     */
    val signIn: SignIn by lazy {
        SignIn(services.auth, services.scope, bringBack = ::bringToFront)
    }

    /** Artists' photographs, cropped around their faces. Kept for the life of the app. */
    val portraits: Portraits by lazy { Portraits(this, services.images, services.artwork) }

    /** Artists' logos, trimmed and kept for the life of the app. */
    val logos: Logos by lazy { Logos(this, services.images, services.artwork) }

    override fun makeServices(): Services {
        settings = PhoneSettings(this)
        return Services(this, settings.levelling, com.rpm.player.core.radio.TunerOpensOn.EveryCountry)
    }

    override fun sessionOpening(): SessionOpening = SessionOpening.Player(
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_SHOW_PLAYER, true),
    )

    /**
     * Brings the app back over the browser it opened. The Custom Tab runs in the app's task, above the
     * activity, and `FLAG_ACTIVITY_CLEAR_TOP` closes it. Starting an activity from the background is
     * allowed because the app has an activity in the task that is in front.
     */
    private fun bringToFront() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }
}

/**
 * The settings the phone keeps for the person. The keys match the ones the iPhone app stores.
 *
 * Volume levelling (`Prefs`): the defaults match albums and leave classical records alone. The value
 * is read once, as the app starts. There is no screen to change it yet.
 *
 * The library's order and view (`library.order`, `library.mode`) are kept across launches. They are
 * read on the main thread as the app starts. They are written with `apply()`, which writes in the
 * background, so a process killed before the write finishes keeps the older value.
 */
class PhoneSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var libraryOrder by mutableStateOf(AlbumOrdering.of(prefs.getString(KEY_ORDER, null)) ?: AlbumOrdering.Artist)
        private set

    var libraryMode by mutableStateOf(LibraryMode.of(prefs.getString(KEY_LIBRARY_MODE, null)) ?: LibraryMode.Albums)
        private set

    fun changeLibraryOrder(order: AlbumOrdering) {
        libraryOrder = order
        prefs.edit().putString(KEY_ORDER, order.stored).apply()
    }

    var artistOrder by mutableStateOf(ArtistOrdering.entries.firstOrNull { it.name == prefs.getString(KEY_ARTIST_ORDER, null) } ?: ArtistOrdering.Name)
        private set

    var trackGrouping by mutableStateOf(ArtistTrackGrouping.of(prefs.getString(KEY_TRACK_GROUPING, null)) ?: ArtistTrackGrouping.Letter)
        private set

    fun changeArtistOrder(order: ArtistOrdering) {
        artistOrder = order
        prefs.edit().putString(KEY_ARTIST_ORDER, order.name).apply()
    }

    fun changeTrackGrouping(grouping: ArtistTrackGrouping) {
        trackGrouping = grouping
        prefs.edit().putString(KEY_TRACK_GROUPING, grouping.stored).apply()
    }

    fun changeLibraryMode(mode: LibraryMode) {
        libraryMode = mode
        prefs.edit().putString(KEY_LIBRARY_MODE, mode.stored).apply()
    }

    val levelling = Levelling(
        prefs.getString(KEY_MODE, null)?.let { name -> LevelMode.entries.firstOrNull { it.name == name } } ?: LevelMode.Album,
        prefs.getBoolean(KEY_CLASSICAL, false),
    )

    private companion object {
        const val FILE = "rpm.phone"
        const val KEY_MODE = "levelling.mode"
        const val KEY_CLASSICAL = "levelling.classical"
        const val KEY_ORDER = "library.order"
        const val KEY_LIBRARY_MODE = "library.mode"
        const val KEY_ARTIST_ORDER = "library.artistOrder"
        const val KEY_TRACK_GROUPING = "artist.trackGrouping"
    }
}
