package com.rpm.player.core.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CastWireTest {
    private val connect = CastMessage(CastNamespace.CONNECTION, """{"type":"CONNECT"}""")

    @Test
    fun `a message is written field by field, the two required zeros included`() {
        val bytes = connect.encoded()
        val expected = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x08, 0x00)) // protocol_version = CASTV2_1_0
            write(byteArrayOf(0x12, 8)); write("sender-0".toByteArray())
            write(byteArrayOf(0x1a, 10)); write("receiver-0".toByteArray())
            write(byteArrayOf(0x22, CastNamespace.CONNECTION.length.toByte())); write(CastNamespace.CONNECTION.toByteArray())
            write(byteArrayOf(0x28, 0x00)) // payload_type = STRING
            write(byteArrayOf(0x32, 18)); write("""{"type":"CONNECT"}""".toByteArray())
        }.toByteArray()
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `a frame is the body's length in four big-endian bytes, then the body`() {
        val frame = connect.framed()
        val body = connect.encoded()
        assertEquals(body.size + 4, frame.size)
        assertEquals(body.size, CastMessage.lengthOf(frame.copyOfRange(0, 4)))
        assertArrayEquals(body, frame.copyOfRange(4, frame.size))
        assertEquals(-1, CastMessage.lengthOf(byteArrayOf(0, 0, 1)))
    }

    @Test
    fun `a length over 127 takes two bytes and still reads back`() {
        val long = CastMessage(CastNamespace.CONTROL, "x".repeat(300), destination = "web-5")
        val read = CastMessage.decode(long.encoded())
        assertEquals(long, read)
        assertEquals(0x0001012C, CastMessage.lengthOf(byteArrayOf(0, 1, 1, 0x2C)))
    }

    @Test
    fun `a message reads back as it was written`() {
        val sent = CastMessage(CastNamespace.MEDIA, """{"type":"GET_STATUS","requestId":7}""", source = "sender-0", destination = "abc-123")
        assertEquals(sent, CastMessage.decode(sent.encoded()))
    }

    @Test
    fun `fields this does not know are stepped over, not refused`() {
        val body = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x08, 0x00))
            write(byteArrayOf(0x12, 10)); write("receiver-0".toByteArray())
            write(byteArrayOf(0x1a, 8)); write("sender-0".toByteArray())
            write(byteArrayOf(0x22, CastNamespace.HEARTBEAT.length.toByte())); write(CastNamespace.HEARTBEAT.toByteArray())
            write(byteArrayOf(0x28, 0x00))
            write(byteArrayOf(0x32, 15)); write("""{"type":"PING"}""".toByteArray())
            write(byteArrayOf(0x3a, 3, 1, 2, 3)) // payload_binary, which nobody here uses
            write(byteArrayOf(0x45, 1, 2, 3, 4)) // field 8, fixed32
            write(byteArrayOf(0x49, 1, 2, 3, 4, 5, 6, 7, 8)) // field 9, fixed64
            write(byteArrayOf(0x50, 0x96.toByte(), 0x01)) // field 10, a two-byte varint
        }.toByteArray()
        val read = CastMessage.decode(body)
        assertNotNull(read)
        assertEquals(CastNamespace.HEARTBEAT, read!!.namespace)
        assertEquals("""{"type":"PING"}""", read.payload)
        assertEquals("receiver-0", read.source)
    }

    @Test
    fun `bytes that are not a message read as none`() {
        assertNull(CastMessage.decode(byteArrayOf()))
        // A string that says it is longer than what is left.
        assertNull(CastMessage.decode(byteArrayOf(0x22, 40, 'u'.code.toByte())))
        // A wire type the schema cannot produce (3, a group).
        assertNull(CastMessage.decode(byteArrayOf(0x0B)))
        // No namespace.
        assertNull(CastMessage.decode(byteArrayOf(0x08, 0x00, 0x32, 2, '{'.code.toByte(), '}'.code.toByte())))
    }

    @Test
    fun `the WAV header declares the stream's format and the longest length a signed reader holds`() {
        val header = WavHeader.of(rate = 48_000, channels = 2)
        assertEquals(44, header.size)
        val at = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(header, 0, 4))
        assertEquals(Int.MAX_VALUE - 44 + 36, at.getInt(4))
        assertEquals("WAVE", String(header, 8, 4))
        assertEquals("fmt ", String(header, 12, 4))
        assertEquals(16, at.getInt(16))
        assertEquals(1, at.getShort(20).toInt())
        assertEquals(2, at.getShort(22).toInt())
        assertEquals(48_000, at.getInt(24))
        assertEquals(48_000 * 4, at.getInt(28))
        assertEquals(4, at.getShort(32).toInt())
        assertEquals(16, at.getShort(34).toInt())
        assertEquals("data", String(header, 36, 4))
        assertEquals(Int.MAX_VALUE - 44, at.getInt(40))
    }
}
