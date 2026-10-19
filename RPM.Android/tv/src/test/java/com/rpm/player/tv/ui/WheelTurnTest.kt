package com.rpm.player.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelTurnTest {

    @Test
    fun `a turn lands on the next entry after 220 ms`() {
        val turn = WheelTurn(0f)
        turn.by(1, 5)
        turn.tick(0.11f)
        turn.tick(0.11f)
        assertEquals(1f, turn.drawnAt, 1e-4f)
    }

    @Test
    fun `a second turn mid-way adds up instead of restarting from the first target`() {
        val turn = WheelTurn(0f)
        turn.by(1, 5)
        turn.tick(0.05f)
        turn.by(1, 5)
        assertEquals(2, turn.target)
        turn.tick(0.3f)
        assertEquals(2f, turn.drawnAt, 1e-4f)
    }

    @Test
    fun `past the end it leans at once, keeps its entry, and the lean decays to nothing`() {
        val turn = WheelTurn(4f)
        turn.by(1, 5)
        assertEquals(4, turn.target)
        assertEquals(4.18f, turn.drawnAt, 1e-4f)
        turn.tick(0.2f)
        // 0.18 x 0.002^0.2 is about 0.052.
        assertEquals(0.052f, turn.drawnAt - 4f, 0.001f)
        turn.tick(1f)
        assertEquals(4f, turn.drawnAt, 1e-6f)
    }
}
