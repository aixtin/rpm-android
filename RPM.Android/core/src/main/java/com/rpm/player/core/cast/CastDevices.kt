package com.rpm.player.core.cast

/** A Cast device on the network: a speaker, a speaker group, a Chromecast or a TV with Cast built in. */
data class CastDevice(
    /** What identifies the device: the advertised `id`, or `host:port` if there is none. Never the address alone
     * (see [CastDevices]). */
    val id: String,
    /** The name the person gave it (`fn`). */
    val name: String,
    /** The model the device reports (`md`), for example `Google Home Mini`, `Chromecast` or `Google Cast Group`. */
    val model: String,
    val host: String,
    val port: Int,
    /** A speaker group or a stereo pair. Google refuses a cast to a member of either (`NOT_ALLOWED`), so the
     * group is what the menu must offer. */
    val isGroup: Boolean,
    /** What the device says it can do (`ca`), a bitmask; 0 when it does not say. */
    val capabilities: Int = 0,
) {
    /**
     * Whether the device shows pictures: a Chromecast, a Google TV, a TV with Cast built in, a Nest Hub. Read from
     * the video out bit of [capabilities], which speakers and groups never set; a device that advertises no `ca`
     * is judged by its model's name instead. The menu draws a screen or a speaker by it.
     */
    val hasScreen: Boolean
        get() = if (capabilities != 0) (capabilities and CA_VIDEO_OUT) != 0
        else !isGroup && SCREEN_MODELS.any { model.contains(it, ignoreCase = true) }

    companion object {
        /** TXT record keys: the friendly name, the model, the id that stays the same when the address changes,
         * and the capabilities. */
        const val KEY_NAME = "fn"
        const val KEY_MODEL = "md"
        const val KEY_ID = "id"
        const val KEY_CAPABILITIES = "ca"

        /** The capability bit of a device with a picture out (Cast's `VIDEO_OUT`). */
        const val CA_VIDEO_OUT = 1

        /** Words in the models of devices with a screen, for a device without `ca`. */
        private val SCREEN_MODELS = listOf("chromecast", "tv", "shield", "hub", "display")

        /** A device from its resolved service, or null if the service has no address. */
        fun of(txt: Map<String, String>, host: String?, port: Int): CastDevice? {
            if (host.isNullOrBlank() || port <= 0) return null
            val advertised = txt[KEY_ID]?.trim().orEmpty()
            val model = txt[KEY_MODEL]?.trim().orEmpty()
            val name = txt[KEY_NAME]?.trim()?.takeIf { it.isNotEmpty() } ?: host
            return CastDevice(
                id = advertised.ifEmpty { "$host:$port" },
                name = name,
                model = model,
                host = host,
                port = port,
                // A group and a stereo pair both advertise `Google Cast Group` as their model.
                isGroup = model.contains("group", ignoreCase = true),
                capabilities = txt[KEY_CAPABILITIES]?.trim()?.toIntOrNull() ?: 0,
            )
        }
    }
}

/**
 * The devices that discovery has found. It follows the rules of `RPM.Core/Audio/CastDeviceIdentity.cs` and the
 * phone's `CastDevice.swift`. It has no side effects, so the rules can be tested without a network.
 *
 * Devices are keyed by the advertised id, never by the address: **a speaker group runs on one of its member
 * speakers, at that member's address on another port**. A household can have a speaker and two groups at one
 * address. If they were keyed by address, they would become one entry, and the groups (the only ones that can
 * be cast to) would be lost. A lost service removes the device it named. A device that is resolved again at a
 * new address replaces its entry.
 */
class CastDevices {
    private val byId = LinkedHashMap<String, CastDevice>()

    /** Service name to device id, so that a lost service can be removed. */
    private val ids = HashMap<String, String>()

    /** A service resolved to [device]. */
    fun found(serviceName: String, device: CastDevice) {
        ids.put(serviceName, device.id)?.takeIf { it != device.id }?.let(::forgetIfUnnamed)
        byId[device.id] = device
    }

    /** A service is gone. Its device is removed too, unless another service still names it. */
    fun lost(serviceName: String) {
        val id = ids.remove(serviceName) ?: return
        forgetIfUnnamed(id)
    }

    fun clear() {
        byId.clear()
        ids.clear()
    }

    /** The devices by name, as the menu lists them. */
    fun list(): List<CastDevice> = byId.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private fun forgetIfUnnamed(id: String) {
        if (id !in ids.values) byId.remove(id)
    }

    companion object {
        /**
         * What the menu shows: the devices discovery has, plus the device being cast to, whatever discovery says
         * about it. A speaker that drops out of a browse is still the one playing, and the menu must keep it
         * ticked.
         */
        fun menu(found: List<CastDevice>, current: CastDevice?): List<CastDevice> {
            if (current == null || found.any { it.id == current.id }) return found
            return (found + current).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }
}
