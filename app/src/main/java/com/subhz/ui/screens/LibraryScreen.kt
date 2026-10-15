package com.subhz.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.subhz.ui.components.LoadState
import com.subhz.data.api.Album
import com.subhz.data.api.Playlist
import com.subhz.player.PlayerManager
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Library hub: the bottom-nav "Library" destination with an internal tab bar
 * (Albums / Artists / Playlists / Favorites).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onPlaylistClick: (Playlist) -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(),
    playerManager: PlayerManager = koinInject(),
) {
    val artistsState by viewModel.artists.collectAsState()
    val playlistsState by viewModel.playlists.collectAsState()
    val starredState by viewModel.starred.collectAsState()
    val starredArtistsState by viewModel.starredArtists.collectAsState()
    val starredSongsState by viewModel.starredSongs.collectAsState()

    // The pager is the single source of truth for the selected tab: tab clicks animate
    // the page and swipes move the selection, so the two can never disagree.
    val pagerState = rememberPagerState { LibraryTab.entries.size }
    val scope = rememberCoroutineScope()

    // Load every section once when the hub enters composition: tab labels show the counts.
    LaunchedEffect(Unit) {
        viewModel.loadArtists()
        viewModel.loadPlaylists()
        viewModel.loadStarred()
    }

    val counts = listOf(
        null,
        (artistsState as? LoadState.Success)?.items?.size,
        (playlistsState as? LoadState.Success)?.items?.size,
        (starredState as? LoadState.Success)?.items?.size,
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Library", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    LibraryTab.entries.forEachIndexed { index, tab ->
                        val count = counts[index]
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    text = if (count != null) "${tab.label} ($count)" else tab.label,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (LibraryTab.entries[page]) {
                LibraryTab.Albums -> AlbumsScreen(
                    onAlbumClick = onAlbumClick,
                    showTopBar = false,
                )

                LibraryTab.Artists -> ArtistsScreen(
                    state = artistsState,
                    onRetry = viewModel::loadArtists,
                    onArtistClick = { artist -> onArtistClick(artist.id, artist.name) },
                    onToggleStar = viewModel::toggleStarArtist,
                    getCoverArtUrl = viewModel::getCoverArtUrl,
                )

                LibraryTab.Playlists -> PlaylistsScreen(
                    state = playlistsState,
                    onRetry = viewModel::loadPlaylists,
                    onPlaylistClick = { playlist -> onPlaylistClick(playlist) },
                    getCoverArtUrl = viewModel::getCoverArtUrl,
                )

                LibraryTab.Favorites -> FavoritesScreen(
                    state = starredState,
                    artistsState = starredArtistsState,
                    songsState = starredSongsState,
                    onRetry = viewModel::loadStarred,
                    onRemove = viewModel::removeStarred,
                    onToggleStarArtist = viewModel::toggleStarredArtist,
                    onToggleStarSong = viewModel::removeStarredSong,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onSongClick = { song ->
                        val songs = (starredSongsState as? LoadState.Success)?.items.orEmpty()
                        playerManager.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
                    },
                    getCoverArtUrl = viewModel::getCoverArtUrl,
                )
            }
        }
    }
}

/** Internal tabs of the Library hub. */
private enum class LibraryTab(val label: String) {
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
    Favorites("Favorites"),
}

/**
 * Renders a [LoadState]: spinner while loading, error with retry, empty message or content.
 */
@Composable
fun <T> LoadStateContent(
    state: LoadState<T>,
    emptyMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (List<T>) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is LoadState.Idle, is LoadState.Loading -> CircularProgressIndicator()

            is LoadState.Error -> ErrorContent(
                message = state.message,
                onRetry = onRetry
            )

            is LoadState.Success -> {
                if (state.items.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    content(state.items)
                }
            }
        }
    }
}
