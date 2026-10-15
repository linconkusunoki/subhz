package com.subhz.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Last.fm rule that decides when a track has been heard enough to write listening
 * history: half its length, or four minutes, whichever lands first — counted as *elapsed
 * playtime*, so neither a skip nor a pause counts toward it.
 *
 * The clock is driven by hand because the credit a tick earns is `min(position moved, time
 * passed)`: a test that ran against the real clock would tick a thousand times inside one
 * millisecond and credit nothing.
 */
class ScrobbleTrackerTest {

    /** A clock the test advances one second at a time, mirroring the service's poll. */
    private class FakeClock(var now: Long = 0L)

    private val clock = FakeClock()
    private val tracker = ScrobbleTracker(nowMs = { clock.now })

    // ==== the threshold itself: four minutes is a ceiling, not a wait ====

    @Test
    fun `a ninety second song counts at forty five seconds`() {
        assertEquals(45_000L, scrobbleThresholdMs(90_000L))
    }

    @Test
    fun `a three minute song counts at a minute and a half`() {
        assertEquals(90_000L, scrobbleThresholdMs(180_000L))
    }

    @Test
    fun `a long track stops waiting at four minutes rather than half its length`() {
        // Half of two hours is an hour of listening, which is not the rule.
        assertEquals(240_000L, scrobbleThresholdMs(7_200_000L))
    }

    @Test
    fun `unknown duration falls back to the four minute rule`() {
        assertEquals(240_000L, scrobbleThresholdMs(0L))
        assertEquals(240_000L, scrobbleThresholdMs(-9_223_372_036_854_775_807L))
    }

    @Test
    fun `a short song is never made to wait four minutes`() {
        // The whole point of the ceiling: driven end to end, a 90s track reports at 45s.
        val ninetySeconds = 90_000L
        assertNull(play(durationMs = ninetySeconds, seconds = 44))
        assertEquals("song-1", play(fromMs = 44_000, durationMs = ninetySeconds, seconds = 2)?.songId)
    }

    // ==== now playing ====

    @Test
    fun `announces now playing as soon as the track starts`() {
        val announced = announcements(seconds = 1).single()

        assertEquals("song-1", announced.songId)
        assertEquals(1_000L, announced.playedAtMillis)
    }

    @Test
    fun `does not re-announce while the same track keeps playing`() {
        play(seconds = 1)
        assertTrue(announcements(fromMs = 1_000, seconds = 120).isEmpty())
    }

    @Test
    fun `the scrobble carries the same timestamp as the now playing announcement`() {
        val announced = announcements(seconds = 1).single().playedAtMillis
        val scrobbled = play(fromMs = 1_000, seconds = 100)

        // Navidrome promotes the now-playing entry to history on the submission, so the two only
        // line up if they report the same moment.
        assertEquals(announced, scrobbled?.playedAtMillis)
    }

    @Test
    fun `announces again when repeat-one wraps back to the top`() {
        play(seconds = 101)
        announcements(fromMs = 101_000, seconds = 1)

        assertEquals("song-1", announcements(fromMs = 0, seconds = 1).single().songId)
    }

    @Test
    fun `a new song is announced rather than the one playing`() {
        announcements(seconds = 1)

        assertEquals("song-2", announcements(fromMs = 0, seconds = 1, songId = "song-2").single().songId)
    }

    // ==== the tracker ====

    @Test
    fun `reports once the track passes half its length`() {
        // A 200s track, so the bar is 100s of real playback.
        assertNull(play(seconds = 99))
        assertEquals("song-1", play(fromMs = 99_000, seconds = 2)?.songId)
    }

    @Test
    fun `never reports the same play twice`() {
        play(seconds = 101)
        assertNull(play(fromMs = 101_000, seconds = 50))
    }

    @Test
    fun `stamps the play with when the track started, not when the threshold was crossed`() {
        announcements(seconds = 1)
        clock.now = 500_000L

        // Four minutes of wall clock go by between the two, and the stamp is still the start.
        val scrobbled = play(fromMs = 1_000, durationMs = 7_200_000, seconds = 240)

        assertEquals(1_000L, scrobbled?.playedAtMillis)
    }

    @Test
    fun `a track skipped to its last seconds never counts`() {
        play(seconds = 2)
        // Dragged from 2s to 190s: the position jumped 188s in one second, so only the second of
        // audio genuinely played after the jump is credited. The bar is still 100s away.
        assertNull(play(fromMs = 190_000, seconds = 10))
    }

    @Test
    fun `paused time does not count toward the threshold`() {
        assertNull(play(seconds = 60, playing = false))
        assertNull(play(fromMs = 60_000, seconds = 5, playing = false))
        // Resuming still needs the full 100s of playback.
        assertNull(play(fromMs = 65_000, seconds = 40))
        assertEquals("song-1", play(fromMs = 105_000, seconds = 70)?.songId)
    }

    @Test
    fun `time that passes without the position advancing is not credited`() {
        play(seconds = 1)
        // Five minutes of wall clock go by, but the player only got a second further: the
        // process was frozen. Crediting the clock instead of the position would have reported
        // this track on the very next tick.
        clock.now += 300_000L
        assertNull(play(fromMs = 1_000, seconds = 60))
    }

    @Test
    fun `a late tick still credits the time that really played`() {
        play(seconds = 1)
        // The audio kept going across a five second stall in the poll loop, so five seconds
        // really were played and have to count — the threshold is 100s.
        clock.now += 5_000L
        assertNull(play(fromMs = 5_000, seconds = 95))
        assertEquals("song-1", play(fromMs = 100_000, seconds = 5)?.songId)
    }

    @Test
    fun `a new song starts a clean play`() {
        assertEquals("song-1", play(seconds = 150, songId = "song-1")?.songId)
        assertNull(play(seconds = 100, songId = "song-2"))
        assertEquals("song-2", play(fromMs = 100_000, seconds = 2, songId = "song-2")?.songId)
    }

    @Test
    fun `repeat-one wrapping to the top counts as a fresh play`() {
        play(seconds = 101)
        assertEquals("song-1", play(fromMs = 0, seconds = 200)?.songId)
    }

    @Test
    fun `seeking back mid-track keeps the credit already earned`() {
        play(seconds = 60)
        // Back to 30s: no new pass began, so the 59s already played still counts.
        assertNull(play(fromMs = 30_000, seconds = 5))
        assertEquals("song-1", play(fromMs = 35_000, seconds = 40)?.songId)
    }

    @Test
    fun `reset forgets the play so a resumed track starts over`() {
        play(seconds = 120)
        tracker.reset()
        // The position never went backwards, so only the explicit reset can clear the play.
        assertNull(play(fromMs = 120_000, seconds = 2))
    }

    // ==== helpers ====
    //
    // Windows chain by passing the previous `fromMs + seconds * 1000`, and every window advances
    // the fake clock by the same second the position advances, which is what ordinary playback
    // looks like to the tracker.

    private fun play(
        fromMs: Long = 0L,
        durationMs: Long = 200_000L,
        seconds: Int,
        songId: String = "song-1",
        playing: Boolean = true,
    ): PlayEvent.Scrobble? = events(fromMs, durationMs, seconds, songId, playing)
        .filterIsInstance<PlayEvent.Scrobble>()
        .firstOrNull()

    private fun announcements(
        fromMs: Long = 0L,
        durationMs: Long = 200_000L,
        seconds: Int,
        songId: String = "song-1",
    ): List<PlayEvent.NowPlaying> = events(fromMs, durationMs, seconds, songId)
        .filterIsInstance<PlayEvent.NowPlaying>()

    private fun events(
        fromMs: Long,
        durationMs: Long,
        seconds: Int,
        songId: String,
        playing: Boolean = true,
    ): List<PlayEvent> {
        val emitted = mutableListOf<PlayEvent>()
        repeat(seconds) { tick ->
            clock.now += 1_000L
            tracker.onTick(
                songId = songId,
                positionMs = fromMs + (tick + 1) * 1_000L,
                durationMs = durationMs,
                isPlaying = playing,
            )?.let(emitted::add)
        }
        return emitted
    }
}