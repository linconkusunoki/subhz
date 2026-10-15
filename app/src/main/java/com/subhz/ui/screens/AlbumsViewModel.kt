package com.subhz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.data.api.Album
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =====================================================================
// 1. THE STATE (UI Representation)
// =====================================================================

/**
 * Represents the distinct states the Albums Screen can be in.
 */
sealed class AlbumsState {
    object Idle : AlbumsState()
    object Loading : AlbumsState()
    data class Success(val albums: List<Album>) : AlbumsState()
    data class Error(val message: String) : AlbumsState()
}

// =====================================================================
// 2. THE VIEWMODEL (Logic & State Management)
// =====================================================================

/**
 * ViewModel responsible for managing album data and UI state for the AlbumsScreen.
 * Fully compatible with Koin dependency injection.
 */
class AlbumsViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    // Internal mutable state flow
    private val _uiState = MutableStateFlow<AlbumsState>(AlbumsState.Idle)

    // Public immutable state flow exposed to the UI
    val uiState: StateFlow<AlbumsState> = _uiState.asStateFlow()

    /**
     * Triggers loading of albums from the signed-in server.
     */
    fun loadAlbums() {
        _uiState.value = AlbumsState.Loading

        viewModelScope.launch {
            val result = repository.getAlbums()

            result.fold(
                onSuccess = { albums ->
                    _uiState.value = AlbumsState.Success(albums)
                },
                onFailure = { error ->
                    _uiState.value = AlbumsState.Error(
                        error.message ?: "Failed to load albums from Navidrome"
                    )
                }
            )
        }
    }

    /**
     * Toggles star/favorite state for an Album item on the home list.
     */
    fun toggleStarAlbum(album: Album) {
        viewModelScope.launch {
            val isStarred = album.isStarred
            val result = repository.toggleStar(id = album.id, isStarred = isStarred, isAlbum = true)
            result.onSuccess { newStarred ->
                val currentState = _uiState.value
                if (currentState is AlbumsState.Success) {
                    val updatedAlbums = currentState.albums.map { item ->
                        if (item.id == album.id) {
                            item.copy(starred = if (newStarred) "starred" else null)
                        } else {
                            item
                        }
                    }
                    _uiState.value = AlbumsState.Success(updatedAlbums)
                }
            }
        }
    }

    /**
     * Generates a full authenticated cover art URL for a given coverArt ID.
     */
    fun getCoverArtUrl(coverArtId: String?): String? {
        if (coverArtId.isNullOrEmpty()) return null
        return repository.getCoverArtUrl(coverArtId)
    }
}
