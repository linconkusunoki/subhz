package com.subhz.player.waveform

import com.subhz.data.api.Song
import com.subhz.player.PlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Builds waveforms ahead of time so the Now Playing screen never waits for one.
 *
 * Analysing a track is a full decode, which is far too slow to run the moment the screen opens.
 * Instead this warms the cache as playback goes: the track that just started is analysed first, so
 * opening Now Playing finds it ready, and the tracks after it are analysed next, so skipping also
 * lands on a ready waveform.
 *
 * Each new request replaces the previous one rather than queueing behind it. When the user skips,
 * the track that was being decoded is already in the past and completing it helps nobody, so it is
 * abandoned and the new current track takes its place. Work is serialised onto a single thread so
 * a burst of analysis cannot starve playback or the rest of the app.
 */
class WaveformPrefetcher(
    private val playerManager: PlayerManager,
    private val repository: WaveformRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var job: Job? = null
    private var activeFirstId: String? = null

    /**
     * Warms the waveform cache for [songs] (the current track and those after it in the queue).
     *
     * Runs on the caller's thread and expects to be called from the main thread — the player's
     * listeners both are, which is why no locking is needed here.
     */
    fun prefetch(songs: List<Song>, ahead: Int = PREFETCH_AHEAD) {
        val firstId = songs.firstOrNull()?.id ?: return
        // A track start fires both a transition and a play event; the second must not throw away
        // the decode the first just began.
        if (firstId == activeFirstId && job?.isActive == true) return

        activeFirstId = firstId
        job?.cancel()

        job = scope.launch {
            for (song in songs.take(ahead)) {
                if (repository.isCached(song.id)) continue
                val streamUrl = playerManager.getStreamUrl(song.id) ?: continue
                try {
                    repository.getPeaks(song.id, streamUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A waveform that cannot be built is a missing nicety, not a failure worth
                    // surfacing. The next track may well succeed.
                }
            }
        }
    }

    private companion object {
        /**
         * The current track plus two ahead. One extra track of data per play is the cost of an
         * instant skip; two keeps the common "next, next" case ready without scraping the queue.
         */
        const val PREFETCH_AHEAD = 3
    }
}
