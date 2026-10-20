package com.rpm.player.core.home

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The values were worked out with a line-for-line port of `PeerIdentity` and `Base32` from RPM.Core. */
class PeerWireTest {

    @Test
    fun `a device id is base32 of the first 16 bytes of the key's SHA-256`() {
        val key = ByteArray(91) { it.toByte() }
        assertEquals("B3998PDJ2C52W58JABA0HEAYDM", PeerWire.idFor(key))
        assertEquals(26, PeerWire.idFor(key).length)
    }

    @Test
    fun `base64url has no padding and no plus or slash, and refuses what is not base64url`() {
        assertEquals("-_8AAQI", PeerWire.toBase64Url(byteArrayOf(-5, -1, 0, 1, 2)))
        assertArrayEquals(byteArrayOf(-5, -1, 0, 1, 2), PeerWire.fromBase64Url("-_8AAQI"))
        assertNull(PeerWire.fromBase64Url("not base 64 url!!"))
    }

    @Test
    fun `a DER signature becomes r and s of 32 bytes each`() {
        // r has the leading zero that DER adds to keep it positive; s is shorter than 32 bytes.
        val r = ByteArray(32) { (0x80 + it).toByte() }
        val s = ByteArray(30) { (it + 1).toByte() }
        val der = byteArrayOf(0x30, (2 + 33 + 2 + 30).toByte(), 0x02, 33, 0x00) + r + byteArrayOf(0x02, 30) + s
        val raw = PeerWire.derToP1363(der)
        assertEquals(64, raw.size)
        assertArrayEquals(r, raw.copyOfRange(0, 32))
        assertArrayEquals(ByteArray(2) + s, raw.copyOfRange(32, 64))
    }

    @Test
    fun `the session text is the purpose, the server's id, the challenge and the account on four lines`() {
        assertEquals("rpm-share-session\nSERVER\nCH\nuser-7", PeerWire.sessionText("SERVER", "CH", "user-7").toString(Charsets.UTF_8))
    }

    @Test
    fun `handles and tickets match the desktop`() {
        // The same values as PeerSessionsTests.Handles_and_tickets_match_the_phones on the desktop.
        assertEquals("ufGG_y6PQoD1A0zS7jm0xg", PeerWire.mediaHandle("token-1"))
        assertEquals("pfexHfz9I4_UxxVAiaRSMw", PeerWire.mediaTicket("token-1", "/rpm/track/", "abc"))
    }
}
