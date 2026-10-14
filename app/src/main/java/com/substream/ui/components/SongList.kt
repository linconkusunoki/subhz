package com.substream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.substream.data.api.Song

/**
 * A list of songs as compact rows, the same shape the album and artist lists use.
 *
 * Shared by every screen that lists tracks rather than albums, so a track looks the same wherever
 * it is shown. [onToggleStar] is null where the caller has no starring to offer.
 */
@Composable
fun SongsList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    getCoverArtUrl: (String?) -> String?,
    modifier: Modifier = Modifier,
    onToggleStar: ((Song) -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = songs,
            key = { song -> song.id }
        ) { song ->
            SongRow(
                song = song,
                coverArtUrl = getCoverArtUrl(song.coverArt),
                onClick = { onSongClick(song) },
                onToggleStar = onToggleStar?.let { toggle -> { toggle(song) } },
                onRemove = onRemove?.let { remove -> { remove(song) } },
                onLongClick = onAddToPlaylist?.let { add -> { add(song) } },
            )
        }
    }
}

/**
 * One song as a row: artwork, title, artist, length and one trailing action.
 *
 * The artist is plain text rather than a link, for the reason the album rows are: the row is one
 * tap target and a nested one only misleads the finger.
 *
 * [onToggleStar] and [onRemove] occupy the same slot; a caller asks for one. A track inside a
 * playlist offers removal rather than a star, because what the user wants there is not "favourite
 * this" but "get this out of here", and two buttons at the end of the row is one too many.
 */
@Composable
fun SongRow(
    song: Song,
    coverArtUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleStar: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                // Long press is where "add to playlist" lives: another button on a row that
                // already carries three tap targets is one more thing to miss.
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) "Add to playlist" else null,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formatSongDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        when {
            onToggleStar != null -> IconButton(onClick = onToggleStar) {
                FavoriteIcon(filled = song.isStarred)
            }

            onRemove != null -> IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Formats a song duration in seconds to "mm:ss", or "--:--" when the server did not report one.
 *
 * Lives here rather than in a screen because songs are listed in more than one of them, and each
 * copy would be one more place for the format to drift.
 */
fun formatSongDuration(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return "--:--"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
}