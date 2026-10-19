package com.rpm.player.core.cast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastProtocolTest {
    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `a LOAD names the stream twice, as WAV, live, with the record's words`() {
        val load = parse(CastPayload.stamp(CastPayload.load("http://10.0.0.5:4000/abc.wav", "Rosanna", "Toto", "IV", "http://10.0.0.5:4000/art/k.jpg", autoplay = false), 3))
        assertEquals("LOAD", load["type"]!!.jsonPrimitive.content)
        assertEquals(3, load["requestId"]!!.jsonPrimitive.int)
        assertFalse(load["autoplay"]!!.jsonPrimitive.boolean)
        val media = load["media"]!!.jsonObject
        assertEquals("http://10.0.0.5:4000/abc.wav", media["contentId"]!!.jsonPrimitive.content)
        assertEquals("http://10.0.0.5:4000/abc.wav", media["contentUrl"]!!.jsonPrimitive.content)
        assertEquals("audio/wav", media["contentType"]!!.jsonPrimitive.content)
        assertEquals("LIVE", media["streamType"]!!.jsonPrimitive.content)
        val metadata = media["metadata"]!!.jsonObject
        assertEquals(3, metadata["metadataType"]!!.jsonPrimitive.int)
        assertEquals("Rosanna", metadata["title"]!!.jsonPrimitive.content)
        assertEquals("Toto", metadata["artist"]!!.jsonPrimitive.content)
        assertEquals("IV", metadata["albumName"]!!.jsonPrimitive.content)
        assertEquals("http://10.0.0.5:4000/art/k.jpg", (metadata["images"] as JsonArray)[0].jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a LOAD without an artist, album or cover leaves them out`() {
        val metadata = parse(CastPayload.load("u", "Station", null, null, null, autoplay = true).toString())["media"]!!.jsonObject["metadata"]!!.jsonObject
        assertFalse("artist" in metadata)
        assertFalse("albumName" in metadata)
        assertFalse("images" in metadata)
    }

    @Test
    fun `an answer is found by its request number and nothing else`() {
        val stamped = CastPayload.stamp(CastPayload.getStatus, 41)
        assertEquals(41, CastPayload.requestIdOf(stamped))
        assertEquals("GET_STATUS", CastPayload.typeOf(stamped))
        // A status nobody asked for carries 0, or nothing.
        assertNull(CastPayload.requestIdOf("""{"type":"RECEIVER_STATUS","requestId":0}"""))
        assertNull(CastPayload.requestIdOf("""{"type":"MEDIA_STATUS"}"""))
        assertNull(CastPayload.requestIdOf("not json"))
    }

    @Test
    fun `the volume is sent between nought and one`() {
        val level = parse(CastPayload.setVolume(1.4).toString())["volume"]!!.jsonObject["level"]!!.jsonPrimitive.content.toDouble()
        assertEquals(1.0, level, 0.0)
    }

    @Test
    fun `a receiver status is read leniently, applications, transport and volume`() {
        val status = ReceiverStatus.of(
            """{"requestId":2,"status":{"applications":[{"appId":"41BD77B1","displayName":"RPM","isIdleScreen":false,
            "namespaces":[{"name":"urn:x-cast:com.google.cast.media"},{"name":"urn:x-cast:com.rpmrpmrpm.control"}],
            "sessionId":"7007b4ab","statusText":"RPM","transportId":"7007b4ab-transport","universalAppId":"41BD77B1"}],
            "userEq":{},"volume":{"controlType":"master","level":0.35,"muted":false,"stepInterval":0.05}},"type":"RECEIVER_STATUS"}""",
        )!!
        val app = status.app(CastApps.RPM)!!
        assertEquals("7007b4ab", app.sessionId)
        assertEquals("7007b4ab-transport", app.transportId)
        assertTrue(CastNamespace.CONTROL in app.namespaces)
        assertEquals(0.35, status.volume!!.level!!, 1e-9)
        assertTrue(status.volume.canBeSet)
        assertNull(status.app(CastApps.DEFAULT_MEDIA))
    }

    @Test
    fun `an idle receiver has no applications, and a fixed volume cannot be set`() {
        val idle = ReceiverStatus.of("""{"type":"RECEIVER_STATUS","status":{"volume":{"controlType":"fixed","level":1.0}}}""")!!
        assertTrue(idle.applications.isEmpty())
        assertFalse(idle.volume!!.canBeSet)
        assertNull(ReceiverStatus.of("""{"type":"MEDIA_STATUS","status":[]}"""))
    }

    @Test
    fun `where the room is has three answers`() {
        val holding = RoomReport.of("""{"type":"MEDIA_STATUS","requestId":9,"status":[{"mediaSessionId":4,"playerState":"PLAYING","currentTime":12.5}]}""")
        assertEquals(RoomReport.Holding(MediaStatus(4, "PLAYING", 12.5, null)), holding)
        assertEquals(RoomReport.NothingLoaded, RoomReport.of("""{"type":"MEDIA_STATUS","requestId":9,"status":[]}"""))
        assertEquals(RoomReport.NoAnswer, RoomReport.of(null))
        assertEquals(RoomReport.NoAnswer, RoomReport.of("""{"type":"INVALID_REQUEST","reason":"INVALID_MEDIA_SESSION_ID"}"""))
        val idle = RoomReport.of("""{"type":"MEDIA_STATUS","status":[{"mediaSessionId":4,"playerState":"IDLE","idleReason":"CANCELLED"}]}""")
        assertEquals("CANCELLED", (idle as RoomReport.Holding).status.idleReason)
    }

    @Test
    fun `an overtaken LOAD is not a refused one`() {
        assertEquals(LoadAnswer.Superseded, LoadAnswer.of("""{"type":"LOAD_CANCELLED","requestId":5}"""))
        assertEquals(LoadAnswer.Refused("LOAD_FAILED"), LoadAnswer.of("""{"type":"LOAD_FAILED","requestId":5}"""))
        assertEquals(LoadAnswer.Taken(12), LoadAnswer.of("""{"type":"MEDIA_STATUS","requestId":5,"status":[{"mediaSessionId":12,"playerState":"IDLE"}]}"""))
    }

    @Test
    fun `the meta word carries the record and its clock anchor`() {
        val meta = parse(CastNowPlaying("Africa", "Toto", "IV", "http://h/art/1.jpg", 295.0, trackAt = 0.0, streamAt = 301.5).payload())
        assertEquals(ControlWords.META, meta["type"]!!.jsonPrimitive.content)
        assertEquals("Africa", meta["title"]!!.jsonPrimitive.content)
        assertEquals("Toto", meta["artist"]!!.jsonPrimitive.content)
        assertEquals("IV", meta["album"]!!.jsonPrimitive.content)
        assertEquals("http://h/art/1.jpg", meta["art"]!!.jsonPrimitive.content)
        assertEquals(295.0, meta["duration"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(301.5, meta["streamAt"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(0.0, meta["trackAt"]!!.jsonPrimitive.content.toDouble(), 0.0)
        val bare = parse(CastNowPlaying("Radio", null, null, null, null, 0.0, 0.0).payload())
        assertFalse("art" in bare || "artist" in bare || "duration" in bare)
    }

    @Test
    fun `a cover goes as bytes only when it fits a frame, and none is an answer too`() {
        val small = parse(ControlWords.art(ByteArray(1_000) { 1 }))
        assertEquals(ControlWords.ART, small["type"]!!.jsonPrimitive.content)
        assertTrue(small["data"]!!.jsonPrimitive.content.startsWith("data:image/jpeg;base64,"))
        assertEquals(JsonNull, parse(ControlWords.art(ByteArray(ControlWords.ART_LIMIT + 1)))["data"])
        assertEquals(JsonNull, parse(ControlWords.art(null))["data"])
        // The whole message still fits the 64 KiB frame at the limit.
        assertTrue(CastMessage(CastNamespace.CONTROL, ControlWords.art(ByteArray(ControlWords.ART_LIMIT) { 7 }), destination = "t").framed().size < 65_536)
    }

    @Test
    fun `the receiver's words are read by type`() {
        assertEquals(RoomWord.Next, RoomWord.of("""{"type":"next"}"""))
        assertEquals(RoomWord.Previous, RoomWord.of("""{"type":"prev"}"""))
        assertEquals(RoomWord.ArtFailed, RoomWord.of("""{"type":"artfail"}"""))
        assertEquals("hello", (RoomWord.of("""{"type":"hello","v":1}""") as RoomWord.Said).type)
        assertNull(RoomWord.of("[1,2]"))
    }
}
