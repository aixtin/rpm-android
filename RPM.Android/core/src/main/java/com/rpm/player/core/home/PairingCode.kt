package com.rpm.player.core.home

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The six digits and the commitments of pairing version 2 (`PeerWire.PAIRING`), as `RPM.Core/Sharing/PairingCode.cs`
 * makes them. The computer shows the digits; the TV, which asks, never sends them: it commits to what was typed,
 * checks the computer's revealed nonce against the commitment it sent first, and opens its own commitment only when
 * the digits agree. Every value must be byte for byte the desktop's.
 */
object PairingCode {
    /** How many random bytes each side contributes. */
    const val NONCE_BYTES = 16

    private val random = SecureRandom()

    fun nonce(): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

    /** The number both screens must show: six digits, leading zeros kept. The keys are sorted (as unsigned bytes)
     * so both ends hash the same bytes, and each nonce stays with its key. */
    fun of(publicKeyA: ByteArray, publicKeyB: ByteArray, nonceA: ByteArray, nonceB: ByteArray): String {
        val aFirst = compare(publicKeyA, publicKeyB) <= 0
        val digest = MessageDigest.getInstance("SHA-256").run {
            if (aFirst) { update(publicKeyA); update(nonceA); update(publicKeyB); update(nonceB) }
            else { update(publicKeyB); update(nonceB); update(publicKeyA); update(nonceA) }
            digest()
        }
        val value = ((digest[0].toInt() and 0xFF shl 16) or (digest[1].toInt() and 0xFF shl 8) or (digest[2].toInt() and 0xFF)) and 0xFFFFF
        return (value % 1_000_000).toString().padStart(6, '0')
    }

    /** The computer's commitment to its nonce for a round. */
    fun commitNonce(nonce: ByteArray): ByteArray = hash("rpm-pair-nonce", nonce)

    /** The TV's commitment to the typed code, bound to both keys as the TV sees them. */
    fun commitCode(askerKey: ByteArray, ownerKey: ByteArray, opening: ByteArray, code: String): ByteArray =
        hash("rpm-pair-code", askerKey, ownerKey, opening, code.toByteArray(Charsets.UTF_8))

    /** The TV's nonce for one round, derived from a seed that is never sent, so two sends for one round send the
     * same bytes. */
    fun askerNonce(seed: ByteArray, round: Int): ByteArray =
        hash("rpm-pair-asker-nonce", seed, ByteBuffer.allocate(4).putInt(round).array()).copyOf(NONCE_BYTES)

    /** Whether two hashes are the same, in constant time; false when either is missing. */
    fun same(a: ByteArray?, b: ByteArray?): Boolean =
        a != null && b != null && a.isNotEmpty() && b.isNotEmpty() && MessageDigest.isEqual(a, b)

    /** Whether two codes are the same, in constant time. */
    fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /** SHA-256 over a label and each part with its length (4 bytes, big-endian) in front. */
    private fun hash(label: String, vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update(label.toByteArray(Charsets.US_ASCII))
        for (part in parts) {
            update(ByteBuffer.allocate(4).putInt(part.size).array())
            update(part)
        }
        digest()
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i].toInt() and 0xFF
            val y = b[i].toInt() and 0xFF
            if (x != y) return x.compareTo(y)
        }
        return a.size.compareTo(b.size)
    }
}
