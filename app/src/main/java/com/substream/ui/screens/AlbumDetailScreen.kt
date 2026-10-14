package com.substream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.data.api.AlbumDetail
import com.substream.data.api.Song
import com.substream.ui.components.AddToPlaylistSheet
import com.substream.ui.components.RatingBar
import com.substream.ui.components.formatSongDuration
import com.substream.player.PlayerManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Locale

// =====================================================================
// 1. MAIN SCREEN COMPOSABLE
// =====================================================================

/**
 * Album Detail Screen displaying the album header and full tracklist with favorite buttons.
 *
 * @param albumId The ID of the album to load.
 * @param onBackClick Optional callback for back navigation.
 * @param viewModel The Koin-injected AlbumDetailViewModel.
 * @param playerManager The Koin-injected PlayerManager for media playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBackClick: (() -> Unit)? = null,
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    viewModel: AlbumDetailViewModel = koinViewModel(),
    playerManager: PlayerManager = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var addingToPlaylist by remember { mutableStateOf<Song?>(null) }

    // Trigger loading album details whenever albumId changes
    LaunchedEffect(albumId) {
        viewModel.loadAlbumDetail(albumId)
    }

    AddToPlaylistSheet(song = addingToPlaylist, onDismiss = { addingToPlaylist = null })

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(text = "Album Details") },
                navigationIcon = {
                    onBackClick?.let { onClick ->
                        IconButton(onClick = onClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is AlbumDetailState.Idle, is AlbumDetailState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is AlbumDetailState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.loadAlbumDetail(albumId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is AlbumDetailState.Success -> {
                    AlbumDetailContent(
                        albumDetail = state.albumDetail,
                        getCoverArtUrl = { coverArtId -> viewModel.getCoverArtUrl(coverArtId) },
                        onSongClick = { song ->
                            val index = state.albumDetail.song.indexOf(song).coerceAtLeast(0)
                            playerManager.playSongs(state.albumDetail.song, index)
                        },
                        onArtistClick = onArtistClick,
                        onToggleStarAlbum = { albumDetail -> viewModel.toggleStarAlbum(albumDetail) },
                        onRate = { albumDetail, rating -> viewModel.rateAlbum(albumDetail, rating) },
                        onToggleStarSong = { song -> viewModel.toggleStarSong(song) },
                        onAddToPlaylist = { song -> addingToPlaylist = song }
                    )
                }
            }
        }
    }
}

// =====================================================================
// 2. ALBUM DETAIL CONTENT COMPOSABLE
// =====================================================================

/**
 * Main content layout rendering header info and LazyColumn for tracklist.
 */
@Composable
private fun AlbumDetailContent(
    albumDetail: AlbumDetail,
    getCoverArtUrl: (String?) -> String?,
    onSongClick: (Song) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onToggleStarAlbum: (AlbumDetail) -> Unit,
    onRate: (AlbumDetail, Int) -> Unit,
    onToggleStarSong: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // Album Header (Cover Art + Title + Artist + Favorite Button)
        item {
            AlbumHeader(
                albumDetail = albumDetail,
                coverArtUrl = getCoverArtUrl(albumDetail.coverArt),
                onArtistClick = onArtistClick,
                onToggleStarAlbum = { onToggleStarAlbum(albumDetail) },
                onRate = onRate,
            )
        }

        // Section Title
        item {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        // Song Tracklist
        itemsIndexed(
            items = albumDetail.song,
            key = { _, song -> song.id }
        ) { index, song ->
            SongRowItem(
                index = index + 1,
                song = song,
                onSongClick = onSongClick,
                onToggleStarSong = { onToggleStarSong(song) },
                onAddToPlaylist = { onAddToPlaylist(song) }
            )
        }
    }
}

// =====================================================================
// 3. ALBUM HEADER COMPOSABLE
// =====================================================================

/**
 * Header component featuring a large cover image, album name, artist, metadata, and favorite button.
 */
@Composable
private fun AlbumHeader(
    albumDetail: AlbumDetail,
    coverArtUrl: String?,
    onArtistClick: (String, String) -> Unit,
    onToggleStarAlbum: () -> Unit,
    onRate: (AlbumDetail, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large Album Cover Art with Star Button
        Box {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = albumDetail.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(220.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )

            IconButton(
                onClick = onToggleStarAlbum,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (albumDetail.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (albumDetail.isStarred) "Unfavorite" else "Favorite",
                    tint = if (albumDetail.isStarred) Color(0xFFFFC107) else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Album Title
        Text(
            text = albumDetail.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Artist Name. Linked when the server indexed the artist, plain text when it did not.
        val headerArtistName = albumDetail.artist ?: "Unknown Artist"
        val headerArtistId = albumDetail.artistId
        Text(
            text = headerArtistName,
            style = MaterialTheme.typography.titleMedium,
            color = if (headerArtistId != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = if (headerArtistId != null) {
                Modifier.clickable { onArtistClick(headerArtistId, headerArtistName) }
            } else {
                Modifier
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata Subtitle (Year & Song Count)
        val metadataText = listOfNotNull(
            albumDetail.year?.toString(),
            "${albumDetail.song.size} tracks"
        ).joinToString(" • ")

        if (metadataText.isNotEmpty()) {
            Text(
                text = metadataText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        RatingBar(
            rating = albumDetail.userRating,
            onRate = { rating -> onRate(albumDetail, rating) },
        )
    }
}

// =====================================================================
// 4. SONG ROW ITEM COMPOSABLE
// =====================================================================

/**
 * Individual track row component in the tracklist with favorite button.
 */
@Composable
private fun SongRowItem(
    index: Int,
    song: Song,
    onSongClick: (Song) -> Unit,
    onToggleStarSong: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Long press adds to a playlist; the tap plays. One more button on a row this dense
            // would be one more thing to miss.
            .combinedClickable(
                onClick = { onSongClick(song) },
                onLongClick = onAddToPlaylist,
                onLongClickLabel = "Add to playlist",
            )
            // No vertical padding: the star button's 48dp minimum touch target already sets the
            // row height, and padding on top of it only opens a gap between the titles.
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Text(
            text = song.track?.toString() ?: index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Song title. The artist is already named in the header above, so repeating it on
        // every row of the same album says nothing.
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Formatted Duration (mm:ss)
        Text(
            text = formatSongDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        // Favorite Star Button for Song
        IconButton(onClick = onToggleStarSong) {
            Icon(
                imageVector = if (song.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (song.isStarred) "Unfavorite" else "Favorite",
                tint = if (song.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================================
// 5. HELPER FUNCTIONS & ERROR UI
// =====================================================================

