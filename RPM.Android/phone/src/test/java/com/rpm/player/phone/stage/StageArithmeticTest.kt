package com.rpm.player.phone.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The parts of the stage that can be proved right without a screen, as the iPhone's `RPMStagesTests` prove them: the
 * cutting room, the spectrum and the plate.
 */
class StageArithmeticTest {

    /** A seek arrives at the shot that playing through would have reached: the flight is derived, not accumulated. */
    @Test fun theFlightIsAddedUpRatherThanMultiplied() {
        var walked = 0.0
        var beat = 0.0
        while (beat < 200) {
            walked += VizDirector.shot(beat, 4).advance * 0.01
            beat += 0.01
        }
        assertEquals(walked, VizDirector.travelled(200.0).toDouble(), 1.0)
    }

    @Test fun itAlwaysFliesForwards() {
        var last = VizDirector.travelled(-10.0)
        for (i in -26..600) {
            val now = VizDirector.travelled(i * 0.37)
            assertTrue("went backwards at beat ${i * 0.37}", now > last)
            last = now
        }
    }

    @Test fun thePulseIsStruckOnEveryBeat() {
        fun pulse(beats: Double) = VizDirector.shot(beats, 4).pulse
        assertTrue(pulse(3.0) > 0.99f)
        assertTrue(pulse(3.5) < pulse(3.05))
        assertTrue(pulse(3.99) < 0.01f)
    }

    @Test fun aRoomIsTheSameRoomEveryTimeAndAWholeNumberOfFolds() {
        assertEquals(VizDirector.room(8), VizDirector.room(8))
        assertNotEquals(VizDirector.room(8), VizDirector.room(9))
        for (section in -20..200) {
            val room = VizDirector.room(section)
            assertEquals(Math.round(room.folds).toFloat(), room.folds, 0f)
            assertTrue(room.folds in 3f..9f)
            assertTrue(room.arm > 2 && room.arm < 5)
            assertTrue(room.girth > 0.03f && room.girth < 0.12f)
        }
    }

    @Test fun theSectionsAreSpreadOverTheFourPalettes() {
        val seen = (0 until 64).map { VizDirector.shot(it * 32.0 + 1, 4).palette }.toSet()
        assertTrue(seen.all { it in 0 until VizDirector.PALETTES })
        assertEquals(VizDirector.PALETTES, seen.size)
    }

    /** A tone lands in its own bin of the spectrum row, and silence eases the row down rather than cutting it. */
    @Test fun aToneLandsInItsBinAndSilenceEasesDown() {
        val audio = ShaderAudio()
        val frame = FloatArray(2048) { (0.5 * sin(2 * PI * 100 * it / 2048)).toFloat() }
        audio.listen(frame, frame.size)
        val loudest = audio.spectrum.indices.maxBy { audio.spectrum[it] }
        assertEquals(100, loudest)
        val held = audio.spectrum[100]
        audio.listen(frame, 0)
        assertEquals(held * 0.5f, audio.spectrum[100], 1e-6f)
    }

    /** Silence leaves the sand exactly where it lies. */
    @Test fun silenceLeavesTheSandAlone() {
        val plate = ChladniPlate(count = 2_000)
        val before = plate.grains.copyOf()
        repeat(40) {
            plate.listen(FloatArray(2048), 0)
            plate.settle(0.016f)
        }
        assertTrue(before.contentEquals(plate.grains))
    }

    /** A sounded plate moves its sand, and keeps every grain on the plate. */
    @Test fun aSoundedPlateMovesTheSandAndKeepsItOn() {
        val plate = ChladniPlate(count = 2_000)
        val before = plate.grains.copyOf()
        val frame = FloatArray(2048) { (0.4 * sin(2 * PI * 12 * it / 2048) + 0.2 * sin(2 * PI * 90 * it / 2048)).toFloat() }
        repeat(200) {
            plate.listen(frame, frame.size)
            plate.settle(0.016f)
        }
        assertTrue(plate.drive > 0f)
        assertTrue(plate.grains.indices.any { abs(plate.grains[it] - before[it]) > 1e-4f })
        assertTrue(plate.grains.all { it in -1f..1f })
    }
}
