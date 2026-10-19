package com.rpm.player.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The TV's end of the control channel, held to the receiver page (`cast/receiver.html` in the rpm repo),
 * as the desktop's `CastReceiverProtocolTests` holds its own. A word renamed on one side compiles and
 * ships but never arrives: the music plays and the television is wrong, or "next" pressed at the speaker
 * does nothing.
 */
class ReceiverPageTest {
    /**
     * The page, in the rpm repo checked out next to this repo. A checkout that holds every app in one tree
     * has it in its own `cast` folder.
     */
    private val page: String by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, "rpm/cast/receiver.html"), File(dir, "cast/receiver.html"))) {
                if (candidate.isFile) return@lazy candidate.readText()
            }
            dir = dir.parentFile
        }
        throw AssertionError("cast/receiver.html was not found. It is in the rpm repo: check it out next to this repo.")
    }

    @Test
    fun `the namespace is the same on both ends`() {
        assertTrue(page.contains("'${CastNamespace.CONTROL}'"))
    }

    @Test
    fun `the protocol version is the same on both ends`() {
        val declared = Regex("""const PROTOCOL\s*=\s*(\d+)""").find(page)?.groupValues?.get(1)
        assertEquals(ControlWords.PROTOCOL.toString(), declared)
    }

    @Test
    fun `everything the TV sends is something the receiver listens for`() {
        for (type in ControlWords.SENT) {
            assertTrue("the page has no branch for '$type'", page.contains("message.type === '$type'"))
        }
    }

    @Test
    fun `everything the TV reads is something the receiver says`() {
        for (type in ControlWords.HEARD) {
            assertTrue("the page never says '$type'", page.contains("type: '$type'"))
        }
    }

    @Test
    fun `the receiver turns the speaker's next and previous into words on the channel`() {
        assertTrue(page.contains("MessageType.QUEUE_UPDATE"))
        assertTrue(page.contains("tell({ type: '${ControlWords.NEXT}' })"))
        assertTrue(page.contains("tell({ type: '${ControlWords.PREV}' })"))
    }

    @Test
    fun `the app id the TV launches is RPM's receiver`() {
        assertEquals("41BD77B1", CastApps.RPM)
        assertEquals("CC1AD845", CastApps.DEFAULT_MEDIA)
    }
}
