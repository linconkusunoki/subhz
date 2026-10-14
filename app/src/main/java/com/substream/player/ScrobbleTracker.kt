package com.substream.player

// =====================================================================
// 1. THE RULE
// =====================================================================

/**
 * The four-minute cap in the Last.fm scrobble rule, which Subsonic and Navidrome both inherit.
 * This is a ceiling, not a wait: a 90 second song counts at 45 seconds, a three minute song at a
 * minute and a half, and only something longer than eight minutes is held all the way to four.
 */
private const val MAX_PLAYED_MS = 4L * 60L * 1000L

/**
 * How close to the start of the track a backwards jump has to land to read as a new play-through
 * rather than a seek. One poll is a second, so a repeat-one wrap is always caught inside three.
 */
private const val NEAR_START_MS = 3_000L

/**
 * The playtime at which a track counts as heard: half its length, or four minutes, whichever
 * comes first.
 *
 * A length of zero means the server never reported one (live streams, some spoken-word files),
 * and half of an unknown length is not a number, so those fall back to the four-minute rule alone.
 */
internal fun scrobbleThresholdMs(durationMs: Long): Long =
    if (durationMs <= 0L) MAX_PLAYED_MS else minOf(durationMs / 2, MAX_PLAYED_MS)

/** Something worth telling the server about the track that is playing. */
internal sealed interface PlayEvent {

    /** The server's song id. */
    val songId: String

    /**
     * Epoch millis at which this play-through started. Both events carry the same value, and it
     * must be the start rather than the moment the event fired: the server files the play under
     * it, so a scrobble that reported "now" would land in history a third of a song late.
     */
    val playedAtMillis: Long

    /** The listener just started this track. Transient — superseded by the next track. */
    data class NowPlaying(
        override val songId: String,
        override val playedAtMillis: Long,
    ) : PlayEvent

    /** The track has been heard enough to write listening history. */
    data class Scrobble(
        override val songId: String,
        override val playedAtMillis: Long,
    ) : PlayEvent
}

// =====================================================================
// 2. THE TRACKER
// =====================================================================

/**
 * Turns the position of a playing track into the two announcements the server expects: a
 * `nowPlaying` the moment it starts, and a `scrobble` once it has been genuinely heard.
 *
 * What counts is *elapsed playtime*, not how far along the progress bar is. A track skipped ten
 * seconds in never reaches its threshold, and one paused at the halfway mark for an hour never
 * reaches it either. Per tick the credit is
 *
 *     min(how far the position moved, how much wall-clock time passed)
 *
 * and only while the player is actually playing. The two halves of that minimum are what make it
 * honest. Dragging across the scrubber moves the position far faster than time passes, so only
 * the second term — the second of audio genuinely played after the jump — is credited. And a
 * process frozen in Doze or stalled behind a busy UI thread passes plenty of wall-clock time
 * without the position advancing, so only the first term counts. Taking the smaller of the two
 * handles both, and needs no guess about how far a "real" seek is.
 *
 * Stateless with respect to Android — the caller supplies position, duration and the clock — so
 * the whole rule is exercised by plain JVM tests.
 */
internal class ScrobbleTracker(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private var trackId: String? = null
    private var trackStartedAtMillis = 0L
    private var playedMs = 0L
    private var lastPositionMs = 0L
    private var lastTickAtMillis = 0L
    private var reported = false

    /**
     * Feeds one observation of the player and returns the event to report, or null on every tick
     * that has nothing to say. A pass announces itself at most twice — once as now playing, once
     * as a scrobble — so a caller can fire on each non-null result without deduplicating.
     *
     * @param songId Media id of the current item, which is the server's song id.
     * @param positionMs Current playback position; negative values (unset) are treated as zero.
     * @param durationMs Track length; zero or unset means unknown.
     * @param isPlaying Whether the player was actually playing during this tick.
     */
    fun onTick(
        songId: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
    ): PlayEvent? {
        val position = positionMs.coerceAtLeast(0L)
        val now = nowMs()

        // A different song: everything counted so far belongs to the previous one.
        if (songId != trackId) {
            return startPass(songId, position, now)
        }

        // The position moved backwards, so this is either a seek back or repeat-one wrapping the
        // track around to the top. Landing within the first few seconds means a new pass either
        // way — dragging back to the start is also a replay from the beginning — so the playtime
        // and the timestamp reset. A seek back into the middle just re-anchors the cursor and
        // keeps the credit already earned, which is honest, since that music really was played.
        if (position < lastPositionMs) {
            if (position <= NEAR_START_MS) return startPass(songId, position, now)
            lastPositionMs = position
            lastTickAtMillis = now
            return null
        }

        if (isPlaying) {
            val heard = minOf(position - lastPositionMs, now - lastTickAtMillis)
            if (heard > 0L) playedMs += heard
        }
        lastPositionMs = position
        lastTickAtMillis = now

        if (reported) return null
        // Recomputed every tick on purpose: a track whose length arrives late (it streams in
        // after the first few seconds) tightens the threshold the moment the real value shows.
        if (playedMs < scrobbleThresholdMs(durationMs)) return null

        reported = true
        return PlayEvent.Scrobble(songId = songId, playedAtMillis = trackStartedAtMillis)
    }

    /**
     * Forgets the current track so the next tick starts a fresh pass. Used when playback stops,
     * so a track that is paused and resumed for ten minutes is not joined back into one play.
     */
    fun reset() {
        trackId = null
        playedMs = 0L
        lastPositionMs = 0L
        lastTickAtMillis = 0L
        reported = false
    }

    /** Begins a play-through and announces it, which is what the server needs before a scrobble. */
    private fun startPass(songId: String, positionMs: Long, nowMillis: Long): PlayEvent {
        trackId = songId
        trackStartedAtMillis = nowMillis
        playedMs = 0L
        lastPositionMs = positionMs
        lastTickAtMillis = nowMillis
        reported = false
        return PlayEvent.NowPlaying(songId = songId, playedAtMillis = trackStartedAtMillis)
    }
}