package com.rpm.player.phone.downloads

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import com.rpm.player.core.Services
import com.rpm.player.core.net.NetworkReach
import com.rpm.player.core.net.Reach
import com.rpm.player.core.playback.AudioLadder
import com.rpm.player.phone.PhoneSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import com.rpm.player.core.cloud.Shelf
import com.rpm.player.core.downloads.AlbumDownloads
import com.rpm.player.core.downloads.DownloadBook
import com.rpm.player.core.downloads.OkHttpTransfer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * The phone's downloads: the book and the downloader from `:core`, wired as the iPhone's `AppModel` wires its
 * `AlbumDownloads`, and kept going by [DownloadService].
 *
 * The book is made before the app's services, because the player reads it ([Services]'s copies) to play a downloaded
 * track from its file. The downloader is made after them, because it signs through the cloud client.
 */
object Downloads {
    /** The folder the files and the manifest are kept in. Not backed up: see [DownloadBook]. */
    fun book(context: Context): DownloadBook = DownloadBook(File(context.noBackupFilesDir, "downloads"))

    /**
     * The downloader. It signs each track as one of the account's own ([Shelf.Own]): a download is never made from a
     * shelf another account shares, which is why the key is only on the person's own records.
     */
    fun make(services: Services, settings: PhoneSettings, reach: NetworkReach, book: DownloadBook): AlbumDownloads {
        val cloud = services.cloud
        return AlbumDownloads(
            book = book,
            online = { reach.now.value.isOnline },
            // The phone's reading keeps no doubt: a request that found the line dead is tried again after a while.
            doubt = {},
            sign = { rpmId, kilobits ->
                val ticket = cloud.stream(Shelf.Own, rpmId, kilobits)
                AlbumDownloads.Signed(ticket.url, ticket.kilobits ?: kilobits, ticket.live == true)
            },
            make = { rpmIds, kilobits -> cloud.makeRenditions(rpmIds, kilobits) },
            transfer = OkHttpTransfer(services.http),
            // The download setting, and the store decides the rest: it limits a free account whatever is asked, and Data
            // Saver is not applied, because a download is a choice to spend the data. A stored value that is not on
            // the ladder gives the original, as `AudioLadder.RateOffer.unread.settles(on:)` does on the iPhone.
            rate = { AudioLadder.RateOffer.UNREAD.settles(settings.rateForDownloads) },
            scope = services.scope,
        )
    }

    /**
     * What keeps the downloads going, for the life of the process, as the iPhone's `AppModel` does:
     *
     * - the network coming back asks for what could not be signed while it was away (`theLineCameBack`), and so does a
     *   change of the download rate, so the tracks still to come are signed at the new rate now;
     * - work to do starts [DownloadService], which keeps the process in the foreground until the work is done;
     * - the files belong to the account signed in, and another account signing in on this phone does not find them.
     */
    fun keepGoing(context: Context, services: Services, settings: PhoneSettings, line: StateFlow<Reach>, downloads: AlbumDownloads) {
        val app = context.applicationContext
        services.scope.launch {
            var was = line.value.isOnline
            line.collect { now ->
                if (!was && now.isOnline) downloads.pump()
                was = now.isOnline
            }
        }
        services.scope.launch {
            snapshotFlow { settings.rateForDownloads }.drop(1).collect { downloads.pump() }
        }
        services.scope.launch {
            downloads.busy.collect { busy -> if (busy) DownloadService.start(app) }
        }
        services.scope.launch {
            services.auth.session.map { it?.userId }.distinctUntilChanged().collect { userId ->
                if (userId != null) downloads.belongTo(userId)
            }
        }
    }

    /**
     * The settled reading: what the offline bar shows and what decides what plays. **Online is believed at once;
     * offline has to last** [SETTLING_MS], as the iPhone's `NetworkReach` waits before it publishes an offline path.
     * A handover from Wi-Fi to mobile data, or a phone waking up, loses the network for a moment and gets it back by
     * itself; publishing that would flash the bar and switch the screen to what plays offline. The reading at the end
     * of the wait is the one that counts.
     */
    fun settle(now: StateFlow<Reach>, scope: CoroutineScope): StateFlow<Reach> {
        val told = MutableStateFlow(now.value)
        scope.launch {
            now.collectLatest { reading ->
                if (!reading.isOnline && told.value.isOnline) delay(SETTLING_MS)
                told.value = reading
            }
        }
        return told.asStateFlow()
    }

    /** How long the line must look gone before the phone says so. */
    const val SETTLING_MS = 1200L
}
