package com.substream.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.substream.data.api.Album
import com.substream.data.api.Artist

/** Sub-tabs of the Favorites hub. */
private enum class FavoriteTab(val label: String) {
    Albums("Albums"),
    Artists("Artists"),
}

/**
 * Starred albums and artists served by getStarred2.view.
 * Rendered inside LibraryScreen, so it has no TopAppBar of its own.
 *
 * Two tabs rather than one long list: two lazy grids stacked in a Column would need measured
 * heights and only one of them scrolls.
 */
@Composable
fun FavoritesScreen(
    state: LoadState<Album>,
    artistsState: LoadState<Artist>,
    onRetry: () -> Unit,
    onRemove: (Album) -> Unit,
    onToggleStarArtist: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String, String) -> Unit,
    getCoverArtUrl: (String?) -> String?,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            FavoriteTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(text = tab.label) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f)) {
            when (FavoriteTab.entries[selectedTab]) {
                FavoriteTab.Albums -> LoadStateContent(
                    state = state,
                    emptyMessage = "Your favorite albums will show up here.",
                    onRetry = onRetry,
                ) { albums ->
                    AlbumList(
                        albums = albums,
                        onAlbumClick = onAlbumClick,
                        onToggleStar = onRemove,
                        getCoverArtUrl = getCoverArtUrl,
                    )
                }

                FavoriteTab.Artists -> LoadStateContent(
                    state = artistsState,
                    emptyMessage = "Your favorite artists will show up here.",
                    onRetry = onRetry,
                ) { artists ->
                    ArtistList(
                        artists = artists,
                        onArtistClick = { artist -> onArtistClick(artist.id, artist.name) },
                        onToggleStar = onToggleStarArtist,
                        getCoverArtUrl = getCoverArtUrl,
                    )
                }
            }
        }
    }
}