package com.rpm.player.core.cast

/**
 * What the room holds of our stream, and what its reports mean given that. Ported from the phone's
 * `RPMAudio/RoomStream.swift`.
 *
 * A receiver sends the same report for different events, and the meaning depends on what this side did a
 * moment earlier. *Nothing loaded* can mean a stop at the speaker, or the half second of a track change when our
 * stream has been taken away and the next one has not started yet. *PAUSED* can mean a person pressed pause on
 * the speaker, or it can be the result of our own pause. If a report is read without knowing which case it is,
 * every skip looks like a stop. So this class holds the state, and the reading is a function of that state.
 * Both are tested (`RoomStreamTest`). Two facts decide everything:
 *
 * - [session]: the media session of the stream that the room holds **of ours**. A LOAD sets it, and it is
 *   cleared the moment the stream is taken away. Null means the room is empty because we emptied it, so nothing
 *   it reports is news.
 * - [turns]: how many times that has changed. The poll reads it before it asks and passes it back with the
 *   answer, so a reply that crossed a track change is dropped. This is true whenever the question is asked, and
 *   it does not depend on a time window in which reports are ignored.
 *
 * Main thread only. The controller owns it.
 */
class RoomStream {
    sealed interface Stage {
        /** No cast. */
        data object None : Stage

        /** A device is being connected, or a stream was taken off the receiver and the next one has not started yet. */
        data object Connecting : Stage

        /** The receiver accepted the LOAD, but it may not have fetched anything yet. The fetch check waits for this. */
        data class Holding(val session: Int) : Stage

        /** The receiver fetched the stream. From here on, the stall check reads its position. */
        data class Playing(val session: Int) : Stage

        /** Casting has ended. */
        data object Gone : Stage
    }

    var stage: Stage = Stage.None
        private set

    /** The media session of the stream that the room holds of ours, or null while it holds nothing of ours.
     * Every transport message carries it, and a message with the wrong session is refused. It is always read
     * from [stage] and never stored separately. */
    val session: Int?
        get() = when (val s = stage) {
            is Stage.Holding -> s.session
            is Stage.Playing -> s.session
            else -> null
        }

    /** How many times the room's held stream has changed. */
    var turns = 0
        private set

    /** How many transport keys this side has sent. */
    var told = 0
        private set

    /** Keys that were sent and have not been answered yet. */
    var keysOut = 0
        private set

    /** The value the poll asks under: [told], or null while a key is on its way. A question sent while a key
     * is still in flight can reach the socket first, and its answer can describe the room before the key had
     * any effect. */
    val stamp: Int? get() = if (keysOut == 0) told else null

    /** A transport key is being sent. */
    fun toldIt() {
        told += 1
        keysOut += 1
    }

    /** The room answered a key, or sending the key failed. */
    fun heardBack() {
        keysOut = maxOf(0, keysOut - 1)
    }

    /** A device is being connected. This is the first step: from here, everything the room reports is read. */
    fun pickingUp() {
        stage = Stage.Connecting
        turns += 1
    }

    /** Our stream is off the receiver. Until the next stream starts, nothing the receiver reports is news. */
    fun takenBack() {
        if (stage != Stage.None && stage != Stage.Gone) stage = Stage.Connecting
        turns += 1
    }

    /** The receiver accepted a new stream, under [session]. */
    fun nowHolding(session: Int) {
        stage = Stage.Holding(session)
        turns += 1
    }

    /** The receiver fetched the stream that it holds. */
    fun itFetched() {
        val s = stage
        if (s is Stage.Holding) stage = Stage.Playing(s.session)
    }

    fun gone() {
        stage = Stage.Gone
        turns += 1
    }

    /** Why the music is being stopped. */
    enum class Because {
        /** The receiver answered and holds no media: a stop on a receiver that keeps its app running. */
        HOLDING_NOTHING,

        /** IDLE, and the reason is a stop and not a track that finished. */
        LET_THE_MUSIC_GO,

        /** The receiver application is gone: a stop on a Google speaker, which quits the app. */
        CLOSED_THE_RECEIVER,
    }

    /** What the player should do about a report. */
    sealed interface Word {
        data object Nothing : Word
        /** Someone paused at the speaker, in the Home app, or with a voice command such as "Hey Google, pause". */
        data object Pause : Word
        data object Resume : Word
        /** Someone stopped the sound: the casting session ends and the player pauses. */
        data class Stopped(val because: Because) : Word
    }

    /**
     * What the media transport's answer to a status request means.
     *
     * @param askedUnder [turns] when the question was asked. A reply about a replaced stream is dropped.
     * @param toldUnder [stamp] when the question was asked. A reply from before a key, or asked while a key was
     *   on its way, is not a person acting at the speaker.
     * @param paused whether the player is paused now. A report that agrees with our state is never news.
     */
    fun reading(report: RoomReport, askedUnder: Int, toldUnder: Int?, paused: Boolean): Word {
        if (askedUnder != turns) return Word.Nothing
        if (toldUnder == null || toldUnder != told) return Word.Nothing
        val ours = session ?: return Word.Nothing
        return when (report) {
            RoomReport.NoAnswer -> Word.Nothing
            RoomReport.NothingLoaded -> Word.Stopped(Because.HOLDING_NOTHING)
            is RoomReport.Holding -> {
                val said = report.status
                // A status about a replaced stream carries that stream's own session, so this check is exact.
                if (said.mediaSessionId != ours) return Word.Nothing
                when {
                    said.playerState == "PAUSED" && !paused -> Word.Pause
                    said.playerState == "PLAYING" && paused -> Word.Resume
                    // Stopped, not finished: our stream never finishes, so an IDLE caused by a stop means someone
                    // stopped the sound.
                    said.playerState == "IDLE" && (said.idleReason == "CANCELLED" || said.idleReason == "INTERRUPTED") ->
                        Word.Stopped(Because.LET_THE_MUSIC_GO)
                    else -> Word.Nothing
                }
            }
        }
    }

    /**
     * What the device's own status means. A Google speaker that is stopped at the speaker quits the receiver app,
     * and the media transport goes with it. The same guard applies as for the transport's report: a receiver
     * whose stream has just been taken away also quits, and if it is asked during that time it correctly
     * reports that our app is gone.
     *
     * @param ours the application session of our receiver app.
     */
    fun reading(status: ReceiverStatus, ours: String, askedUnder: Int): Word {
        if (askedUnder != turns) return Word.Nothing
        if (session == null) return Word.Nothing
        if (status.applications.any { it.sessionId == ours }) return Word.Nothing
        return Word.Stopped(Because.CLOSED_THE_RECEIVER)
    }
}
