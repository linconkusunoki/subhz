package com.subhz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.data.api.Song
import com.subhz.data.repository.SongLyrics
import com.subhz.data.repository.SongLyricLine
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The lyrics of the playing track, in place of the artwork.
 *
 * Timed lyrics follow the player: the current line is highlighted and scrolled to, and the scroll
 * follows on its own unless the user has moved it themselves. Untimed lyrics are shown as the text
 * the server had.
 */
@Composable
fun LyricsPanel(
    song: Song,
    positionMs: Long,
    modifier: Modifier = Modifier,
    viewModel: LyricsViewModel = koinViewModel(),
) {
    val lyrics by viewModel.lyrics.collectAsState()

    // Re-read per track: the panel is drawn for whatever is playing now, not for whatever was
    // playing when the panel was first composed.
    LaunchedEffect(song.id) {
        viewModel.load(song)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = lyrics) {
            null, is LyricsState.Loading -> CircularProgressIndicator()

            is LyricsState.Unavailable -> Text(
                text = "No lyrics for this track.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is LyricsState.Loaded -> when (val lines = current.lyrics) {
                is SongLyrics.None -> Text(
                    text = "No lyrics for this track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is SongLyrics.Plain -> Text(
                    text = lines.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )

                is SongLyrics.Synced -> SyncedLyrics(lines = lines.lines, positionMs = positionMs)
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<SongLyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // The line being sung is the last one that has already started: a gap between lines still shows
    // the earlier one, which is what a karaoke display does.
    val activeIndex = lines.indexOfLast { line -> line.timeMs <= positionMs }

    // Follow the song, unless the user scrolled away to read ahead.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) following = true
    }
    LaunchedEffect(activeIndex, following) {
        if (following && activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex.coerceAtMost(lines.lastIndex))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            count = lines.size,
            key = { index -> "${lines[index].timeMs}:$index" },
        ) { index ->
            val line = lines[index]
            val isActive = index == activeIndex
            Text(
                text = line.text.ifBlank { "♪" },
                style = if (isActive) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Lyrics state for one track. Loading is its own case so an empty answer is not a blank screen. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data class Loaded(val lyrics: SongLyrics) : LyricsState
    data object Unavailable : LyricsState
}

/**
 * Fetches lyrics per track. Failures land as "no lyrics" rather than an error: a server without the
 * endpoints is normal, and the artwork it replaced is still one tap away.
 */
class LyricsViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    private val _lyrics = MutableStateFlow<LyricsState?>(null)
    val lyrics: StateFlow<LyricsState?> = _lyrics.asStateFlow()

    fun load(song: Song) {
        _lyrics.value = LyricsState.Loading
        viewModelScope.launch {
            val state = repository.getSongLyrics(song).fold(
                onSuccess = { lyrics -> LyricsState.Loaded(lyrics) },
                onFailure = { LyricsState.Unavailable },
            )
            _lyrics.value = state
        }
    }
}