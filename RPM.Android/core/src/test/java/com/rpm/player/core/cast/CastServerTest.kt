package com.rpm.player.core.cast

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.Socket

/** The server on a real socket on this machine: what it answers for, and what it cuts. */
class CastServerTest {
    private val ring = PcmRing(48_000 * 4)
    private val server = CastServer(ring).apply { start() }

    @After
    fun stop() = server.stop()

    private fun ask(request: String): Pair<Socket, InputStream> {
        val socket = Socket("127.0.0.1", server.port)
        socket.soTimeout = 5_000
        socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
        socket.getOutputStream().flush()
        return socket to socket.getInputStream()
    }

    /** The response head, up to the blank line. */
    private fun head(input: InputStream): String {
        val text = StringBuilder()
        while (!text.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) break
            text.append(b.toChar())
        }
        return text.toString()
    }

    private fun readExactly(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var at = 0
        while (at < count) {
            val n = input.read(out, at, count - at)
            if (n < 0) break
            at += n
        }
        return out.copyOf(at)
    }

    private fun get(path: String) = ask("GET $path HTTP/1.1\r\nHost: tv\r\n\r\n")

    @Test
    fun `the live stream is answered with a WAV header and the ring's bytes, and counts as fetched`() {
        val stream = CastStream(1, "abc", 44_100)
        server.serve(stream)
        ring.empty(1)
        ring.write(ByteArray(8) { it.toByte() }, 0, 8)
        val (socket, input) = get("/abc.wav")
        socket.use {
            val head = head(input)
            assertTrue(head, head.startsWith("HTTP/1.1 200"))
            assertTrue(head.contains("Content-Type: audio/wav"))
            assertTrue(head.contains("Access-Control-Allow-Origin: *"))
            assertArrayEquals(WavHeader.of(44_100, 2), readExactly(input, 44))
            assertArrayEquals(ByteArray(8) { it.toByte() }, readExactly(input, 8))
        }
        assertEquals(1, server.fetchedGeneration)
    }

    @Test
    fun `a request for any other stream is refused`() {
        server.serve(CastStream(2, "new", 44_100))
        val (socket, input) = get("/old.wav")
        socket.use { assertTrue(head(input).startsWith("HTTP/1.1 404")) }
        assertEquals(0, server.fetchedGeneration)
    }

    @Test
    fun `a HEAD is answered and is not a fetch`() {
        server.serve(CastStream(3, "abc", 48_000))
        val (socket, input) = ask("HEAD /abc.wav HTTP/1.1\r\n\r\n")
        socket.use {
            assertTrue(head(input).startsWith("HTTP/1.1 200"))
            assertEquals(-1, input.read())
        }
        assertEquals(0, server.fetchedGeneration)
    }

    @Test
    fun `a new stream closes the connection reading the old one`() {
        server.serve(CastStream(1, "one", 44_100))
        ring.empty(1)
        val (socket, input) = get("/one.wav")
        socket.use {
            head(input)
            readExactly(input, 44)
            // Wait until the server has taken the connection as the stream's reader.
            val until = System.currentTimeMillis() + 2_000
            while (server.fetchedGeneration != 1 && System.currentTimeMillis() < until) Thread.sleep(10)
            server.serve(CastStream(2, "two", 44_100))
            ring.empty(2)
            ring.write(ByteArray(64) { 9 }, 0, 64)
            // The old connection ends without a byte of the new stream.
            assertEquals(-1, input.read())
        }
        assertEquals(64, ring.held)
    }

    @Test
    fun `a cover fetched before it is read waits for it`() {
        server.cover.expect()
        val (socket, input) = get("/art/anything.jpg")
        Thread.sleep(100)
        server.cover.show(byteArrayOf(1, 2, 3))
        socket.use {
            val head = head(input)
            assertTrue(head, head.startsWith("HTTP/1.1 200"))
            assertTrue(head.contains("Content-Type: image/jpeg"))
            assertTrue(head.contains("Content-Length: 3"))
            assertArrayEquals(byteArrayOf(1, 2, 3), readExactly(input, 3))
        }
    }

    @Test
    fun `a record with no cover is a 404`() {
        server.cover.expect()
        server.cover.show(null)
        val (socket, input) = get("/art/x.jpg")
        socket.use { assertTrue(head(input).startsWith("HTTP/1.1 404")) }
    }

    @Test
    fun `a preflight is answered with the cross-origin lines`() {
        val (socket, input) = ask("OPTIONS /art/x.jpg HTTP/1.1\r\n\r\n")
        socket.use {
            val head = head(input)
            assertTrue(head.startsWith("HTTP/1.1 204"))
            assertTrue(head.contains("Access-Control-Allow-Methods: GET, HEAD, OPTIONS"))
        }
    }

    @Test
    fun `addresses put an IPv6 host in brackets`() {
        assertEquals("http://10.0.0.5:${server.port}/abc.wav", server.address("10.0.0.5", CastStream(1, "abc", 44_100)))
        assertEquals("http://[fe80::1]:${server.port}/art/k.jpg", server.coverAddress("fe80::1%wlan0", "k"))
    }
}
