package com.substream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.ui.components.FavoriteIcon
import com.substream.data.api.Album
import org.koin.androidx.compose.koinViewModel

// =====================================================================
// 1. MAIN SCREEN COMPOSABLE
// =====================================================================

/**
 * Main Composable function for the Albums Screen.
 * Uses Koin to inject the AlbumsViewModel and loads albums automatically upon entry.
 *
 * Set [showTopBar] to false to embed the list inside another screen (e.g. the Library tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (Album) -> Unit = {},
    onSearchClick: () -> Unit = {},
    title: String = "Albums",
    showTopBar: Boolean = true,
    viewModel: AlbumsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Trigger initial load when the screen enters the Composition
    LaunchedEffect(Unit) {
        viewModel.loadAlbums()
    }

    val content: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is AlbumsState.Idle, is AlbumsState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is AlbumsState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.loadAlbums() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is AlbumsState.Success -> {
                    if (state.albums.isEmpty()) {
                        Text(
                            text = "No albums found.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        AlbumList(
                            albums = state.albums,
                            onAlbumClick = onAlbumClick,
                            onToggleStar = { album -> viewModel.toggleStarAlbum(album) },
                            getCoverArtUrl = { coverArt -> viewModel.getCoverArtUrl(coverArt) }
                        )
                    }
                }
            }
        }
    }

    if (!showTopBar) {
        content(PaddingValues(0.dp))
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

// =====================================================================
// 2. ALBUM LIST AND ROW COMPOSABLES
// =====================================================================

/**
 * Renders the albums as one compact row per album.
 *
 * A row and not a card grid: two columns of square covers spend most of the screen on artwork
 * and leave the names short enough to be cut off, while a list fits twice as many albums on the
 * same screen and gives the names a line each. The Home shelves are the exception and keep
 * [AlbumCard], because they scroll sideways.
 */
@Composable
fun AlbumList(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onToggleStar: (Album) -> Unit,
    getCoverArtUrl: (String?) -> String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = albums,
            key = { album -> album.id }
        ) { album ->
            AlbumRow(
                album = album,
                coverArtUrl = getCoverArtUrl(album.coverArt),
                onClick = { onAlbumClick(album) },
                onToggleStar = { onToggleStar(album) }
            )
        }
    }
}

/**
 * One album as a row: cover, name, artist and the star.
 *
 * The artist is plain text. The row already opens the album, and a link inside it is the same
 * mis-tap trap the song rows had: the tap lands on one thing while the finger aimed at another.
 * [onToggleStar] is null where starring does not apply, so the row does not carry a dead button.
 */
@Composable
fun AlbumRow(
    album: Album,
    coverArtUrl: String?,
    onClick: () -> Unit,
    onToggleStar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The space between rows is outside the clickable: it belongs to neither row, so
            // it is not part of either tap target.
            .padding(vertical = 8.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = coverArtUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = album.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onToggleStar != null) {
            IconButton(onClick = onToggleStar) {
                FavoriteIcon(filled = album.isStarred)
            }
        }
    }
}

// =====================================================================
// 3. ITEM CARD COMPOSABLE
// =====================================================================

/**
 * Card component to represent an individual Album item.
 */
@Composable
fun AlbumCard(
    album: Album,
    coverArtUrl: String?,
    onClick: () -> Unit,
    onArtistClick: (artistId: String, artistName: String) -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    isPlayingLoading: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                // Album Cover Art loaded via Coil
                AsyncImage(
                    model = coverArtUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )

                // Favorite Star Button on Album Cover
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    FavoriteIcon(filled = album.isStarred, unfilledTint = Color.White)
                }

                // Play, where a shelf card can act rather than only lead somewhere. Bottom-start so
                // it does not sit beside the star it would be mistaken for.
                if (onPlay != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPlayingLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            IconButton(onClick = onPlay) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play ${album.name}",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            // Album Details (Title and Artist)
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Servers that index artists do send artistId here; the ones that don't leave
                // it null, and then the name stays plain text rather than a dead link.
                val artistName = album.artist ?: "Unknown Artist"
                val artistId = album.artistId
                if (artistId != null) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onArtistClick(artistId, artistName) }
                    )
                } else {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =====================================================================
// 4. ERROR CONTENT COMPOSABLE
// =====================================================================

/**
 * Display component for error state with a retry button.
 */
@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}
