package com.substream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.ui.components.LoadState
import com.substream.data.api.Artist

/**
 * List of artists served by getArtists.view.
 * Rendered inside LibraryScreen, so it has no TopAppBar of its own.
 */
@Composable
fun ArtistsScreen(
    state: LoadState<Artist>,
    onRetry: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onToggleStar: (Artist) -> Unit,
    getCoverArtUrl: (String?) -> String?,
) {
    LoadStateContent(
        state = state,
        emptyMessage = "No artists found.",
        onRetry = onRetry,
    ) { artists ->
        ArtistList(
            artists = artists,
            onArtistClick = onArtistClick,
            onToggleStar = onToggleStar,
            getCoverArtUrl = getCoverArtUrl,
        )
    }
}

/**
 * Renders the artists as one compact row each, the same shape as [AlbumRow]. Shared by the
 * Library tab, the Favorites tab and the search results, so all three stay identical.
 */
@Composable
fun ArtistList(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    onToggleStar: (Artist) -> Unit,
    getCoverArtUrl: (String?) -> String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(items = artists, key = { artist -> artist.id }) { artist ->
            ArtistRow(
                artist = artist,
                coverArtUrl = getCoverArtUrl(artist.coverArt),
                onClick = { onArtistClick(artist) },
                onToggleStar = { onToggleStar(artist) }
            )
        }
    }
}

/**
 * One artist as a row: artwork, name, album count and the star.
 *
 * [onToggleStar] is null where starring does not apply, so the row does not carry a dead button.
 */
@Composable
fun ArtistRow(
    artist: Artist,
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
        // An artist can arrive with no artwork at all, so the placeholder shows through until
        // the image resolves rather than leaving a blank square.
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            AsyncImage(
                model = coverArtUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            artist.albumCount?.let { count ->
                Text(
                    text = if (count == 1) "1 album" else "$count albums",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onToggleStar != null) {
            IconButton(onClick = onToggleStar) {
                Icon(
                    imageVector = if (artist.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (artist.isStarred) "Unfavorite" else "Favorite",
                    tint = if (artist.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
