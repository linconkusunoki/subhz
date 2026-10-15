package com.subhz.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.data.api.Playlist
import com.subhz.data.api.Song
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Picks the playlist a song goes into.
 *
 * Reached by long-pressing a track rather than from a button on the row: every song list would
 * otherwise carry another control, and the rows are already dense with the play target, the star
 * and the length. Nothing is drawn until the sheet opens.
 *
 * Pass null as [song] when there is nothing to add; the sheet then draws nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song?,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = koinViewModel(),
) {
    if (song == null) return

    val playlists by viewModel.playlists.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(song.id) {
        viewModel.loadPlaylists()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            when (val state = playlists) {
                is LoadState.Idle, is LoadState.Loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }

                is LoadState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )

                is LoadState.Success -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(
                            items = listOf(NEW_PLAYLIST) + state.items,
                            key = { playlist -> playlist.id },
                        ) { playlist ->
                            PlaylistChoice(
                                playlist = playlist,
                                onClick = {
                                    if (playlist.id == NEW_PLAYLIST_ID) {
                                        creating = true
                                    } else {
                                        viewModel.addTo(playlist, song)
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NewPlaylistDialog(
            onConfirm = { name ->
                creating = false
                viewModel.createWith(name, song)
                onDismiss()
            },
            onDismiss = { creating = false },
        )
    }
}

/** Stands in for the "new playlist" row, so one list can carry it. */
private val NEW_PLAYLIST_ID = "new-playlist"
private val NEW_PLAYLIST = Playlist(id = NEW_PLAYLIST_ID, name = "New playlist")

@Composable
private fun PlaylistChoice(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playlist.id == NEW_PLAYLIST_ID) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.size(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            playlist.songCount?.let { count ->
                Text(
                    text = if (count == 1) "1 song" else "$count songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewPlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Loads the playlists and writes the song into one. Failures are swallowed on purpose: the sheet is
 * closing either way, and the next time the user opens the song's playlist they will see whether the
 * song arrived.
 */
class AddToPlaylistViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    private val _playlists = MutableStateFlow<LoadState<Playlist>>(LoadState.Idle)
    val playlists: StateFlow<LoadState<Playlist>> = _playlists.asStateFlow()

    fun loadPlaylists() {
        _playlists.value = LoadState.Loading
        viewModelScope.launch {
            repository.getPlaylists().fold(
                onSuccess = { playlists -> _playlists.value = LoadState.Success(playlists) },
                onFailure = { error ->
                    _playlists.value = LoadState.Error(
                        error.message ?: "Could not load your playlists"
                    )
                }
            )
        }
    }

    fun addTo(playlist: Playlist, song: Song) {
        viewModelScope.launch { repository.addSongsToPlaylist(playlist.id, listOf(song.id)) }
    }

    fun createWith(name: String, song: Song) {
        viewModelScope.launch { repository.createPlaylist(name = name, songIds = listOf(song.id)) }
    }
}