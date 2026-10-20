package com.rpm.player.core.playback

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // OK with the controls hidden only shows them: it used to pause, which stopped the music for someone who
    // only wanted to see the keys.
    @Test fun withControlsHiddenOkNeverPlaysOrPauses() {
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_ENTER))
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_NUMPAD_ENTER))
    }

    @Test fun withControlsHiddenOkUpAndDownBringThemUp() {
        assertTrue(KeyRules.raisesControls(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(KeyRules.raisesControls(KeyEvent.KEYCODE_ENTER))
        assertTrue(KeyRules.raisesControls(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(KeyRules.raisesControls(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(KeyRules.raisesControls(KeyEvent.KEYCODE_DPAD_LEFT))
        assertFalse(KeyRules.raisesControls(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    // The remote's own play/pause key still toggles, with the controls hidden or not.
    @Test fun thePlayPauseKeyStillToggles() {
        assertEquals(PlayerAction.PAUSE, KeyRules.forMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, playing = true))
        assertEquals(PlayerAction.PLAY, KeyRules.forMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, playing = false))
    }

    @Test fun withControlsHiddenTheArrowsSeek() {
        assertEquals(PlayerAction.FORWARD, KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(PlayerAction.BACK, KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test fun upAndDownAreLeftToTheScreen() {
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(KeyRules.forHiddenControls(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test fun aSignedUrlWithFiveMinutesOrLessLeftIsNotOpened() {
        val now = 1_000_000L
        assertEquals(true, StreamLinks.usable(now + StreamLinks.MIN_LEFT_MS + 1, now))
        assertEquals(false, StreamLinks.usable(now + StreamLinks.MIN_LEFT_MS, now))
    }
}
