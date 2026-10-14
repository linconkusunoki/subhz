package com.substream.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substream.data.api.Album
import com.substream.data.api.Artist
import com.substream.data.repository.SubsonicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How many albums each home shelf asks the server for. */
private const val SHELF_SIZE = 20

/** Pixel size asked of the server for home cover art, matching the 150dp shelf cards. */
private const val COVER_SIZE = 300

/**
 * Everything the home screen renders, loaded in one shot.
 *
 * Shelves that come back empty are simply not drawn: a library with no favorites should still
 * show its shelves rather than an error.
 */
data class HomeData(
    val recentlyAdded: List<Album> = emptyList(),
    val frequentlyPlayed: List<Album> = emptyList(),
    val madeForYou: List<Album> = emptyList(),
    val favorites: List<Album> = emptyList(),
    val favoriteArtists: List<Artist> = emptyList(),
)

/**
 * Home state. Its own type rather than the shared [LoadState] because the home loads several
 * lists at once, and LoadState carries exactly one.
 */
sealed interface HomeState {
    data object Idle : HomeState
    data object Loading : HomeState
    data class Success(val data: HomeData) : HomeState
    data class Error(val message: String) : HomeState
}

/**
 * ViewModel backing the home screen: getAlbumList2 (newest / frequent / random) and getStarred2.
 */
class HomeViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeState>(HomeState.Idle)
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    /**
     * Loads every home section concurrently so the screen appears after one round trip instead
     * of five. The first failure wins: a home that cannot reach the server shows the error and
     * the retry button rather than a half-empty page.
     */
    fun loadHome() {
        _uiState.value = HomeState.Loading

        viewModelScope.launch {
            try {
                val recent = async { repository.getAlbums(type = "newest", size = SHELF_SIZE) }
                val frequent = async { repository.getAlbums(type = "frequent", size = SHELF_SIZE) }
                val random = async { repository.getAlbums(type = "random", size = SHELF_SIZE) }
                val favorites = async { repository.getStarredAlbums() }
                val favoriteArtists = async { repository.getStarredArtists() }

                _uiState.value = HomeState.Success(
                    HomeData(
                        recentlyAdded = recent.await().getOrThrow().withImage(),
                        frequentlyPlayed = frequent.await().getOrThrow().withImage(),
                        madeForYou = random.await().getOrThrow().withImage(),
                        favorites = favorites.await().getOrThrow().withImage(),
                        // The grid at the top of the home is the starred artists. All of them are
                        // loaded: the row scrolls, and the screen fits six at a time.
                        favoriteArtists = favoriteArtists.await().getOrThrow().withArtistImage(),
                    )
                )
            } catch (e: Exception) {
                _uiState.value = HomeState.Error(e.message ?: "Could not load your home")
            }
        }
    }

    /**
     * Toggles an album's starred state and reflects it in every shelf showing that album, so the
     * same album cannot show a filled star in one row and an outline in another.
     */
    fun toggleStarAlbum(album: Album) {
        viewModelScope.launch {
            repository.toggleStar(id = album.id, isStarred = album.isStarred, isAlbum = true)
                .onSuccess { newStarred ->
                    val current = _uiState.value as? HomeState.Success ?: return@onSuccess
                    val starred = album.copy(starred = if (newStarred) "starred" else null)
                    _uiState.value = HomeState.Success(
                        current.data.copy(
                            recentlyAdded = current.data.recentlyAdded.replace(starred),
                            frequentlyPlayed = current.data.frequentlyPlayed.replace(starred),
                            madeForYou = current.data.madeForYou.replace(starred),
                            favorites = if (newStarred) {
                                (current.data.favorites + starred).distinctBy { it.id }
                            } else {
                                current.data.favorites.filterNot { it.id == album.id }
                            }
                        )
                    )
                }
        }
    }

    private fun List<Album>.replace(album: Album): List<Album> =
        map { if (it.id == album.id) album else it }

    /**
     * Generates a full authenticated cover art URL for a given coverArt ID, asking the server
     * for a home-sized image.
     */
    fun getCoverArtUrl(coverArtId: String?): String? {
        if (coverArtId.isNullOrEmpty()) return null
        return repository.getCoverArtUrl(coverArtId, size = COVER_SIZE)
    }
}

/**
 * Home tiles are nothing but artwork, so entries the server has no cover for are dropped here
 * rather than drawn as blank boxes.
 */
internal fun List<Album>.withImage(): List<Album> = filter { !it.coverArt.isNullOrEmpty() }

internal fun List<Artist>.withArtistImage(): List<Artist> = filter { !it.coverArt.isNullOrEmpty() }