package com.subhz.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.subhz.player.PlayerState
import com.subhz.ui.navigation.PullToExpand

/**
 * Bottom Player Bar component displaying current playing song with playback controls.
 *
 * Tapping the bar expands the full screen player via [onExpand]. Dragging it up hands the gesture
 * to [pullToExpand] instead, so the sheet is pulled up under the finger rather than deciding for
 * itself how far to travel.
 */
@Composable
fun PlayerBar(
    playerState: PlayerState,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    getCoverArtUrl: (String?) -> String?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    pullToExpand: PullToExpand? = null,
) {
    val currentSong = playerState.currentSong

    // Where the bar sits on the screen, which is where a drag on it starts and therefore how far
    // the sheet has left to travel to be fully open.
    var barTopOnScreen by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        // On the wrapper rather than the Card, so the gesture covers the bar's whole footprint
        // and not only the card inset by its own padding.
        modifier = modifier.then(
            if (pullToExpand == null) Modifier else Modifier.onGloballyPositioned {
                barTopOnScreen = it.positionInWindow().y
            }.pointerInput(pullToExpand) {
                val pull = pullToExpand
                var grabbed = false
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (!grabbed) {
                            // Any vertical drag claims the sheet, not only an upward one: a finger
                            // rarely starts moving in exactly one direction, and by the time it
                            // has settled into one the first delta is gone. A drag that turns out
                            // to be downward moves nothing and releases into nothing.
                            grabbed = true
                            pull.onGrab(barTopOnScreen)
                        }
                        // Every delta from here on, in both directions: dragging back down is how
                        // a change of mind is expressed.
                        pull.onDrag(dragAmount)
                        change.consume()
                    },
                    onDragEnd = {
                        if (grabbed) {
                            grabbed = false
                            pull.onRelease()
                        }
                    },
                    onDragCancel = {
                        if (grabbed) {
                            grabbed = false
                            pull.onRelease()
                        }
                    },
                )
            },
        ),
    ) {
        if (currentSong != null) {
            Card(
                onClick = onExpand,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album Cover Art or Music Icon Fallback
                    val coverArtUrl = remember(currentSong.coverArt) {
                        getCoverArtUrl(currentSong.coverArt)
                    }

                    if (!coverArtUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = coverArtUrl,
                            contentDescription = currentSong.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Song Info (Title & Artist)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentSong.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = currentSong.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Play/Pause or Buffering Indicator
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onPlayPauseClick) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Stop Button
                    IconButton(onClick = onStopClick) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
