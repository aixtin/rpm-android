package com.rpm.player.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastDevicesTest {
    private fun device(id: String?, name: String, host: String, port: Int, model: String = "Google Nest Audio"): CastDevice =
        CastDevice.of(listOfNotNull(id?.let { "id" to it }, "fn" to name, "md" to model).toMap(), host, port)!!

    @Test
    fun `a group and its host speaker share an address and both are listed`() {
        // Seen on a real network: Keuken 2 at .165:8009, the pair at .165:32000, a group at .165:32223.
        val list = CastDevices().apply {
            found("Google-Nest-Audio-a", device("a", "Keuken 2", "192.168.1.165", 8009))
            found("Google-Cast-Group-b", device("b", "Keuken-paar", "192.168.1.165", 32000, model = "Google Cast Group"))
            found("Google-Cast-Group-c", device("c", "Schield en Keuken", "192.168.1.165", 32223, model = "Google Cast Group"))
        }.list()
        assertEquals(listOf("Keuken 2", "Keuken-paar", "Schield en Keuken"), list.map { it.name })
        assertEquals(listOf(false, true, true), list.map { it.isGroup })
    }

    @Test
    fun `a device resolved again at a new address replaces its entry`() {
        val devices = CastDevices()
        devices.found("svc", device("a", "Kitchen", "10.0.0.2", 8009))
        devices.found("svc", device("a", "Kitchen", "10.0.0.9", 8009))
        assertEquals(listOf("10.0.0.9"), devices.list().map { it.host })
    }

    @Test
    fun `a device that advertises no id is known by its address and port`() {
        val d = device(null, "Old", "10.0.0.3", 8009)
        assertEquals("10.0.0.3:8009", d.id)
        assertEquals("10.0.0.3", CastDevice.of(mapOf("fn" to "  "), "10.0.0.3", 8009)!!.name)
        assertNull(CastDevice.of(mapOf("fn" to "x"), null, 8009))
        assertNull(CastDevice.of(mapOf("fn" to "x"), "10.0.0.3", 0))
    }

    @Test
    fun `a lost service takes its device out, and an unknown one changes nothing`() {
        val devices = CastDevices()
        devices.found("one", device("a", "Kitchen", "10.0.0.2", 8009))
        devices.found("two", device("b", "Lounge", "10.0.0.4", 8009))
        devices.lost("nobody")
        assertEquals(2, devices.list().size)
        devices.lost("one")
        assertEquals(listOf("Lounge"), devices.list().map { it.name })
    }

    @Test
    fun `a device named by two services stays until both are lost`() {
        val devices = CastDevices()
        devices.found("one", device("a", "Kitchen", "10.0.0.2", 8009))
        devices.found("one-again", device("a", "Kitchen", "10.0.0.2", 8009))
        devices.lost("one")
        assertEquals(1, devices.list().size)
        devices.lost("one-again")
        assertTrue(devices.list().isEmpty())
    }

    @Test
    fun `a service that comes back as another device lets go of the first`() {
        val devices = CastDevices()
        devices.found("svc", device("a", "Kitchen", "10.0.0.2", 8009))
        devices.found("svc", device("b", "Kitchen (new)", "10.0.0.2", 8009))
        assertEquals(listOf("b"), devices.list().map { it.id })
    }

    @Test
    fun `the list is by name, whatever the case`() {
        val list = CastDevices().apply {
            found("1", device("1", "lounge", "10.0.0.1", 1))
            found("2", device("2", "Bedroom", "10.0.0.2", 1))
            found("3", device("3", "attic", "10.0.0.3", 1))
        }.list()
        assertEquals(listOf("attic", "Bedroom", "lounge"), list.map { it.name })
    }

    @Test
    fun `the menu keeps the device in use when discovery has lost it`() {
        val inUse = device("a", "Kitchen", "10.0.0.2", 8009)
        val other = device("b", "Bedroom", "10.0.0.4", 8009)
        assertEquals(listOf("Bedroom", "Kitchen"), CastDevices.menu(listOf(other), inUse).map { it.name })
        assertEquals(listOf("Bedroom", "Kitchen"), CastDevices.menu(listOf(other, inUse), inUse).map { it.name })
        assertEquals(listOf("Bedroom"), CastDevices.menu(listOf(other), null).map { it.name })
        assertFalse(CastDevices.menu(emptyList(), null).isNotEmpty())
    }
}
