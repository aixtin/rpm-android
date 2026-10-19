package com.rpm.player.core.playback

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyRulesTest {

    // The Amiko's only play button arrives as MEDIA_PAUSE.
    @Test fun aPauseKeyResumesWhenPaused() {
        assertEquals(PlayerAction.PLAY, KeyRules.forMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, playing = false))
        assertEquals(PlayerAction.PAUSE, KeyRules.forMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, playing = true))
    }

    @Test fun aPlayKeyOnlyPlays() {
        assertEquals(PlayerAction.PLAY, KeyRules.forMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, playing = true))
    }

    @Test fun closeIsAStop() {
        assertEquals(PlayerAction.STOP, KeyRules.forMediaKey(KeyEvent.KEYCODE_MEDIA_CLOSE, playing = true))
        assertEquals(PlayerAction.STOP, KeyRules.forMediaKey(KeyEvent.KEYCODE_MEDIA_STOP, playing = true))
    }

    @Test fun anArrowIsNotAMediaKey() {
        assertNull(KeyRules.forMediaKey(KeyEvent.KEYCODE_DPAD_RIGHT, playing = true))
    }

    @Test fun withControlsHiddenOkTogglesOnceHowEverLongItIsHeld() {
        assertEquals(PlayerAction.PAUSE, KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 0, playing = true))
        assertEquals(PlayerAction.PLAY, KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 0, playing = false))
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 3, playing = true))
    }

    @Test fun withControlsHiddenAHeldArrowKeepsSeeking() {
        assertEquals(PlayerAction.FORWARD, KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 0, playing = true))
        assertEquals(PlayerAction.FORWARD, KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 7, playing = true))
        assertEquals(PlayerAction.BACK, KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_LEFT, repeat = 0, playing = false))
    }

    @Test fun upAndDownAreLeftToTheScreen() {
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_UP, repeat = 0, playing = true))
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 0, playing = true))
    }

    @Test fun aSignedUrlWithFiveMinutesOrLessLeftIsNotOpened() {
        val now = 1_000_000L
        assertEquals(true, StreamLinks.usable(now + StreamLinks.MIN_LEFT_MS + 1, now))
        assertEquals(false, StreamLinks.usable(now + StreamLinks.MIN_LEFT_MS, now))
    }
}
