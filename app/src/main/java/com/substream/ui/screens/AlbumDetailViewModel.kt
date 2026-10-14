package com.substream.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substream.data.api.AlbumDetail
import com.substream.data.api.Song
import com.substream.data.repository.SubsonicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =====================================================================
// 1. THE STATE (UI Representation)
// =====================================================================

/**
 * Represents the distinct states the Album Detail Screen can be in.
 */
sealed class AlbumDetailState {
    object Idle : AlbumDetailState()
    object Loading : AlbumDetailState()
    data class Success(val albumDetail: AlbumDetail) : AlbumDetailState()
    data class Error(val message: String) : AlbumDetailState()
}

// =====================================================================
// 2. THE VIEWMODEL (Logic & State Management)
// =====================================================================

/**
 * ViewModel responsible for fetching and managing album details and tracklist.
 * Injectable via Koin with optional SavedStateHandle navigation support.
 */
class AlbumDetailViewModel(
    private val repository: SubsonicRepository,
    savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlbumDetailState>(AlbumDetailState.Idle)
    val uiState: StateFlow<AlbumDetailState> = _uiState.asStateFlow()

    init {
        // Automatically load if albumId was passed via SavedStateHandle / Navigation args
        val initialAlbumId: String? = savedStateHandle?.get("albumId")
        if (!initialAlbumId.isNullOrEmpty()) {
            loadAlbumDetail(initialAlbumId)
        }
    }

    /**
     * Loads the details and song list for a given album ID.
     */
    fun loadAlbumDetail(albumId: String) {
        _uiState.value = AlbumDetailState.Loading

        viewModelScope.launch {
            val result = repository.getAlbumDetails(albumId)

            result.fold(
                onSuccess = { detail ->
                    _uiState.value = AlbumDetailState.Success(detail)
                },
                onFailure = { error ->
                    _uiState.value = AlbumDetailState.Error(
                        error.message ?: "Failed to load album details"
                    )
                }
            )
        }
    }

    /**
     * Toggles star/favorite state for the current AlbumDetail.
     */
    /**
     * Rates the album 0-5 and writes it into the loaded copy so the stars move without a refetch.
     */
    fun rateAlbum(albumDetail: AlbumDetail, rating: Int) {
        viewModelScope.launch {
            repository.setRating(id = albumDetail.id, rating = rating).onSuccess { newRating ->
                val current = _uiState.value
                if (current is AlbumDetailState.Success) {
                    _uiState.value = current.copy(
                        albumDetail = current.albumDetail.copy(userRating = newRating.takeIf { it > 0 })
                    )
                }
            }
        }
    }

    fun toggleStarAlbum(albumDetail: AlbumDetail) {
        viewModelScope.launch {
            val isStarred = albumDetail.isStarred
            val result = repository.toggleStar(id = albumDetail.id, isStarred = isStarred, isAlbum = true)
            result.onSuccess { newStarred ->
                val currentState = _uiState.value
                if (currentState is AlbumDetailState.Success) {
                    val updatedDetail = currentState.albumDetail.copy(
                        starred = if (newStarred) "starred" else null
                    )
                    _uiState.value = AlbumDetailState.Success(updatedDetail)
                }
            }
        }
    }

    /**
     * Toggles star/favorite state for a Song item in the tracklist.
     */
    fun toggleStarSong(song: Song) {
        viewModelScope.launch {
            val isStarred = song.isStarred
            val result = repository.toggleStar(id = song.id, isStarred = isStarred, isAlbum = false)
            result.onSuccess { newStarred ->
                val currentState = _uiState.value
                if (currentState is AlbumDetailState.Success) {
                    val updatedSongs = currentState.albumDetail.song.map { item ->
                        if (item.id == song.id) {
                            item.copy(starred = if (newStarred) "starred" else null)
                        } else {
                            item
                        }
                    }
                    val updatedDetail = currentState.albumDetail.copy(song = updatedSongs)
                    _uiState.value = AlbumDetailState.Success(updatedDetail)
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
