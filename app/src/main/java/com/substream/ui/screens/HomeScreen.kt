package com.substream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.data.api.Album
import com.substream.data.api.Artist
import java.util.Calendar
import org.koin.androidx.compose.koinViewModel

/**
 * Spotify-shaped home: a greeting, a grid of the artists you have starred, and horizontal shelves
 * of albums fed by getAlbumList2 (newest / frequent / random) and getStarred2.
 *
 * The main Scaffold already owns the window insets, same as every other destination.
 */
@Composable
fun HomeScreen(
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val loadingAlbumId by viewModel.loadingAlbumId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHome()
    }

    // Top insets only: this screen has no TopAppBar to claim the status bar, and the main
    // Scaffold's NavigationBar + PlayerBar already sit on the bottom insets.
    Scaffold(contentWindowInsets = WindowInsets.statusBars) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val current = state) {
                is HomeState.Idle, is HomeState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                is HomeState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = viewModel::loadHome,
                    modifier = Modifier.align(Alignment.Center)
                )

                is HomeState.Success -> HomeContent(
                    data = current.data,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onSearchClick = onSearchClick,
                    onToggleStar = viewModel::toggleStarAlbum,
                    onPlayAlbum = viewModel::playAlbum,
                    loadingAlbumId = loadingAlbumId,
                    getCoverArtUrl = viewModel::getCoverArtUrl,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onSearchClick: () -> Unit,
    onToggleStar: (Album) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    loadingAlbumId: String?,
    getCoverArtUrl: (String?) -> String?,
) {
    val shelves = listOf(
        "Recently added" to data.recentlyAdded,
        "Frequently played" to data.frequentlyPlayed,
        "Made for you" to data.madeForYou,
        "Your favorites" to data.favorites,
    )
    if (data.favoriteArtists.isEmpty() && shelves.all { it.second.isEmpty() }) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Nothing to show yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Bottom padding keeps the last shelf off the mini player and tab bar: this column is
    // already inset by the main Scaffold's bottomBar, so the last row would sit flush
    // against them otherwise.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSearchClick) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                }
            }
        }

        if (data.favoriteArtists.isNotEmpty()) {
            item {
                QuickPicks(
                    artists = data.favoriteArtists,
                    getCoverArtUrl = getCoverArtUrl,
                    onArtistClick = onArtistClick,
                )
            }
        }

        // Empty shelves are dropped rather than rendered as a bare heading.
        shelves.forEach { (title, albums) ->
            if (albums.isNotEmpty()) {
                item(key = title) {
                    ShelfRow(title = title, items = albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            coverArtUrl = getCoverArtUrl(album.coverArt),
                            onClick = { onAlbumClick(album) },
                            onArtistClick = onArtistClick,
                            onToggleStar = { onToggleStar(album) },
                            onPlay = { onPlayAlbum(album) },
                            isPlayingLoading = loadingAlbumId == album.id,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A titled horizontal shelf. Cards get a fixed width so they stay card-sized while the row
 * scrolls, which is why the width is set here rather than inside [AlbumCard].
 */
@Composable
private fun <T> ShelfRow(
    title: String,
    items: List<T>,
    key: (T) -> String,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = items, key = key) { item ->
                Column(modifier = Modifier.width(150.dp)) {
                    itemContent(item)
                }
            }
        }
    }
}

/**
 * How many starred artists one screen of the home row holds: three rows of two.
 */
private const val ARTISTS_PER_PAGE = 6

/**
 * The starred artists, two to a row and three rows deep, scrolling sideways for the next screen.
 *
 * The grid is built from chunked rows rather than a LazyVerticalGrid because it cannot be nested
 * in the vertical scroll with a bounded height, and the whole grid is one lazy item per screen
 * rather than one per artist so it can scroll sideways.
 */
@Composable
private fun QuickPicks(
    artists: List<Artist>,
    getCoverArtUrl: (String?) -> String?,
    onArtistClick: (String, String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // A page has to be told how wide it is: inside a LazyRow the children are measured with
        // an unbounded width, so the two tiles could not share it.
        val pageWidth = maxWidth - 16.dp * 2

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            // Pages are laid out one after another, so without a gap the last column of this
            // screen sits against the first column of the next one.
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = artists.chunked(ARTISTS_PER_PAGE),
                key = { page -> page.first().id }
            ) { page ->
                Column(
                    modifier = Modifier.width(pageWidth),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    page.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { artist ->
                                QuickPickTile(
                                    artist = artist,
                                    coverArtUrl = getCoverArtUrl(artist.coverArt),
                                    onClick = { onArtistClick(artist.id, artist.name) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keeps a lone artist in the last row from stretching across it.
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickTile(
    artist: Artist,
    coverArtUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                // Artists the server has no image for still get a filled tile, not a hole.
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = coverArtUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${artist.albumCount ?: 0} albums",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Time-of-day greeting shown above the home content. */
internal fun greetingFor(hourOfDay: Int): String = when (hourOfDay) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}