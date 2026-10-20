package com.rpm.player.phone.signin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The browser's way back after a sign-in with Apple or Google: `rpm://auth?sessionId=…&code=…` ([SignIn.codeIn]). Only
 * a link for the sign-in that is waiting gives a code. */
class SignInLinkTest {
    @Test fun theCodeOfTheWaitingSignInIsRead() =
        assertEquals("x y&z", SignIn.codeIn("rpm://auth?sessionId=abc&code=x%20y%26z", "abc"))

    @Test fun aLinkForAnotherSignInGivesNothing() =
        assertNull(SignIn.codeIn("rpm://auth?sessionId=other&code=xyz", "abc"))

    @Test fun aLinkWithoutACodeGivesNothing() {
        assertNull(SignIn.codeIn("rpm://auth?sessionId=abc", "abc"))
        assertNull(SignIn.codeIn("rpm://auth?sessionId=abc&code=", "abc"))
    }

    @Test fun anotherAddressGivesNothing() {
        assertNull(SignIn.codeIn("https://auth?sessionId=abc&code=xyz", "abc"))
        assertNull(SignIn.codeIn("rpm://other?sessionId=abc&code=xyz", "abc"))
        assertNull(SignIn.codeIn("not a uri at all", "abc"))
    }
}
