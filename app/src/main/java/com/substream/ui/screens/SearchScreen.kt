package com.substream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.data.api.Album
import com.substream.data.api.Artist
import com.substream.data.api.SearchResult
import com.substream.data.api.Song
import com.substream.ui.components.FavoriteIcon
import com.substream.ui.components.formatSongDuration
import com.substream.player.PlayerManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Locale

// =====================================================================
// 1. MAIN SEARCH SCREEN COMPOSABLE
// =====================================================================

/**
 * Main Composable function for the Search Screen.
 * Provides a reactive search text input, tabbed results for Albums and Songs, and favorite toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onBackClick: (() -> Unit)? = null,
    viewModel: SearchViewModel = koinViewModel(),
    playerManager: PlayerManager = koinInject()
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Selecting the Search tab (or coming back to it) puts the caret in the field and
    // raises the keyboard, so the user can type straight away. The destination's
    // composition is rebuilt on every tab switch, so this re-arms each time.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Search", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    onBackClick?.let { onClick ->
                        IconButton(onClick = onClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Text Field Input
            SearchInputField(
                query = query,
                onQueryChange = { viewModel.onQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
            )

            // Search Content / Results Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is SearchState.Idle -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Type a song or album name to search.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }

                    is SearchState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    is SearchState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = { viewModel.search() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    is SearchState.Success -> {
                        SearchResultsTabbedContent(
                            searchResult = state.result,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onSongClick = { song -> playerManager.playSong(song) },
                            onToggleStarAlbum = { album -> viewModel.toggleStarAlbum(album) },
                            onToggleStarArtist = { artist -> viewModel.toggleStarArtist(artist) },
                            onToggleStarSong = { song -> viewModel.toggleStarSong(song) },
                            getCoverArtUrl = { coverArt -> viewModel.getCoverArtUrl(coverArt) }
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// 2. SEARCH INPUT FIELD COMPOSABLE
// =====================================================================

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Artists, albums, songs...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear"
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
    )
}

// =====================================================================
// 3. TABBED RESULTS COMPOSABLE
// =====================================================================

@Composable
private fun SearchResultsTabbedContent(
    searchResult: SearchResult,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleStarAlbum: (Album) -> Unit,
    onToggleStarArtist: (Artist) -> Unit,
    onToggleStarSong: (Song) -> Unit,
    getCoverArtUrl: (String?) -> String?,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Artists (${searchResult.artist.size})",
        "Albums (${searchResult.album.size})",
        "Songs (${searchResult.song.size})"
    )

    if (searchResult.artist.isEmpty() && searchResult.album.isEmpty() && searchResult.song.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results found.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                if (searchResult.artist.isEmpty()) {
                    EmptySectionText(message = "No artists found.")
                } else {
                    ArtistList(
                        artists = searchResult.artist,
                        onArtistClick = { artist -> onArtistClick(artist.id, artist.name) },
                        onToggleStar = onToggleStarArtist,
                        getCoverArtUrl = getCoverArtUrl,
                    )
                }
            }

            1 -> {
                if (searchResult.album.isEmpty()) {
                    EmptySectionText(message = "No albums found.")
                } else {
                    AlbumList(
                        albums = searchResult.album,
                        onAlbumClick = onAlbumClick,
                        onToggleStar = onToggleStarAlbum,
                        getCoverArtUrl = getCoverArtUrl
                    )
                }
            }

            else -> {
                if (searchResult.song.isEmpty()) {
                    EmptySectionText(message = "No songs found.")
                } else {
                    SearchSongsList(
                        songs = searchResult.song,
                        onSongClick = onSongClick,
                        onArtistClick = onArtistClick,
                        onToggleStar = onToggleStarSong,
                        getCoverArtUrl = getCoverArtUrl
                    )
                }
            }
        }
    }
}

// =====================================================================
// 4. SONGS LIST COMPOSABLE
// =====================================================================

@Composable
private fun SearchSongsList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onToggleStar: (Song) -> Unit,
    getCoverArtUrl: (String?) -> String?
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = songs,
            key = { song -> song.id }
        ) { song ->
            SearchSongRowItem(
                song = song,
                coverArtUrl = getCoverArtUrl(song.coverArt),
                onSongClick = { onSongClick(song) },
                onArtistClick = onArtistClick,
                onToggleStar = { onToggleStar(song) }
            )
        }
    }
}

@Composable
private fun SearchSongRowItem(
    song: Song,
    coverArtUrl: String?,
    onSongClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onToggleStar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSongClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        AsyncImage(
            model = coverArtUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist/Album Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = listOfNotNull(song.artist, song.album).joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                // "Artist • Album" is one string, so only the artist half is linkable; the whole
                // subtitle becomes the tap target rather than trying to split the Text.
                val songArtistId = song.artistId
                val songArtistName = song.artist
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (songArtistId != null && songArtistName != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (songArtistId != null && songArtistName != null) {
                        Modifier.clickable { onArtistClick(songArtistId, songArtistName) }
                    } else {
                        Modifier
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Song Duration
        Text(
            text = formatSongDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        // Favorite Star Button
        IconButton(onClick = onToggleStar) {
            FavoriteIcon(filled = song.isStarred, unfilledTint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =====================================================================
// 5. HELPER COMPONENTS
// =====================================================================

@Composable
private fun EmptySectionText(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
