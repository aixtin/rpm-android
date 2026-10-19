package com.rpm.player.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iOS app's PlaylistFeaturesTests, for the part that :core holds. The words around the names belong to the screen. */
class PlaylistFeaturesTest {

    @Test fun theArtistTheListIsBuiltAroundWinsHoweverTheyWereTyped() {
        val featured = PlaylistFeatures.of(listOf("Kanye West", "Kanye West & Ty Dolla \$ign", "Jay-Z & Kanye West", "Rihanna"), most = 3)
        assertEquals("Kanye West", featured.named.first())
    }

    @Test fun everyoneNamedLeavesNobodyOver() {
        val featured = PlaylistFeatures.of(listOf("Blur", "Pulp"), most = 3)
        assertEquals(listOf("Blur", "Pulp"), featured.named)
        assertEquals(0, featured.more)
    }

    @Test fun theRestAreCountedRatherThanNamed() {
        val featured = PlaylistFeatures.of(listOf("A Band", "B Band", "C Band", "D Band", "E Band"), most = 2)
        assertEquals(2, featured.named.size)
        assertEquals(3, featured.more)
    }

    @Test fun oneArtistTypedTwoWaysIsOneArtist() {
        val featured = PlaylistFeatures.of(listOf("Blur", "BLUR", "blur"), most = 3)
        assertEquals(listOf("Blur"), featured.named)
        assertEquals(0, featured.more)
    }

    @Test fun aSongThatNamesNobodyIsNotAnAct() {
        val featured = PlaylistFeatures.of(listOf("Blur", null, "", "   "), most = 3)
        assertEquals(listOf("Blur"), featured.named)
        assertEquals(0, featured.more)
    }

    @Test fun tiesFallBackToTheNameSoTheLineIsTheSameLineEveryTime() =
        assertEquals(listOf("Aardvark", "Mongoose", "Zebra"), PlaylistFeatures.of(listOf("Zebra", "Aardvark", "Mongoose"), most = 3).named)

    @Test fun aListThatNamesNobodyFeaturesNobody() {
        val featured = PlaylistFeatures.of(listOf(null, ""), most = 3)
        assertTrue(featured.named.isEmpty())
        assertEquals(0, featured.more)
    }

    @Test fun namingNoneOfThemStillCountsThemAll() {
        val featured = PlaylistFeatures.of(listOf("Blur", "Pulp"), most = 0)
        assertTrue(featured.named.isEmpty())
        assertEquals(2, featured.more)
    }

    @Test fun theLineNamesThreeAndCountsTheRest() {
        val featured = PlaylistFeatures.of(listOf("Blur", "Blur", "Pulp", "Suede", "Oasis", "Elastica"), PlaylistFeatures.NAMED)
        assertEquals(listOf("Blur", "Elastica", "Oasis"), featured.named)
        assertEquals(2, featured.more)
    }
}
