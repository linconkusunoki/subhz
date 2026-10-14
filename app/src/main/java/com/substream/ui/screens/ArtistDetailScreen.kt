package com.substream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.ui.components.FavoriteIcon
import com.substream.ui.components.LoadState
import com.substream.data.api.Album
import com.substream.data.api.ArtistDetail
import com.substream.data.api.Song
import com.substream.ui.components.AddToPlaylistSheet
import com.substream.ui.components.RatingBar
import com.substream.ui.components.formatSongDuration
import com.substream.player.PlayerManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// =====================================================================
// 1. MAIN SCREEN COMPOSABLE
// =====================================================================

/**
 * Artist Detail Screen: artwork, name and star action over two tabs of content.
 *
 * getArtist.view returns albums only, so the songs cost a second search call. It is made as part
 * of loading the artist rather than when the Songs tab is opened, because the header's Play all
 * and Shuffle buttons are above the tabs and apply to the whole artist.
 *
 * @param artistId The ID of the artist to load.
 * @param onBackClick Optional callback for back navigation.
 * @param onAlbumClick Callback for album taps, navigating to the album detail.
 * @param viewModel The Koin-injected ArtistDetailViewModel.
 * @param playerManager The Koin-injected PlayerManager for media playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBackClick: (() -> Unit)? = null,
    onAlbumClick: (Album) -> Unit = {},
    viewModel: ArtistDetailViewModel = koinViewModel(),
    playerManager: PlayerManager = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var addingToPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }

    AddToPlaylistSheet(song = addingToPlaylist, onDismiss = { addingToPlaylist = null })

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Artist",
                        fontWeight = FontWeight.Bold
                    )
                },
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
                is ArtistDetailState.Idle, is ArtistDetailState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is ArtistDetailState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.loadArtist(artistId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is ArtistDetailState.Success -> {
                    // One list drives the Songs tab and every playback action, so the order on
                    // screen is the order that plays.
                    val songs = remember(state.songs) {
                        (state.songs as? LoadState.Success)?.items.orEmpty().groupedByAlbum()
                    }
                    ArtistDetailContent(
                        artistDetail = state.artistDetail,
                        songsState = state.songs,
                        songs = songs,
                        getCoverArtUrl = viewModel::getCoverArtUrl,
                        onAlbumClick = onAlbumClick,
                        onSongClick = { song ->
                            playerManager.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
                        },
                        onToggleStarArtist = { viewModel.toggleStarArtist(state.artistDetail) },
                        onToggleStarSong = viewModel::toggleStarSong,
                        onAddToPlaylist = { song -> addingToPlaylist = song },
                        onRate = { artistDetail, rating -> viewModel.rateArtist(artistDetail, rating) },
                        onPlayAll = { songs -> playerManager.playSongs(songs) },
                        onShuffleAll = { songs -> playerManager.playSongs(songs.shuffled()) },
                    )
                }
            }
        }
    }
}

// =====================================================================
// 2. ARTIST DETAIL CONTENT COMPOSABLE
// =====================================================================

private enum class ArtistTab(val label: String) {
    Albums("Albums"),
    Songs("Songs"),
}

/**
 * The whole screen is one LazyColumn: artwork, name, controls, tab row and tab content all scroll
 * together, so nothing is pinned and the content gets the full height of the window.
 *
 * That rules out a LazyVerticalGrid for the albums, which cannot be nested in a lazy layout
 * without a measured height. Instead the albums are emitted as rows of two, one lazy item per
 * row, which keeps the list lazy and the 2-column look intact.
 */
@Composable
private fun ArtistDetailContent(
    artistDetail: ArtistDetail,
    songsState: LoadState<Song>,
    songs: List<Song>,
    getCoverArtUrl: (String?) -> String?,
    onAlbumClick: (Album) -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleStarArtist: () -> Unit,
    onToggleStarSong: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onRate: (ArtistDetail, Int) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val songRows = remember(songs, artistDetail) { buildSongRows(songs, artistDetail) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "header") {
            ArtistHeader(
                artistDetail = artistDetail,
                coverArtUrl = getCoverArtUrl(artistDetail.coverArt),
                onToggleStarArtist = onToggleStarArtist,
                onPlayAll = { onPlayAll(songs) },
                onShuffleAll = { onShuffleAll(songs) },
                songsLoaded = songsState is LoadState.Success && songs.isNotEmpty(),
                onRate = onRate,
            )
        }

        item(key = "tabs") {
            TabRow(selectedTabIndex = selectedTab) {
                ArtistTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(text = tab.label) }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        when (selectedTab) {
            ArtistTab.Albums.ordinal -> {
                if (artistDetail.album.isEmpty()) {
                    item { EmptyArtistTab("No albums found.") }
                } else {
                    // One album per row now, so the albums go straight into the list. There is
                    // no star: the header above already stars the artist.
                    items(
                        items = artistDetail.album,
                        key = { album -> album.id }
                    ) { album ->
                        AlbumRow(
                            album = album,
                            coverArtUrl = getCoverArtUrl(album.coverArt),
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
            }

            else -> {
                when (songsState) {
                    is LoadState.Idle -> item { EmptyArtistTab("") }

                    is LoadState.Loading -> item { EmptyArtistTab("") }

                    is LoadState.Error -> item { EmptyArtistTab("No songs found.") }

                    is LoadState.Success -> {
                        if (songs.isEmpty()) {
                            item { EmptyArtistTab("No songs found.") }
                        } else {
                            itemsIndexed(
                                items = songRows,
                                key = { _, row -> row.key }
                            ) { _, row ->
                                when (row) {
                                    is SongTabItem.AlbumHeader -> AlbumGroupHeader(
                                        album = row.album,
                                        onAlbumClick = onAlbumClick
                                    )

                                    is SongTabItem.Track -> ArtistSongRow(
                                        index = row.index,
                                        song = row.song,
                                        onSongClick = onSongClick,
                                        onToggleStarSong = { onToggleStarSong(row.song) },
                                        onAddToPlaylist = { onAddToPlaylist(row.song) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// 3. ARTIST HEADER COMPOSABLE
// =====================================================================

/**
 * Full-bleed artist artwork with the name set over its bottom edge.
 *
 * The bottom scrim is a real gradient rather than a shadow: the name has to stay legible over
 * artwork of any brightness, and a drop shadow behind light text on a bright photo does not. It
 * is anchored to the bottom of the image only, so the top of the photo stays clean.
 */
@Composable
private fun ArtistHeader(
    artistDetail: ArtistDetail,
    coverArtUrl: String?,
    onToggleStarArtist: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    songsLoaded: Boolean,
    onRate: (ArtistDetail, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Half the available height: enough to read as a hero image without pushing the
            // name and controls off the fold. Derived from the viewport rather than hardcoded
            // so it holds on both a phone and a tablet.
            val heroHeight = maxHeight / 2

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            ) {
                AsyncImage(
                    model = coverArtUrl,
                    contentDescription = artistDetail.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                // Transparent through the top, dark at the bottom edge.
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.45f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.75f),
                                )
                            )
                        )
                )

                IconButton(
                    onClick = onToggleStarArtist,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    FavoriteIcon(filled = artistDetail.isStarred, unfilledTint = Color.White)
                }

                val albumCount = artistDetail.albumCount ?: artistDetail.album.size
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    RatingBar(
                        rating = artistDetail.userRating,
                        onRate = { rating -> onRate(artistDetail, rating) },
                        modifier = Modifier.align(Alignment.End),
                    )
                    Text(
                        text = artistDetail.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (albumCount == 1) "1 album" else "$albumCount albums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Disabled until the tracklist is in: playing an empty list is a silent no-op, so the buttons
        // wait for real songs rather than appearing to start something.
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onPlayAll,
                enabled = songsLoaded
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Play all")
            }

            FilledTonalButton(
                onClick = onShuffleAll,
                enabled = songsLoaded
            ) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Shuffle")
            }
        }
        }
    }
}

// =====================================================================
// 4. SONGS TAB MODEL, ALBUM HEADER AND SONG ROW
// =====================================================================

/**
 * One line of the Songs tab: an album heading or a track.
 */
private sealed interface SongTabItem {
    val key: String

    /** [album] carries an empty id when the server did not index the album, leaving it inert. */
    data class AlbumHeader(val album: Album, override val key: String) : SongTabItem

    data class Track(val index: Int, override val key: String, val song: Song) : SongTabItem
}

/**
 * Groups the songs by album, keeping the albums in the order they first appear and the songs in
 * the order the server returned them.
 *
 * search3 makes no promise that one album's songs come back together, and when they are not, a
 * heading per change lands between nearly every pair of tracks: the list stops reading as songs
 * and starts reading as headings, which is what a heading on its own row is for. Grouping is
 * what makes one heading per album true.
 */
private fun List<Song>.groupedByAlbum(): List<Song> {
    val byAlbum = LinkedHashMap<String, MutableList<Song>>()
    forEach { song ->
        byAlbum.getOrPut(song.album.orEmpty()) { mutableListOf() }.add(song)
    }
    return byAlbum.values.flatten()
}

/**
 * Flattens the songs into one heading per album followed by that album's tracks.
 *
 * The album name is a heading rather than a second line under every title, because an artist
 * with one album repeats that name once per track and it tells the reader nothing.
 *
 * Keys are the position in the song list, not the song id: a track listed on two albums comes
 * back twice with one id, and the lazy list throws on a duplicate key.
 */
private fun buildSongRows(songs: List<Song>, artist: ArtistDetail): List<SongTabItem> = buildList {
    var previousAlbum: String? = null
    songs.forEachIndexed { index, song ->
        val albumName = song.album?.takeIf { it.isNotEmpty() }
        if (albumName != null && albumName != previousAlbum) {
            add(
                SongTabItem.AlbumHeader(
                    album = Album(
                        id = song.albumId.orEmpty(),
                        name = albumName,
                        artist = artist.name,
                        artistId = artist.id
                    ),
                    key = "album-$index"
                )
            )
            previousAlbum = albumName
        }
        add(SongTabItem.Track(index = index + 1, key = "song-$index", song = song))
    }
}

/**
 * Album name above the first track of that album, with a rule under it so it reads as a heading
 * rather than a track missing its number, duration and star. Tapping it opens that album: a
 * heading the user can land on by accident has to be worth landing on.
 */
@Composable
private fun AlbumGroupHeader(
    album: Album,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linked = album.id.isNotEmpty()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (linked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Aligned with the track number column above it, and the space above it is what tells
            // one album's tracks from the next. The clickable is inside the padding so its
            // area is the heading itself and not the track below it.
            modifier = if (linked) {
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
                    .clickable { onAlbumClick(album) }
            } else {
                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
            }
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun ArtistSongRow(
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
            // Long press adds to a playlist; the tap plays.
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
        Text(
            text = song.track?.toString() ?: index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(12.dp))

        // The album is named once in the heading above its first track, not on every row.
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formatSongDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        IconButton(onClick = onToggleStarSong) {
            FavoriteIcon(filled = song.isStarred, unfilledTint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =====================================================================
// 5. HELPER FUNCTIONS & ERROR UI
// =====================================================================

@Composable
private fun EmptyArtistTab(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        if (message.isNotEmpty()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // No message yet: the songs are still being fetched, so this reserves the space a
            // message would occupy and stops the list from jumping when it arrives.
            CircularProgressIndicator()
        }
    }
}
