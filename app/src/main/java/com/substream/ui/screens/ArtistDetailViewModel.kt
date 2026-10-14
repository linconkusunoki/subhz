package com.substream.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substream.ui.components.LoadState
import com.substream.data.api.ArtistDetail
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
 * Represents the distinct states the Artist Detail Screen can be in.
 *
 * [Success.songs] is a [LoadState] of its own because it costs a second search call that
 * getArtist.view does not make: getArtist.view returns albums only, and the songs have to be
 * searched for by name. It resolves after the artist detail, so the screen can render the albums
 * while the tracklist is still on its way.
 */
sealed class ArtistDetailState {
    object Idle : ArtistDetailState()
    object Loading : ArtistDetailState()
    data class Success(
        val artistDetail: ArtistDetail,
        val songs: LoadState<Song> = LoadState.Idle,
    ) : ArtistDetailState()
    data class Error(val message: String) : ArtistDetailState()
}

// =====================================================================
// 2. THE VIEWMODEL (Logic & State Management)
// =====================================================================

/**
 * ViewModel responsible for fetching and managing an artist's albums and songs.
 * Injectable via Koin with optional SavedStateHandle navigation support.
 */
class ArtistDetailViewModel(
    private val repository: SubsonicRepository,
    savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArtistDetailState>(ArtistDetailState.Idle)
    val uiState: StateFlow<ArtistDetailState> = _uiState.asStateFlow()

    // The init block below and the screen's LaunchedEffect both load on first open, so this guard
    // is what keeps that from fetching the artist and its songs twice.
    private var loadedArtistId: String? = null

    init {
        val initialArtistId: String? = savedStateHandle?.get("artistId")
        if (!initialArtistId.isNullOrEmpty()) {
            loadArtist(initialArtistId)
        }
    }

    /**
     * Loads an artist with their albums and songs.
     *
     * The songs are part of this load rather than fetched when the Songs tab is opened: the
     * header's Play all and Shuffle buttons sit above the tabs and apply to the whole artist, so
     * they have to work from the Albums tab too.
     */
    fun loadArtist(artistId: String) {
        if (artistId.isEmpty() || loadedArtistId == artistId) return
        loadedArtistId = artistId
        _uiState.value = ArtistDetailState.Loading

        viewModelScope.launch {
            repository.getArtistDetails(artistId).fold(
                onSuccess = { detail ->
                    // Published before the songs resolve: the albums are worth showing straight
                    // away, and the header's controls wait on [Success.songs] regardless.
                    _uiState.value = ArtistDetailState.Success(detail, songs = LoadState.Loading)

                    val songs = fetchSongs(detail)
                    val latest = _uiState.value
                    if (latest is ArtistDetailState.Success && latest.artistDetail.id == detail.id) {
                        _uiState.value = latest.copy(songs = songs)
                    }
                },
                onFailure = { error ->
                    // Cleared so the screen's retry button actually refetches.
                    loadedArtistId = null
                    _uiState.value = ArtistDetailState.Error(
                        error.message ?: "Failed to load artist details"
                    )
                }
            )
        }
    }

    /**
     * Fetches an artist's songs.
     *
     * A failure is not an error state worth shouting about: an artist whose songs the server
     * cannot match by name simply shows as an empty tracklist.
     */
    private suspend fun fetchSongs(artistDetail: ArtistDetail): LoadState<Song> =
        repository.getArtistSongs(
            artistId = artistDetail.id,
            artistName = artistDetail.name,
        ).fold(
            onSuccess = { LoadState.Success(it) },
            onFailure = { LoadState.Success(emptyList()) },
        )

    /**
     * Toggles star/favorite state for the artist.
     */
    /**
     * Rates the artist 0-5 and writes it into the loaded copy so the stars move without a refetch.
     */
    fun rateArtist(artistDetail: ArtistDetail, rating: Int) {
        viewModelScope.launch {
            repository.setRating(id = artistDetail.id, rating = rating).onSuccess { newRating ->
                val current = _uiState.value
                if (current is ArtistDetailState.Success) {
                    _uiState.value = current.copy(
                        artistDetail = current.artistDetail.copy(
                            userRating = newRating.takeIf { it > 0 }
                        )
                    )
                }
            }
        }
    }

    fun toggleStarArtist(artistDetail: ArtistDetail) {
        viewModelScope.launch {
            val result = repository.toggleStar(
                id = artistDetail.id,
                isStarred = artistDetail.isStarred,
                isArtist = true,
            )
            result.onSuccess { newStarred ->
                val currentState = _uiState.value
                if (currentState is ArtistDetailState.Success) {
                    _uiState.value = currentState.copy(
                        artistDetail = currentState.artistDetail.copy(
                            starred = if (newStarred) "starred" else null
                        )
                    )
                }
            }
        }
    }

    /**
     * Toggles star/favorite state for a song in the artist's tracklist.
     */
    fun toggleStarSong(song: Song) {
        viewModelScope.launch {
            val result = repository.toggleStar(id = song.id, isStarred = song.isStarred)
            result.onSuccess { newStarred ->
                val currentState = _uiState.value
                if (currentState !is ArtistDetailState.Success) return@onSuccess
                val currentSongs = currentState.songs
                if (currentSongs !is LoadState.Success) return@onSuccess

                _uiState.value = currentState.copy(
                    songs = LoadState.Success(
                        currentSongs.items.map {
                            if (it.id == song.id) {
                                it.copy(starred = if (newStarred) "starred" else null)
                            } else {
                                it
                            }
                        }
                    )
                )
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