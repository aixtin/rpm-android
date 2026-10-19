package com.rpm.player.core.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The saved stations' merge rule, which the API, the desktop and the iOS app also follow. */
class FavoriteLedgerTest {
    private fun station(id: String) = RadioStation(uuid = id, name = "Radio $id", streamUrl = "https://s/$id")

    private fun row(id: String, saved: Boolean, at: Long, stamp: Long = 1) = FavoriteRow(id, saved, at, station(id), stamp)

    @Test fun aFirstLedgerKeepsTheListInItsOrderAndSendsAllOfIt() {
        val ledger = FavoriteLedger.of(listOf(station("b"), station("a"), station("c")))
        assertEquals(listOf("b", "a", "c"), ledger.saved.map { it.uuid })
        assertEquals(setOf("a", "b", "c"), ledger.unsent)
    }

    @Test fun aSaveGoesToTheEndAndAnUnsaveTakesItOut() {
        val ledger = FavoriteLedger.of(listOf(station("a"), station("b")))
            .changed(station("c"), saved = true, now = 1000)
            .changed(station("a"), saved = false, now = 1001)
        assertEquals(listOf("b", "c"), ledger.saved.map { it.uuid })
        assertFalse(ledger.holds("a"))
        assertTrue(ledger.holds("C"))
    }

    @Test fun aChangeIsNewerThanTheOneItReplacesWhateverTheClockSays() {
        val ledger = FavoriteLedger().changed(station("a"), saved = true, now = 5000).changed(station("a"), saved = false, now = 10)
        assertEquals(5001, ledger.changes.getValue("a").changedAt)
    }

    @Test fun aNewerRowFromTheCloudReplacesWhatIsHeldAndIsNoLongerOursToSend() {
        val ledger = FavoriteLedger.of(listOf(station("a"))).taking(listOf(row("a", saved = false, at = 50, stamp = 7)))
        assertFalse(ledger.holds("a"))
        assertTrue(ledger.unsent.isEmpty())
        assertEquals(7, ledger.cursor)
    }

    @Test fun anOlderRowFromTheCloudDoesNotUndoAChangeMadeHere() {
        val ledger = FavoriteLedger().changed(station("a"), saved = false, now = 900)
            .taking(listOf(row("a", saved = true, at = 100)))
        assertFalse(ledger.holds("a"))
        assertEquals(setOf("a"), ledger.unsent)
    }

    @Test fun anotherDevicesStationIsAddedInTheOrderItWasSaved() {
        val ledger = FavoriteLedger().changed(station("mine"), saved = true, now = 500)
            .taking(listOf(row("theirs", saved = true, at = 300), row("later", saved = true, at = 800)))
        assertEquals(listOf("theirs", "mine", "later"), ledger.saved.map { it.uuid })
    }

    @Test fun aChangeMadeWhileOneWasOutStaysUnsent() {
        val before = FavoriteLedger().changed(station("a"), saved = true, now = 100)
        val out = before.toSend(500)
        val after = before.changed(station("a"), saved = false, now = 200).sent(out)
        assertEquals(setOf("a"), after.unsent)
        assertTrue(before.sent(out).unsent.isEmpty())
    }

    @Test fun anotherAccountStartsAnEmptyLedgerAndTheSameOneKeepsIt() {
        val mine = FavoriteLedger.of(listOf(station("a"))).forAccount("me")
        assertEquals(listOf("a"), mine.forAccount("me").saved.map { it.uuid })
        val theirs = mine.forAccount("someone else")
        assertTrue(theirs.saved.isEmpty())
        assertTrue(theirs.unsent.isEmpty())
        assertEquals(0, theirs.cursor)
    }

    @Test fun theCursorNeverGoesBack() {
        val ledger = FavoriteLedger(cursor = 10).taking(listOf(row("a", saved = true, at = 1, stamp = 4)))
        assertEquals(10, ledger.cursor)
    }
}
