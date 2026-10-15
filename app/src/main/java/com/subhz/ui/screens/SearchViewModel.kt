package com.subhz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.data.api.Album
import com.subhz.data.api.Artist
import com.subhz.data.api.SearchResult
import com.subhz.data.api.Song
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import kotlin.time.Duration.Companion.milliseconds

// =====================================================================
// 1. THE STATE (UI Representation)
// =====================================================================

/**
 * Represents the distinct states the Search Screen can be in.
 */
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val result: SearchResult) : SearchState()
    data class Error(val message: String) : SearchState()
}

// =====================================================================
// 2. THE VIEWMODEL (Logic & State Management)
// =====================================================================

/**
 * ViewModel responsible for managing reactive search queries and favorite (star/unstar) actions.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchState>(SearchState.Idle)
    val uiState: StateFlow<SearchState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(300.milliseconds)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _uiState.value = SearchState.Idle
                    } else {
                        performSearch(q)
                    }
                }
        }
    }

    /**
     * Updates search query input.
     */
    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    /**
     * Re-executes the search query manually (e.g. for retry).
     */
    fun search() {
        val currentQuery = _query.value
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        }
    }

    private fun performSearch(q: String) {
        _uiState.value = SearchState.Loading
        viewModelScope.launch {
            val result = repository.search(q)
            result.fold(
                onSuccess = { searchResult ->
                    _uiState.value = SearchState.Success(searchResult)
                },
                onFailure = { error ->
                    _uiState.value = SearchState.Error(
                        error.message ?: "Erro ao buscar resultados"
                    )
                }
            )
        }
    }

    /**
     * Toggles star/favorite state for a Song item.
     */
    fun toggleStarSong(song: Song) {
        viewModelScope.launch {
            val isStarred = song.isStarred
            val result = repository.toggleStar(id = song.id, isStarred = isStarred, isAlbum = false)
            result.onSuccess { newStarred ->
                updateSongStarred(song.id, newStarred)
            }
        }
    }

    /**
     * Toggles star/favorite state for an Album item.
     */
    fun toggleStarAlbum(album: Album) {
        viewModelScope.launch {
            val isStarred = album.isStarred
            val result = repository.toggleStar(id = album.id, isStarred = isStarred, isAlbum = true)
            result.onSuccess { newStarred ->
                updateAlbumStarred(album.id, newStarred)
            }
        }
    }

    private fun updateSongStarred(songId: String, newStarred: Boolean) {
        val currentState = _uiState.value
        if (currentState is SearchState.Success) {
            val updatedSongs = currentState.result.song.map { song ->
                if (song.id == songId) {
                    song.copy(starred = if (newStarred) "starred" else null)
                } else {
                    song
                }
            }
            val updatedResult = currentState.result.copy(song = updatedSongs)
            _uiState.value = SearchState.Success(updatedResult)
        }
    }

    /**
     * Toggles star/favorite state for an Artist item.
     */
    fun toggleStarArtist(artist: Artist) {
        viewModelScope.launch {
            val isStarred = artist.isStarred
            val result = repository.toggleStar(id = artist.id, isStarred = isStarred, isArtist = true)
            result.onSuccess { newStarred ->
                updateArtistStarred(artist.id, newStarred)
            }
        }
    }

    private fun updateArtistStarred(artistId: String, newStarred: Boolean) {
        val currentState = _uiState.value
        if (currentState !is SearchState.Success) return
        val updatedArtists = currentState.result.artist.map { artist ->
            if (artist.id == artistId) {
                artist.copy(starred = if (newStarred) "starred" else null)
            } else {
                artist
            }
        }
        _uiState.value = SearchState.Success(currentState.result.copy(artist = updatedArtists))
    }

    private fun updateAlbumStarred(albumId: String, newStarred: Boolean) {
        val currentState = _uiState.value
        if (currentState is SearchState.Success) {
            val updatedAlbums = currentState.result.album.map { album ->
                if (album.id == albumId) {
                    album.copy(starred = if (newStarred) "starred" else null)
                } else {
                    album
                }
            }
            val updatedResult = currentState.result.copy(album = updatedAlbums)
            _uiState.value = SearchState.Success(updatedResult)
        }
    }

    /**
     * Generates authenticated cover art URL.
     */
    fun getCoverArtUrl(coverArtId: String?): String? {
        if (coverArtId.isNullOrEmpty()) return null
        return repository.getCoverArtUrl(coverArtId)
    }
}
