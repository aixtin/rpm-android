package com.rpm.player.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The values come from running `RPM.Core/Sharing/PairingCode.cs` itself on the same inputs, so the TV and the
 * desktop agree on every digit and commitment of pairing version 2. */
class PairingCodeTest {
    private fun bytes(start: Int, n: Int) = ByteArray(n) { (start + it * 7).toByte() }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private val asker = bytes(0x30, 91)
    private val owner = bytes(0x90, 91)
    private val askerNonce = bytes(1, 16)
    private val ownerNonce = bytes(200, 16)

    @Test
    fun `the digits are the desktop's, whichever key comes first`() {
        assertEquals("594660", PairingCode.of(asker, owner, askerNonce, ownerNonce))
        assertEquals("594660", PairingCode.of(owner, asker, ownerNonce, askerNonce))
    }

    @Test
    fun `the commitments and the round nonce are the desktop's`() {
        assertEquals("7325859e53a00b439cc42fce55e632ffa65046417e9776ce7e6b4e7711db27aa", hex(PairingCode.commitNonce(ownerNonce)))
        assertEquals(
            "959c711899fe532e96806c4028fe99cd4ecf67c427fbea84d543f75b1aa4b229",
            hex(PairingCode.commitCode(asker, owner, bytes(50, 16), "004213")),
        )
        assertEquals("a6d2be4457c8c69a8245f42593897bdb", hex(PairingCode.askerNonce(askerNonce, 3)))
    }

    @Test
    fun `same refuses what is missing or different`() {
        assertTrue(PairingCode.same(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(PairingCode.same(byteArrayOf(), byteArrayOf()))
        assertFalse(PairingCode.same(null, byteArrayOf(1)))
        assertFalse(PairingCode.same("004213", "004214"))
    }
}
