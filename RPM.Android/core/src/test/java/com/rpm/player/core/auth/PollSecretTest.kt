package com.rpm.player.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The poll secret sent with a social sign-in. The hash must be the one the server computes
 * (rpm.Api `PollSecret.HashOf`), or the server refuses this app's own poll. */
class PollSecretTest {
    @Test
    fun theHashIsTheUnpaddedBase64urlSha256OfTheSecret() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0", PollSecret.hashOf("abc"))
    }

    @Test
    fun aSecretIs43Base64urlCharactersAndNewEachTime() {
        val a = PollSecret.make()
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(a, PollSecret.make())
    }
}
