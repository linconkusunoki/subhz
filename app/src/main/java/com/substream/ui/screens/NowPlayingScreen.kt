package com.substream.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import com.substream.ui.components.LyricsPanel
import com.substream.ui.components.RatingBar
import com.substream.ui.components.RatingViewModel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.substream.data.api.Song
import com.substream.player.PlayerManager
import com.substream.player.PlayerState
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Locale

/**
 * Full screen "Now Playing" media player composable.
 * Displays high-res album cover, song metadata, interactive progress bar (Slider),
 * and playback controls (Previous, Play/Pause, Next, Shuffle, Repeat).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBackClick: () -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    playerManager: PlayerManager = koinInject(),
    ratingViewModel: RatingViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val playerState by playerManager.playerState.collectAsState()
    val currentSong = playerState.currentSong

    // Lyrics stand in for the artwork rather than sitting under it, so only one of the two is ever
    // on screen and the controls below stay where the thumb is.
    var showingLyrics by rememberSaveable { mutableStateOf(false) }

    // Automatically dismiss full screen if no song is currently playing or loaded
    LaunchedEffect(currentSong) {
        if (currentSong == null) {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    if (currentSong != null) {
                        IconButton(onClick = { showingLyrics = !showingLyrics }) {
                            Icon(
                                imageVector = if (showingLyrics) {
                                    Icons.Default.Album
                                } else {
                                    Icons.Default.Lyrics
                                },
                                contentDescription = if (showingLyrics) "Show artwork" else "Show lyrics",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (currentSong != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Adaptive High-Resolution Album Cover Art, or the lyrics in its place.
                // Sized off the width alone, never off the leftover height: the sheet re-measures
                // its content while it settles, and an artwork that depends on the remaining space
                // visibly resizes mid-animation. Width is fixed for the whole animation.
                if (showingLyrics) {
                    LyricsPanel(
                        song = currentSong,
                        positionMs = playerState.currentPositionMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val coverArtUrl = remember(currentSong.coverArt) {
                        playerManager.getCoverArtUrl(currentSong.coverArt)
                    }

                    if (!coverArtUrl.isNullOrEmpty()) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            AsyncImage(
                                model = coverArtUrl,
                                contentDescription = currentSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(80.dp)
                                )
                            }
                        }
                    }
                }

                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Song Title & Artist Info
                SongInfoSection(song = currentSong, onArtistClick = onArtistClick)

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Interactive Slider & Time Display
                PlaybackProgressSection(
                    playerState = playerState,
                    onSeek = { positionMs -> playerManager.seekTo(positionMs) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 4. Rating for the track being played
                RatingBar(
                    rating = currentSong.userRating,
                    onRate = { rating -> ratingViewModel.rate(currentSong, rating) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 5. Centered Media Controls
                PlaybackControlsSection(
                    playerState = playerState,
                    onTogglePlayPause = { playerManager.togglePlayPause() },
                    onSkipPrevious = { playerManager.skipToPrevious() },
                    onSkipNext = { playerManager.skipToNext() },
                    onToggleShuffle = { playerManager.toggleShuffle() },
                    onToggleRepeat = { playerManager.toggleRepeatMode() }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// =====================================================================
// SUB-COMPOSABLES
// =====================================================================

@Composable
private fun SongInfoSection(
    song: Song,
    onArtistClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        val artistName = song.artist ?: "Unknown Artist"
        val artistId = song.artistId
        Text(
            text = artistName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (artistId != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = if (artistId != null) {
                Modifier.clickable { onArtistClick(artistId, artistName) }
            } else {
                Modifier
            }
        )

        if (!song.album.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlaybackProgressSection(
    playerState: PlayerState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isUserDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val currentPosition = playerState.currentPositionMs
    val duration = playerState.durationMs

    val displayPosition = if (isUserDragging) sliderPosition else currentPosition.toFloat()
    val maxRange = duration.coerceAtLeast(1L).toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayPosition.coerceIn(0f, maxRange),
            onValueChange = { newValue ->
                isUserDragging = true
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                onSeek(sliderPosition.toLong())
                isUserDragging = false
            },
            valueRange = 0f..maxRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMs(displayPosition.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = formatMs(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaybackControlsSection(
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (playerState.shuffleModeEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // Previous Track Button
        IconButton(
            onClick = onSkipPrevious,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Song",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Play/Pause Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(64.dp),
            onClick = onTogglePlayPause
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Next Track Button
        IconButton(
            onClick = onSkipNext,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Song",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Repeat Mode Button
        IconButton(onClick = onToggleRepeat) {
            val (icon, tint) = when (playerState.repeatMode) {
                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne to MaterialTheme.colorScheme.primary
                Player.REPEAT_MODE_ALL -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary
                else -> Icons.Default.Repeat to MaterialTheme.colorScheme.onSurfaceVariant
            }

            Icon(
                imageVector = icon,
                contentDescription = "Repeat Mode",
                tint = tint
            )
        }
    }
}

// =====================================================================
// HELPER FUNCTIONS
// =====================================================================

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
