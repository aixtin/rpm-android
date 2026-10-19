package com.rpm.player.core.home

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import kotlin.coroutines.resume

/** A computer on the network that shares its library. The values come from its beacon, after validation. */
data class FoundComputer(
    /** As written in the beacon. The session signature covers this exact text. */
    val deviceId: String,
    val name: String,
    val address: String,
    val port: Int,
    val publicKey: ByteArray,
    val account: String,
) {
    val key: String get() = deviceId.uppercase()
    val base: String get() = "https://$address:$port"

    override fun equals(other: Any?): Boolean =
        other is FoundComputer && deviceId == other.deviceId && name == other.name && address == other.address &&
            port == other.port && account == other.account && publicKey.contentEquals(other.publicKey)

    override fun hashCode(): Int = deviceId.hashCode() * 31 + port
}

/**
 * Computers that share their library on the network, found from `_rpm._tcp` beacons, as `PeerBeacon` reads
 * them. `v` must be 3, `k` is the key (base64url SubjectPublicKeyInfo), and `id` must be the id that the key
 * gives, otherwise the beacon is dropped. A computer's port changes whenever its server restarts, so a new
 * connection must use the latest resolved address. Each address is pinned to its key.
 *
 * Android 9's `NsdManager` resolves one service at a time, so resolves are queued.
 */
class Discovery(context: Context, private val trust: PeerTrust, private val scope: CoroutineScope) {

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private val _found = MutableStateFlow<Map<String, FoundComputer>>(emptyMap())

    /** By upper-case device id. */
    val found: StateFlow<Map<String, FoundComputer>> = _found.asStateFlow()

    /** Service name to device id, so that a lost service can be removed. */
    private val names = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val browsing = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            queue.trySend(info)
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            val id = names.remove(info.serviceName) ?: return
            _found.update { it - id }
        }

        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    fun start() {
        nsd.discoverServices(PeerWire.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, browsing)
        scope.launch(Dispatchers.IO) {
            for (info in queue) resolve(info)?.let { (name, computer) ->
                trust.pin(computer.address, computer.publicKey)
                names[name] = computer.key
                _found.update { it + (computer.key to computer) }
            }
        }
    }

    private suspend fun resolve(info: NsdServiceInfo): Pair<String, FoundComputer>? {
        val resolved = suspendCancellableCoroutine<NsdServiceInfo?> { done ->
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(service: NsdServiceInfo) = done.resume(service)
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) = done.resume(null)
            })
        } ?: return null
        val text = resolved.attributes.entries.associate { (k, v) -> k.lowercase() to v?.toString(Charsets.UTF_8).orEmpty() }
        if (text["v"]?.trim() != PeerWire.VERSION.toString()) return null
        val key = PeerWire.fromBase64Url(text["k"]) ?: return null
        val id = text["id"]?.trim().orEmpty()
        if (!id.equals(PeerWire.idFor(key), ignoreCase = true)) return null
        @Suppress("DEPRECATION")
        val address = (resolved.host as? Inet4Address)?.hostAddress ?: return null
        val name = text["d"]?.takeIf { it.isNotBlank() } ?: text["n"]?.takeIf { it.isNotBlank() } ?: id
        return info.serviceName to FoundComputer(id, name, address, resolved.port, key, text["a"].orEmpty())
    }
}
