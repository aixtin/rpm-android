package com.rpm.player.core.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How far the device can reach, as the iPhone app's `Reach` (RPMKit/Net). The rate a track is asked for is chosen from
 * it ([com.rpm.player.core.playback.AudioLadder.RateOffer.settles]).
 *
 * @property costsData whether each byte costs money: a metered network. A hotspot from another phone is Wi-Fi and
 * still metered, so the question is what a byte costs, not which radio carries it.
 * @property isConstrained the person has asked the system to use less data: Data Saver, Android's Low Data Mode. Data
 * Saver restricts metered networks only, so it counts only on one.
 */
data class Reach(val isOnline: Boolean, val costsData: Boolean, val isConstrained: Boolean) {
    companion object {
        /**
         * The belief before the first network is reported. `costsData` starts true, so a track started in that moment
         * does not spend mobile data by mistake. `isOnline` starts true, so the first request, the one a person is most
         * likely to be waiting for, is not refused. No single starting value is safe for both.
         */
        val UNKNOWN = Reach(isOnline = true, costsData = true, isConstrained = false)
    }
}

/**
 * Watches the default network and Data Saver, and keeps [now] up to date. Made by an app that chooses rates (the
 * phone). It is not started by `:core` itself, so the TV, which always asks for one rate, runs no watcher.
 */
class NetworkReach(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val current = MutableStateFlow(Reach.UNKNOWN)

    /** What the default network is now. */
    val now: StateFlow<Reach> = current.asStateFlow()

    @Volatile private var capabilities: NetworkCapabilities? = null
    @Volatile private var online = true

    /** The default network the readings are about. The loss of any other one is not news. */
    @Volatile private var network: Network? = null
    private var started = false

    /** Starts watching. Called once, on the main thread. */
    fun begin() {
        if (started) return
        started = true
        // With no network at all the system calls nothing, so a launch with none would stay online. Read before the
        // callback is registered, so a network it reports is not overwritten.
        if (connectivity.activeNetwork == null) online = false
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    this@NetworkReach.network = network
                    capabilities = caps
                    online = true
                    publish()
                }

                override fun onLost(network: Network) {
                    // A handover can report the old network lost after the new one has arrived.
                    if (this@NetworkReach.network != null && network != this@NetworkReach.network) return
                    capabilities = null
                    online = false
                    publish()
                }
            },
        )
        // Data Saver is a setting, not a property of the network, and it is announced by broadcast only.
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = publish()
            },
            IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        publish()
    }

    private fun publish() {
        val caps = capabilities
        // No network reported yet: metered, as in [Reach.UNKNOWN].
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        val saver = connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        current.value = Reach(isOnline = online, costsData = metered, isConstrained = saver && metered)
    }
}
