package com.rpm.player.core.room

/**
 * Where along its drift a room starts: the iPhone's `RoomPose` (RPMStages/RoomPose.swift), with the same hash and
 * the same span.
 *
 * The room's figures are a function of its clock and nothing else, so rooms that start together, or share one clock
 * (the home banner's cards), would stand in the same pose and move in step. Each room therefore starts a distance
 * along its drift picked from what it shows. It is picked from a name and not at random, so two views of one card
 * draw the same frame, and a room scrolled away and back starts from the pose it had.
 */
object RoomPose {
    /** How far along a room can start, in the room's own seconds. */
    const val SPAN = 10_000.0

    /** How far along the room named [seed] starts, from 0 up to [SPAN]: FNV-1a over the name's UTF-8, the same on
     * every launch and every device, and the same number the iPhone gives the same name. */
    fun offset(seed: String): Double {
        var hash = 0xCBF29CE484222325uL
        for (byte in seed.encodeToByteArray()) {
            hash = hash xor (byte.toUByte().toULong())
            hash *= 0x00000100000001B3uL
        }
        return (hash % 10_000_000uL).toDouble() / 10_000_000.0 * SPAN
    }
}
