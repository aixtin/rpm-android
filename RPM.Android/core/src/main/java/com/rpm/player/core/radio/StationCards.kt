package com.rpm.player.core.radio

import com.rpm.player.core.net.Hosts

/**
 * The stations with a card picture: a 16:9 picture the admin chose for the station's card in the essential row.
 * It is listed by `radio/state` under every row of the station and served at `radio/card/{uuid}`.
 * [stamp] moves whenever a picture changes and goes on the address, so a changed picture is a new address.
 */
data class StationCards(val stations: Set<String>, val stamp: String) {
    /** The address of [station]'s card picture: its own entry's, else the first folded entry's that has one, as
     * edits are looked up ([StationRules.editOf]). Null where it has none. */
    fun addressOf(station: RadioStation): String? {
        if (stations.isEmpty()) return null
        val uuid = sequenceOf(station.uuid).plus(station.alsoFiledAs).map { it.lowercase() }.firstOrNull { it in stations }
            ?: return null
        return Hosts.API + "radio/card/" + uuid + (if (stamp.isNotEmpty()) "?v=$stamp" else "")
    }

    companion object {
        val NONE = StationCards(emptySet(), "")
    }
}
