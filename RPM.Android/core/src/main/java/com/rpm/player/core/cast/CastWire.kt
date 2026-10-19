package com.rpm.player.core.cast

import java.io.ByteArrayOutputStream

/**
 * One CASTV2 message: the only kind of message that either side sends. Everything else is JSON in [payload],
 * on a [namespace] that says how to read it. Ported from the phone's `RPMCast/CastMessage.swift`.
 *
 * It is written by hand, with no protobuf library, because `cast_channel.proto` is small: it has seven
 * fields, this app writes six of them, two of those are constants, and the schema is stable.
 */
data class CastMessage(
    val namespace: String,
    val payload: String,
    val source: String = SENDER,
    val destination: String = RECEIVER,
) {
    /** The protobuf body, without the length prefix. */
    fun encoded(): ByteArray {
        val out = ProtoWriter()
        // Both fields are `required` in the proto2 schema, so they must be sent even though both values are
        // zero. A receiver that does not find them closes the connection without giving a reason.
        out.varint(FIELD_PROTOCOL_VERSION, CASTV2_1_0)
        out.string(FIELD_SOURCE, source)
        out.string(FIELD_DESTINATION, destination)
        out.string(FIELD_NAMESPACE, namespace)
        out.varint(FIELD_PAYLOAD_TYPE, PAYLOAD_STRING)
        out.string(FIELD_PAYLOAD_UTF8, payload)
        return out.bytes()
    }

    /** The message as it is written to the socket: its length in four big-endian bytes, then the body. */
    fun framed(): ByteArray {
        val body = encoded()
        val frame = ByteArray(body.size + 4)
        frame[0] = (body.size ushr 24).toByte()
        frame[1] = (body.size ushr 16).toByte()
        frame[2] = (body.size ushr 8).toByte()
        frame[3] = body.size.toByte()
        body.copyInto(frame, 4)
        return frame
    }

    companion object {
        /** The ids that the platform reserves. Any other id is the transport id of a running app. */
        const val SENDER = "sender-0"
        const val RECEIVER = "receiver-0"

        /** The largest length a frame may declare. A receiver's own limit is 64 KiB. A length over one megabyte
         * means the reader is out of sync with the stream. */
        const val LONGEST = 1 shl 20

        private const val FIELD_PROTOCOL_VERSION = 1
        private const val FIELD_SOURCE = 2
        private const val FIELD_DESTINATION = 3
        private const val FIELD_NAMESPACE = 4
        private const val FIELD_PAYLOAD_TYPE = 5
        private const val FIELD_PAYLOAD_UTF8 = 6
        private const val CASTV2_1_0 = 0
        private const val PAYLOAD_STRING = 0

        /** The length in a frame's first four bytes, or -1 if there are fewer than four bytes. */
        fun lengthOf(header: ByteArray): Int {
            if (header.size < 4) return -1
            return ((header[0].toInt() and 0xFF) shl 24) or ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
        }

        /**
         * Reads a message body. Returns null if the bytes are not a message. Unknown fields are skipped and not
         * refused, so a receiver that starts sending a new field does not stop the music. A message with no
         * namespace is not a valid message.
         */
        fun decode(body: ByteArray): CastMessage? {
            val reader = ProtoReader(body)
            var source = ""
            var destination = ""
            var namespace = ""
            var payload = ""
            while (!reader.done) {
                val (field, wire) = reader.tag() ?: return null
                when {
                    field == FIELD_SOURCE && wire == ProtoReader.BYTES -> source = reader.string() ?: return null
                    field == FIELD_DESTINATION && wire == ProtoReader.BYTES -> destination = reader.string() ?: return null
                    field == FIELD_NAMESPACE && wire == ProtoReader.BYTES -> namespace = reader.string() ?: return null
                    field == FIELD_PAYLOAD_UTF8 && wire == ProtoReader.BYTES -> payload = reader.string() ?: return null
                    else -> if (!reader.skip(wire)) return null
                }
            }
            if (namespace.isEmpty()) return null
            return CastMessage(namespace, payload, source, destination)
        }
    }
}

/** Just enough protobuf to write [CastMessage]: varints and length-delimited strings. */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Int) {
        tag(field, ProtoReader.VARINT)
        raw(value.toLong())
    }

    fun string(field: Int, value: String) {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        tag(field, ProtoReader.BYTES)
        raw(utf8.size.toLong())
        out.write(utf8)
    }

    fun bytes(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = raw(((field shl 3) or wire).toLong())

    /** Writes seven bits at a time, lowest first. The top bit is set when more bytes follow. */
    private fun raw(value: Long) {
        var left = value
        do {
            var byte = (left and 0x7F).toInt()
            left = left ushr 7
            if (left != 0L) byte = byte or 0x80
            out.write(byte)
        } while (left != 0L)
    }
}

/** Just enough protobuf to read [CastMessage] and step over any field it does not know. */
internal class ProtoReader(private val data: ByteArray) {
    private var at = 0

    val done: Boolean get() = at >= data.size

    /** The next field number and wire type. Returns null if the bytes run out or the wire type is one that this
     * schema cannot produce. */
    fun tag(): Pair<Int, Int>? {
        val key = varint() ?: return null
        val wire = (key and 0x07).toInt()
        if (wire != VARINT && wire != FIXED64 && wire != BYTES && wire != FIXED32) return null
        return (key ushr 3).toInt() to wire
    }

    fun string(): String? {
        val length = varint() ?: return null
        if (length < 0 || length > data.size - at) return null
        val text = String(data, at, length.toInt(), Charsets.UTF_8)
        at += length.toInt()
        return text
    }

    fun skip(wire: Int): Boolean = when (wire) {
        VARINT -> varint() != null
        FIXED64 -> step(8)
        FIXED32 -> step(4)
        BYTES -> varint()?.let { it <= Int.MAX_VALUE && step(it.toInt()) } ?: false
        else -> false
    }

    private fun step(count: Int): Boolean {
        if (count < 0 || count > data.size - at) return false
        at += count
        return true
    }

    private fun varint(): Long? {
        var value = 0L
        var shift = 0
        while (at < data.size) {
            val byte = data[at++].toInt() and 0xFF
            value = value or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return value
            shift += 7
            // Ten groups of seven bits hold a 64-bit varint. A longer one means the reader is out of sync.
            if (shift > 63) return null
        }
        return null
    }

    companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val BYTES = 2
        const val FIXED32 = 5
    }
}

/**
 * The 44 bytes at the start of a WAV, for a stream with no end. The sizes are declared as the largest value that
 * a signed 32-bit reader can hold, minus the header (about nine hours of stereo at 44.1 kHz). The phone and
 * other endless WAV streams do the same, because a receiver that reads the size as signed refuses a negative
 * size. Always 16-bit, which is what the sink writes. Ported from `RPMCast/WavHeader.swift`.
 */
object WavHeader {
    const val SIZE = 44

    fun of(rate: Int, channels: Int): ByteArray {
        val bits = 16
        val blockAlign = channels * bits / 8
        val byteRate = rate * blockAlign
        val endless = Int.MAX_VALUE.toLong() - SIZE
        val out = java.nio.ByteBuffer.allocate(SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt((endless + 36).toInt())
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)
        out.putShort(1) // linear PCM
        out.putShort(channels.toShort())
        out.putInt(rate)
        out.putInt(byteRate)
        out.putShort(blockAlign.toShort())
        out.putShort(bits.toShort())
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(endless.toInt())
        return out.array()
    }
}
