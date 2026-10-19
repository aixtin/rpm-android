package com.rpm.player.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ArtistEditTest {

    private fun act(name: String) = ArtistWallItem(name, listOf(name + "1"), emptyList(), emptyList(), 10)

    private val me = act("Me")
    private val a = act("A")
    private val b = act("B")
    private val c = act("C")
    private val walls = Walls(listOf(me, a, b, c), emptyList())

    @Test
    fun noListsLeavesTheComputedListAsItIs() {
        val computed = listOf(a, b)
        assertSame(computed, ArtistEdit.NONE.relate(computed, me, walls))
    }

    @Test
    fun alwaysComesFirstInTheAdminsOrderAndOnlyWhenTheCollectionHoldsTheAct() {
        val edit = ArtistEdit(related = listOf("c", "Not held", "b"))
        assertEquals(listOf("C", "B", "A"), edit.relate(listOf(a, b), me, walls).map { it.name })
    }

    @Test
    fun neverIsLeftOutEvenWhenAlsoListedUnderAlways() {
        val edit = ArtistEdit(related = listOf("C"), unrelated = listOf("a", "C"))
        assertEquals(listOf("B"), edit.relate(listOf(a, b), me, walls).map { it.name })
    }

    @Test
    fun theActItselfIsNeverRelated() {
        val edit = ArtistEdit(related = listOf("me"))
        assertEquals(listOf("A"), edit.relate(listOf(a), me, walls).map { it.name })
    }
}
