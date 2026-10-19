package com.rpm.player.core.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The iOS app's ArtSceneTests, SaliencyCropTests, CoveredFaceTests and the window half of ArtFrameTests. */
class ArtCutTest {
    private val stage = ArtScene.forClearLeft(852.0, 393.0, clearTop = 0.0, clearRight = 0.75, clearBottom = 0.62)

    // ── the scene ──

    @Test fun theRecordIsWhereTheClearBandEnds() = assertEquals(0.75, stage.faceHardRight, 0.0)

    @Test fun nothingCoversTheLeftSoTheFloorIsOnlyAMargin() {
        assertTrue(stage.faceHardLeft > 0)
        assertTrue(stage.faceHardLeft < 0.1)
    }

    @Test fun theSubjectGoesLeftOfTheRecord() {
        assertTrue(stage.focusAcross > stage.faceHardLeft)
        assertTrue(stage.focusAcross < stage.faceHardRight)
        assertTrue(stage.focusAcross < 0.5)
    }

    @Test fun theSubjectGoesAboveTheMiddle() {
        assertTrue(stage.focusDown < 0.5)
        assertTrue(stage.focusDown > 0)
    }

    @Test fun aClearFrameStillHasMargins() {
        val clear = ArtScene.forClearLeft(393.0, 852.0, clearTop = 0.0, clearRight = 1.0, clearBottom = 1.0)
        assertEquals(1.0, clear.faceHardRight, 0.0)
        assertTrue(clear.faceHardLeft > 0)
        assertEquals(0.45, clear.focusDown, 1e-9)
    }

    @Test fun anImpossibleFrameIsBroughtBackIntoRange() {
        val squashed = ArtScene.forClearLeft(100.0, 100.0, clearTop = 0.0, clearRight = -3.0, clearBottom = 0.0)
        val stretched = ArtScene.forClearLeft(100.0, 100.0, clearTop = 0.0, clearRight = 5.0, clearBottom = 5.0)
        assertEquals(0.2, squashed.clearRight, 0.0)
        assertEquals(0.15, squashed.focusDown, 1e-9)
        assertEquals(1.0, stretched.clearRight, 0.0)
        assertEquals(0.45, stretched.focusDown, 1e-9)
        assertTrue(squashed.faceHardRight > squashed.faceHardLeft)
    }

    @Test fun aFrameOfNoSizeSaysSo() {
        assertFalse(ArtScene.forClearLeft(0.0, 0.0, 0.0, 0.75, 0.6).isLaidOut)
        assertTrue(stage.isLaidOut)
    }

    @Test fun theShapeIsTheFramesOwn() = assertEquals(852.0 / 393.0, stage.aspect, 0.0001)

    // ── where to cut ──

    private fun map(width: Int, height: Int, subject: CropRect? = null): DoubleArray {
        val map = DoubleArray(width * height) { 0.05 }
        if (subject != null) for (y in subject.y until subject.y + subject.height) for (x in subject.x until subject.x + subject.width) map[y * width + x] = 1.0
        return map
    }

    private fun across(x: Double, rect: CropRect) = (x - rect.x) / rect.width

    @Test fun theFaceGoesWhereTheRecordAndTheWordsAreNot() {
        val face = FaceBox(330, 100, 40, 48)
        val choice = SaliencyCrop.choose(map(600, 300, CropRect(300, 80, 110, 160)), 600, 300, stage.aspect, listOf(face), stage)
        val middle = face.x + face.width / 2.0
        assertTrue(abs(across(middle, choice.rect) - stage.focusAcross) < 0.01)
        assertTrue(choice.fillsTarget)
    }

    @Test fun aFaceOutOnTheRightIsBroughtInFromBehindTheRecord() {
        val face = FaceBox(490, 100, 40, 48)
        val middle = face.x + face.width / 2.0
        val choice = SaliencyCrop.choose(map(600, 300, CropRect(460, 80, 110, 160)), 600, 300, stage.aspect, listOf(face), stage)
        assertTrue(middle / 600 > stage.faceHardRight)
        assertTrue(across(middle, choice.rect) < stage.faceHardRight)
        assertTrue(across(middle, choice.rect) < middle / 600)
    }

    @Test fun theCrownIsNotSpentOnTheLift() {
        val face = FaceBox(300, 100, 40, 48)
        val choice = SaliencyCrop.choose(map(600, 300, CropRect(270, 80, 110, 160)), 600, 300, stage.aspect, listOf(face), stage)
        assertTrue(choice.rect.y <= face.y - face.height * 0.45 + 1)
        assertTrue(choice.rect.y + choice.rect.height >= face.y + face.height)
    }

    @Test fun everybodyInThePhotographIsInTheWindow() {
        val band = listOf(FaceBox(200, 110, 36, 44), FaceBox(300, 100, 36, 44), FaceBox(400, 118, 36, 44))
        val choice = SaliencyCrop.choose(map(700, 400, CropRect(180, 90, 280, 200)), 700, 400, stage.aspect, band, stage)
        for (face in band) {
            assertTrue(face.x >= choice.rect.x)
            assertTrue(face.x + face.width <= choice.rect.x + choice.rect.width)
            assertTrue(face.y >= choice.rect.y)
            assertTrue(face.y + face.height <= choice.rect.y + choice.rect.height)
        }
    }

    @Test fun aPhotographOfNothingInParticularIsNotZoomedInto() {
        val choice = SaliencyCrop.choose(map(600, 300), 600, 300, stage.aspect, emptyList(), stage)
        assertEquals(600, choice.rect.width)
        assertTrue(choice.fillsTarget)
    }

    @Test fun aSpeckIsNotASubject() =
        assertEquals(600, SaliencyCrop.choose(map(600, 300, CropRect(100, 100, 4, 4)), 600, 300, stage.aspect, emptyList(), stage).rect.width)

    @Test fun aFigureIsFramedOnItsHeadAndNotItsChest() {
        val figure = CropRect(170, 30, 60, 340)
        val choice = SaliencyCrop.choose(map(400, 400, figure), 400, 400, stage.aspect, emptyList(), stage)
        val windowMiddle = choice.rect.y + choice.rect.height / 2.0
        val figureMiddle = figure.y + figure.height / 2.0
        assertTrue(windowMiddle < figureMiddle)
        assertTrue(choice.rect.y < figure.y + 40)
    }

    @Test fun aSubjectThatFitsIsZoomedTo() {
        val choice = SaliencyCrop.choose(map(600, 300, CropRect(240, 90, 120, 120)), 600, 300, stage.aspect, emptyList(), stage)
        assertTrue(choice.rect.width < 600)
        assertTrue(choice.fillsTarget)
    }

    @Test fun aMapThatDoesNotFitThePictureCropsNothing() =
        assertEquals(CropRect(0, 0, 600, 300), SaliencyCrop.choose(doubleArrayOf(0.5, 0.5), 600, 300, stage.aspect, emptyList(), stage).rect)

    @Test fun aFaceHighInAFrameCountedFromTheBottomIsHighInThePicture() {
        assertEquals(FaceBox(10, 10, 20, 20), FaceBox.fromNormalizedBottomLeft(0.1, 0.7, 0.2, 0.2, 100, 100))
        assertEquals(70, FaceBox.fromNormalizedBottomLeft(0.1, 0.1, 0.2, 0.2, 100, 100).y)
    }

    /** With no attention map, the faces alone place the crop, through a map with their heads lit. */
    @Test fun withNoAttentionMapTheFacesPlaceTheCrop() {
        val face = FaceBox(330, 100, 40, 48)
        val choice = SaliencyCrop.choose(SaliencyCrop.facesOnly(listOf(face), 600, 300), 600, 300, stage.aspect, listOf(face), stage)
        assertTrue(abs(across(face.x + face.width / 2.0, choice.rect) - stage.focusAcross) < 0.01)
        assertTrue(choice.rect.width < 600)
    }

    /** Why the map is not flat: a flat map has no subject, and with no subject the faces are not looked at. */
    @Test fun aFlatMapHasNoSubjectSoItsFacesAreNotLookedAt() {
        val face = FaceBox(330, 100, 40, 48)
        val choice = SaliencyCrop.choose(DoubleArray(600 * 300) { 1.0 }, 600, 300, stage.aspect, listOf(face), stage)
        assertEquals(600, choice.rect.width)
    }

    // ── the window filled from the top ──

    @Test fun aSquareInAWideFrameKeepsItsTop() = assertEquals(CropRect(0, 0, 1000, 714), ArtFrame.topWindow(1000, 1000, 1.4))

    @Test fun aSquareInATallFrameLosesItsSidesEvenly() = assertEquals(CropRect(75, 0, 850, 1000), ArtFrame.topWindow(1000, 1000, 0.85))

    @Test fun aFrameTheShapeOfThePictureTakesAllOfIt() = assertEquals(CropRect(0, 0, 1000, 1000), ArtFrame.topWindow(1000, 1000, 1.0))

    // ── a face the frame covers ──

    private val card = ArtScene.forClearLeft(390.0, 470.0, clearTop = 59.0 / 470, clearRight = 0.94, clearBottom = 0.62)
    private val edge = 512

    private fun face(down: Int, size: Int = 60, across: Int = 226) = FaceBox(across, down, size, size)

    @Test fun aFaceAtTheVeryTopIsCovered() = assertTrue(ArtFrame.coversFace(listOf(face(4)), edge, edge, card))

    @Test fun aFaceBelowTheStatusBarIsClear() = assertFalse(ArtFrame.coversFace(listOf(face(120)), edge, edge, card))

    @Test fun aForeheadUnderTheBarIsNotACoveredFace() = assertFalse(ArtFrame.coversFace(listOf(face(50)), edge, edge, card))

    @Test fun noFaceIsNotCovered() = assertFalse(ArtFrame.coversFace(emptyList(), edge, edge, card))

    @Test fun theLargestFaceDecides() =
        assertFalse(ArtFrame.coversFace(listOf(face(2, size = 20, across = 20), face(200, size = 120)), edge, edge, card))

    @Test fun aFaceOutsideTheWindowIsCovered() = assertTrue(ArtFrame.coversFace(listOf(face(200, size = 20, across = 2)), edge, edge, card))

    @Test fun withNothingOverTheTopAHighFaceIsClear() {
        val under = ArtScene.forClearLeft(390.0, 470.0, clearTop = 0.0, clearRight = 0.94, clearBottom = 0.62)
        assertFalse(ArtFrame.coversFace(listOf(face(4)), edge, edge, under))
    }
}
