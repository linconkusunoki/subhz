package com.subhz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.ui.components.LoadState
import com.subhz.data.api.Playlist
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of one playlist. Its own type rather than the shared [LoadState] because it carries a
 * failure that only matters while editing: the tracks are still shown, but the change did not land.
 */
sealed interface PlaylistDetailState {
    data object Idle : PlaylistDetailState
    data object Loading : PlaylistDetailState
    data class Success(
        val playlist: Playlist,
        /** Set when the last change failed, so the screen can say so without hiding the tracks. */
        val errorMessage: String? = null,
    ) : PlaylistDetailState

    data class Error(val message: String) : PlaylistDetailState
}

/**
 * ViewModel backing PlaylistDetailScreen.
 *
 * Every change writes to the server and then re-reads the playlist. Guessing at the result locally
 * would drift: the server is what decides the final track order and the counts, so what the screen
 * shows is simply what the server last said.
 */
class PlaylistDetailViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistDetailState>(PlaylistDetailState.Idle)
    val uiState: StateFlow<PlaylistDetailState> = _uiState.asStateFlow()

    private var playlistId: String? = null

    fun loadPlaylist(id: String) {
        playlistId = id
        _uiState.value = PlaylistDetailState.Loading
        viewModelScope.launch {
            repository.getPlaylist(id).fold(
                onSuccess = { playlist -> _uiState.value = PlaylistDetailState.Success(playlist) },
                onFailure = { error ->
                    _uiState.value = PlaylistDetailState.Error(
                        error.message ?: "Could not load the playlist"
                    )
                }
            )
        }
    }

    fun rename(name: String) = change { repository.renamePlaylist(it, name) }

    fun removeTrack(index: Int) = change { repository.removeSongFromPlaylist(it, index) }

    fun addSongs(songIds: List<String>) = change { repository.addSongsToPlaylist(it, songIds) }

    /**
     * Deletes the playlist, then hands control back so the screen closes itself.
     */
    fun delete(onDeleted: () -> Unit) {
        val id = playlistId ?: return
        viewModelScope.launch {
            repository.deletePlaylist(id).fold(
                onSuccess = { onDeleted() },
                onFailure = { error ->
                    val current = _uiState.value
                    if (current is PlaylistDetailState.Success) {
                        _uiState.value = current.copy(
                            errorMessage = error.message ?: "Could not delete the playlist"
                        )
                    }
                }
            )
        }
    }

    /**
     * Runs a change against the server and reloads. A failure keeps the playlist on screen with
     * the error attached rather than replacing it with an error page, which would throw away the
     * tracklist the user is looking at over a failed rename.
     */
    private fun change(block: suspend (String) -> Result<*>) {
        val id = playlistId ?: return
        viewModelScope.launch {
            block(id).fold(
                onSuccess = { loadPlaylist(id) },
                onFailure = { error ->
                    val current = _uiState.value
                    if (current is PlaylistDetailState.Success) {
                        _uiState.value = current.copy(
                            errorMessage = error.message ?: "That change did not save"
                        )
                    }
                }
            )
        }
    }

    fun getCoverArtUrl(coverArtId: String?): String? {
        if (coverArtId.isNullOrEmpty()) return null
        return repository.getCoverArtUrl(coverArtId)
    }
}