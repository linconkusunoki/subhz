package com.subhz.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.subhz.data.api.Song
import com.subhz.data.preferences.PlaybackPreferences
import com.subhz.data.preferences.ServerPreferences
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// =====================================================================
// 1. PLAYER STATE
// =====================================================================

/**
 * State representing the current playback status of the media player.
 */
data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

// =====================================================================
// 2. PLAYER MANAGER
// =====================================================================

/**
 * Manager class connecting to PlaybackService via MediaController.
 * Registered as a Singleton in Koin.
 */
class PlayerManager(
    private val context: Context,
    private val serverPreferences: ServerPreferences,
    private val playbackPreferences: PlaybackPreferences,
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    private var currentPlaylist: List<Song> = emptyList()

    // Declared before init so the cache collector below never sees a null map.
    private val coverArtCache = mutableMapOf<String, String>()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
                controller?.let { p ->
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    )
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            val duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            val currentPos = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            _playerState.value = _playerState.value.copy(
                isBuffering = isBuffering,
                durationMs = duration,
                currentPositionMs = currentPos
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            val currentPos = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            val mediaId = mediaItem?.mediaId
            val song = currentPlaylist.find { it.id == mediaId } ?: _playerState.value.currentSong
            _playerState.value = _playerState.value.copy(
                currentSong = song,
                durationMs = duration,
                currentPositionMs = currentPos
            )
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.value = _playerState.value.copy(shuffleModeEnabled = shuffleModeEnabled)
        }
    }

    init {
        // Cached URLs carry the old server's host and token, so drop them on switch.
        // Logging out also stops playback: the session would keep streaming the old
        // server's audio with credentials the user just revoked.
        scope.launch {
            serverPreferences.config.collect { server ->
                coverArtCache.clear()
                if (server == null) stop()
            }
        }
        connect()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val mediaController = controllerFuture?.get()
                controller = mediaController
                mediaController?.addListener(playerListener)

                mediaController?.let { p ->
                    _playerState.value = _playerState.value.copy(
                        isPlaying = p.isPlaying,
                        isBuffering = p.playbackState == Player.STATE_BUFFERING,
                        durationMs = p.duration.coerceAtLeast(0L),
                        currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                        shuffleModeEnabled = p.shuffleModeEnabled,
                        repeatMode = p.repeatMode
                    )
                    if (p.isPlaying) {
                        startPositionUpdates()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                controller?.let { p ->
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                        durationMs = p.duration.coerceAtLeast(0L)
                    )
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * Constructs the authenticated streaming URL for a given song ID.
     * Returns null when nobody is signed in.
     *
     * Carries the user's quality ceiling when they set one: the server transcodes on the fly, so
     * asking for a lower bitrate costs the phone's data plan and nothing else. A ceiling of 0 asks
     * for the original file. Transcoded streams carry no content length up front, so it is asked for
     * explicitly, otherwise seeking has nothing to seek within.
     */
    fun getStreamUrl(songId: String): String? {
        val config = serverPreferences.activeServer ?: return null
        val maxBitRate = playbackPreferences.maxBitRate.value
        return if (maxBitRate <= 0) {
            config.authenticatedUrl("stream.view", "id" to songId)
        } else {
            config.authenticatedUrl(
                "stream.view",
                "id" to songId,
                "maxBitRate" to maxBitRate.toString(),
                "estimateContentLength" to "true",
            )
        }
    }

    /**
     * Constructs the authenticated cover art URL for a given coverArt ID.
     * Caches generated URLs by coverArtId to ensure stable URLs and avoid
     * Coil image reloading/flickering on player state updates.
     */
    fun getCoverArtUrl(coverArtId: String?): String? {
        if (coverArtId.isNullOrEmpty()) return null
        val config = serverPreferences.activeServer ?: return null
        return coverArtCache.getOrPut(coverArtId) {
            config.authenticatedUrl("getCoverArt.view", "id" to coverArtId)
        }
    }

    /**
     * The tracks currently loaded in the player, in queue order.
     *
     * The queue the player holds is a list of media items carrying ids; the songs behind them live
     * here, and the service needs them to store the queue on the server.
     */
    fun currentQueue(): List<Song> = currentPlaylist

    /**
     * Plays a list of songs, starting at the specified index.
     *
     * [startPositionMs] and [playWhenReady] exist for restoring a queue saved on the server: it
     * comes back paused, at the point the user left it, rather than resuming playback on its own.
     */
    fun playSongs(
        songs: List<Song>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        if (songs.isEmpty()) return

        val mediaItems = songs.mapNotNull { song ->
            val streamUrl = getStreamUrl(song.id) ?: return@mapNotNull null
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist ?: "Unknown Artist")
                .setAlbumTitle(song.album ?: "")
                .setArtworkUri(getCoverArtUrl(song.coverArt)?.let { Uri.parse(it) })
                .build()

            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(song.id)
                .setMediaMetadata(mediaMetadata)
                .build()
        }
        if (mediaItems.isEmpty()) return

        currentPlaylist = songs

        val initialSong = songs.getOrNull(startIndex) ?: songs[0]
        _playerState.value = _playerState.value.copy(
            currentSong = initialSong,
            isBuffering = true,
            currentPositionMs = startPositionMs
        )

        controller?.let { player ->
            player.setMediaItems(mediaItems, startIndex, startPositionMs)
            player.prepare()
            if (playWhenReady) player.play() else player.pause()
        }
    }

    /**
     * Plays a single song by preparing and starting the MediaController with the Navidrome stream URL.
     */
    fun playSong(song: Song) {
        playSongs(listOf(song), 0)
    }

    fun play() {
        controller?.let { player ->
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        controller?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
    }

    fun skipToNext() {
        controller?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                player.seekToNext()
            }
        }
    }

    fun skipToPrevious() {
        controller?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                player.seekToPrevious()
            }
        }
    }

    fun toggleShuffle() {
        controller?.let { player ->
            val nextShuffle = !player.shuffleModeEnabled
            player.shuffleModeEnabled = nextShuffle
            _playerState.value = _playerState.value.copy(shuffleModeEnabled = nextShuffle)
        }
    }

    fun toggleRepeatMode() {
        controller?.let { player ->
            val nextMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            player.repeatMode = nextMode
            _playerState.value = _playerState.value.copy(repeatMode = nextMode)
        }
    }

    fun stop() {
        stopPositionUpdates()
        controller?.stop()
        _playerState.value = PlayerState()
    }

    /**
     * Binds to [PlaybackService]. Idempotent — safe to call on every Activity start.
     *
     * The binding must be dropped again in [disconnect] whenever the app goes away: a bound
     * controller keeps the service alive, and `stopSelf()` is a documented no-op while any
     * client is still bound, so swipe-away-from-recents could never clear the notification.
     */
    fun connect() {
        if (controller != null || controllerFuture != null) return
        initializeController()
    }

    /**
     * Unbinds from [PlaybackService]. Playback in the service is unaffected — the session owns
     * the player, not this controller — but the service becomes stoppable again.
     */
    fun disconnect() {
        stopPositionUpdates()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}
