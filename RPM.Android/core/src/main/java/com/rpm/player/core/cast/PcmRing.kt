package com.rpm.player.core.cast

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A buffer where the stream's bytes wait between the sink, which writes on ExoPlayer's playback thread, and the
 * server, which reads on the connection's thread. Ported from the phone's `RPMCast/PcmRing.swift`, with one
 * change: a write never overwrites the oldest data. The sink asks for [free] first and writes only what fits,
 * so the decoder waits and the stream does not lose audio ([CastFeed.take]).
 *
 * The writer never holds the lock for long and never allocates memory under it. The reader may wait for bytes
 * ([read] with a timeout), but the writer never waits.
 */
class PcmRing(val capacity: Int) {
    private val lock = ReentrantLock()
    private val arrived = lock.newCondition()
    private val store = ByteArray(capacity)
    private var readAt = 0
    private var count = 0
    /** Which stream the bytes belong to: the generation that [empty] was last given. A reader for another
     * stream gets -1 and takes nothing, so a connection for a replaced stream cannot take the first bytes of
     * the next stream. */
    private var epoch = 0

    val held: Int get() = lock.withLock { count }

    val free: Int get() = lock.withLock { capacity - count }

    /** Copies as much as fits of [length] bytes from [from] at [offset]. Returns how many bytes were written. */
    fun write(from: ByteArray, offset: Int, length: Int): Int = lock.withLock {
        val taking = minOf(length, capacity - count)
        if (taking <= 0) return 0
        val writeAt = (readAt + count) % capacity
        val first = minOf(taking, capacity - writeAt)
        System.arraycopy(from, offset, store, writeAt, first)
        if (taking > first) System.arraycopy(from, offset + first, store, 0, taking - first)
        count += taking
        arrived.signalAll()
        taking
    }

    /** Takes up to `into.size` bytes of stream [of], and waits at most [waitMs] for any to arrive. Returns how
     * many bytes were read, or -1 if the ring now holds another stream. Zero is a normal result while the decoder
     * is between buffers. */
    fun read(into: ByteArray, waitMs: Long, of: Int): Int = lock.withLock {
        if (epoch != of) return -1
        if (count == 0 && waitMs > 0) arrived.await(waitMs, TimeUnit.MILLISECONDS)
        if (epoch != of) return -1
        val taking = minOf(into.size, count)
        if (taking <= 0) return 0
        val first = minOf(taking, capacity - readAt)
        System.arraycopy(store, readAt, into, 0, first)
        if (taking > first) System.arraycopy(store, 0, into, first, taking - first)
        readAt = (readAt + taking) % capacity
        count -= taking
        taking
    }

    /** Empties the ring for stream [of]. Use 0 for no stream. */
    fun empty(of: Int) = lock.withLock {
        readAt = 0
        count = 0
        epoch = of
        arrived.signalAll()
    }
}
