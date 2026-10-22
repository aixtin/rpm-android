package com.rpm.player.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistMergeTest {
    @Test
    fun `one side unchanged takes the other as it is`() {
        val base = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), PlaylistMerge.songs(base, mine = listOf("c", "a", "b"), theirs = base))
        assertEquals(listOf("a", "b", "c", "d"), PlaylistMerge.songs(base, mine = base, theirs = listOf("a", "b", "c", "d")))
    }

    @Test
    fun `a reorder here and an addition there keep both`() {
        val merged = PlaylistMerge.songs(listOf("a", "b", "c"), mine = listOf("c", "b", "a"), theirs = listOf("a", "b", "c", "d"))
        // The store's order wins; the song it added is kept.
        assertEquals(listOf("a", "b", "c", "d"), merged)
    }

    @Test
    fun `a removal there and an addition here keep both`() {
        val merged = PlaylistMerge.songs(listOf("a", "b", "c"), mine = listOf("a", "b", "c", "e"), theirs = listOf("a", "c"))
        assertEquals(listOf("a", "c", "e"), merged)
    }

    @Test
    fun `the same song added on both sides is added once`() {
        val merged = PlaylistMerge.songs(listOf("a"), mine = listOf("a", "x", "y"), theirs = listOf("a", "x"))
        assertEquals(listOf("a", "x", "y"), merged)
    }

    @Test
    fun `an unknown base removes nothing`() {
        val merged = PlaylistMerge.songs(null, mine = listOf("a", "b"), theirs = listOf("b", "c"))
        assertEquals(listOf("b", "c", "a"), merged)
    }

    @Test
    fun `a second copy put there on purpose is kept`() {
        val merged = PlaylistMerge.songs(listOf("a", "b"), mine = listOf("b", "a"), theirs = listOf("a", "b", "a"))
        assertEquals(listOf("a", "b", "a"), merged)
    }

    @Test
    fun `selections step along a run and the last reaches back`() {
        val run = (0 until 130).map { "t$it" }
        assertEquals(3, Lists.slices(run.size, 50))
        assertEquals(run.subList(0, 50), Lists.window(run, 50, 0))
        assertEquals(run.subList(50, 100), Lists.window(run, 50, 1))
        assertEquals(run.subList(80, 130), Lists.window(run, 50, 2))
        assertEquals(run.subList(0, 50), Lists.window(run, 50, 3))
        assertEquals(run.subList(80, 130), Lists.window(run, 50, -1))
        assertEquals(1, Lists.slices(40, 50))
        assertEquals(1, Lists.slices(40, 0))
    }

    @Test
    fun `a generated list has a look and a made one has none`() {
        val calm = GeneratedListLook.of("Calm", null, 0)!!
        assertEquals(false, calm.roomed)
        assertEquals(true, GeneratedListLook.of("RightNow", null, 0)!!.roomed)
        assertNull(GeneratedListLook.of("NoSuchKind", null, 0))
        // The lettering is readable on the border at large-text strength.
        assert(GeneratedListLook.contrast(calm.text, calm.border) >= 2.5)
    }

    @Test
    fun `a long name is set smaller to fit the band`() {
        // A monospaced stand-in: each letter is half the size wide, the gauge is the size tall.
        val measure: (String, Float) -> ListFrameMetrics.Ink = { said, size -> ListFrameMetrics.Ink(0f, said.length * size * 0.5f, -size * 0.8f, size * 0.2f) }
        val short = ListFrameMetrics.lettering("Mix", 100f, measure)!!
        assertEquals(8f, short.size, 0.001f)
        val long = ListFrameMetrics.lettering("A very long list name indeed", 100f, measure)!!
        assert(long.size < short.size)
        assertNull(ListFrameMetrics.lettering("", 100f, measure))
    }
}
