@file:OptIn(UnstableApi::class)

package com.substream.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.substream.MainActivity
import com.substream.data.repository.SubsonicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** How often the scrobble rule is evaluated. Fine-grained enough for an accurate playtime. */
private const val SCROBBLE_POLL_MS = 1_000L

/**
 * Foreground MediaSessionService enabling background audio playback
 * and integration with Android system notifications and lockscreen controls.
 */
class PlaybackService : MediaSessionService(), KoinComponent {

    private var mediaSession: MediaSession? = null

    /**
     * The real player. Held as a field because the scrobble watch needs to read the position that
     * this service owns — [PlayerManager]'s controller is unbound every time the UI goes away.
     */
    private var exoPlayer: ExoPlayer? = null

    // Injeção do CacheDataSource.Factory via Koin
    private val cacheDataSourceFactory: CacheDataSource.Factory by inject()

    /**
     * Reports plays to the server. Resolved lazily because Koin is not touched until the first
     * song actually needs submitting.
     */
    private val repository: SubsonicRepository by inject()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val scrobbleTracker = ScrobbleTracker()
    private var scrobbleJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // Configures the DefaultMediaSourceFactory with the injected CacheDataSource.Factory
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        // Inicializa o ExoPlayer utilizando o MediaSourceFactory com suporte a Cache
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.addListener(scrobbleListener)
        exoPlayer = player

        mediaSession = MediaSession.Builder(this, player)
            // Without this the media notification has no target and tapping it is a no-op.
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private val scrobbleListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startScrobbleWatch()
            } else {
                stopScrobbleWatch()
            }
        }
    }

    /**
     * Polls the player while it plays and hands each observation to [ScrobbleTracker], which
     * decides when the track has been heard enough to count.
     *
     * Lives here rather than in [PlayerManager] on purpose: the controller is unbound on every
     * `Activity.onStop`, so a tracker up there would go quiet for exactly the case that matters
     * most — a song reaching its threshold while the app sits in the background.
     */
    private fun startScrobbleWatch() {
        scrobbleJob?.cancel()
        scrobbleJob = serviceScope.launch {
            while (isActive) {
                val player = exoPlayer
                val songId = player?.currentMediaItem?.mediaId
                if (songId != null) {
                    val event = scrobbleTracker.onTick(
                        songId = songId,
                        // C.TIME_UNSET reads as a huge negative; the tracker clamps both this
                        // and the duration to zero, which it treats as "unknown".
                        positionMs = player.currentPosition,
                        durationMs = player.duration,
                        isPlaying = player.isPlaying
                    )
                    // A sibling job, not a child of this loop: an announcement in flight must
                    // survive the song ending. Failure is swallowed by the repository's Result —
                    // losing one history entry is not worth a stutter or an error on screen.
                    if (event != null) {
                        serviceScope.launch { submit(event) }
                    }
                }
                delay(SCROBBLE_POLL_MS)
            }
        }
    }

    /**
     * Sends one announcement. Both share the tracker's start-of-play timestamp on purpose: the
     * server keys the now-playing entry and the history entry off the same value, so they only
     * line up if it is reported identically to both.
     *
     * The Result is deliberately not inspected, and that is not an oversight. The server cannot
     * report this outcome: Navidrome's scrobble handler always returns success, so it answers
     * `status="ok"` even to a play it silently discarded. Nothing here can distinguish "counted"
     * from "dropped" — only the server's own log can. Retrying would be worse than useless, since
     * it would double-count the plays that did land.
     */
    private suspend fun submit(event: PlayEvent) {
        when (event) {
            is PlayEvent.NowPlaying -> repository.nowPlaying(event.songId, event.playedAtMillis)
            is PlayEvent.Scrobble -> repository.scrobble(event.songId, event.playedAtMillis)
        }
    }

    private fun stopScrobbleWatch() {
        scrobbleJob?.cancel()
        scrobbleJob = null
    }

    /**
     * Dismissing the app from recents means "I'm done": pause and tear the service down so
     * the media notification goes with it. Leaving the app by pressing home does not land
     * here, so background playback keeps playing with a live notification.
     */
    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    /**
     * START_STICKY (the MediaSessionService default) makes Android restart the service once
     * it stops, which re-posts the media notification with actions bound to a player the
     * service no longer owns — the notification sits in the tray and its buttons do nothing.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        super.onStartCommand(intent, flags, startId).let { START_NOT_STICKY }

    override fun onDestroy() {
        stopScrobbleWatch()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
