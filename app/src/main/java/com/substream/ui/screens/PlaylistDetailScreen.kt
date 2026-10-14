package com.substream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.data.api.Playlist
import com.substream.player.PlayerManager
import com.substream.ui.components.SongsList
import com.substream.ui.components.formatSongDuration
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One playlist: its cover, name and tracks.
 *
 * The playlist is fetched whole rather than assembled from the summary getPlaylists.view sends,
 * because that one leaves the tracks out. Everything that changes the playlist does so on the
 * server and re-reads it, so the list on screen is the server's, not a local guess.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit = {},
    viewModel: PlaylistDetailViewModel = koinViewModel(),
    playerManager: PlayerManager = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Pair<Int, String>?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (uiState as? PlaylistDetailState.Success)?.playlist?.name ?: "Playlist",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (uiState is PlaylistDetailState.Success) {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuOpen = false
                                    renaming = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist") },
                                onClick = {
                                    menuOpen = false
                                    confirmingDelete = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is PlaylistDetailState.Idle, is PlaylistDetailState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is PlaylistDetailState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.loadPlaylist(playlistId) },
                    modifier = Modifier.align(Alignment.Center),
                )

                is PlaylistDetailState.Success -> PlaylistContent(
                    playlist = state.playlist,
                    getCoverArtUrl = viewModel::getCoverArtUrl,
                    onPlay = { startIndex ->
                        playerManager.playSongs(state.playlist.entry, startIndex)
                    },
                    onPlayAll = { playerManager.playSongs(state.playlist.entry) },
                    onShuffle = { playerManager.playSongs(state.playlist.entry.shuffled()) },
                    onRemoveTrack = { index, title -> removing = index to title },
                )
            }
        }
    }

    if (renaming) {
        RenamePlaylistDialog(
            initialName = (uiState as? PlaylistDetailState.Success)?.playlist?.name.orEmpty(),
            onConfirm = { name ->
                renaming = false
                viewModel.rename(name)
            },
            onDismiss = { renaming = false },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete(onDeleted = onBackClick)
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }

    removing?.let { (index, title) ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from playlist?") },
            text = { Text("\"$title\" will be taken out. The song itself is not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    viewModel.removeTrack(index)
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistContent(
    playlist: Playlist,
    getCoverArtUrl: (String?) -> String?,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRemoveTrack: (Int, String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            PlaylistHeader(
                playlist = playlist,
                coverArtUrl = getCoverArtUrl(playlist.coverArt ?: playlist.id),
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                // Nothing to play means nothing to press: an empty playlist gets no buttons.
                enabled = playlist.entry.isNotEmpty(),
            )
        }

        if (playlist.entry.isEmpty()) {
            item {
                Text(
                    text = "This playlist is empty.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item(key = "tracks") {
            SongsList(
                songs = playlist.entry,
                // The list is the server's order, so the index the player needs is this song's
                // place in it rather than a position handed down by the lazy layout.
                onSongClick = { song -> onPlay(playlist.entry.indexOf(song)) },
                getCoverArtUrl = getCoverArtUrl,
                onRemove = { song -> onRemoveTrack(playlist.entry.indexOf(song), song.title) },
            )
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    coverArtUrl: String?,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = coverArtUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp)),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        playlist.duration?.let { duration ->
            Text(
                text = formatSongDuration(duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onPlayAll, enabled = enabled) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play all")
            }

            FilledTonalButton(onClick = onShuffle, enabled = enabled) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                // An empty name would replace the playlist's name with nothing.
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}