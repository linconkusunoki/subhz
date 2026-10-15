package com.subhz.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhz.data.api.Song
import com.subhz.data.repository.MAX_RATING
import com.subhz.data.repository.SubsonicRepository
import kotlinx.coroutines.launch

private val RATED_STAR = Color(0xFFFFC107)

/**
 * Five stars for rating what is on screen.
 *
 * Tapping the star you already gave clears the rating, because rating and un-rating are one place
 * rather than a control and a menu. Stars are their own row in a header: unlike a link inside a
 * track row, this is not one tap target sitting inside another, it is a row of five that is
 * nothing else.
 */
@Composable
fun RatingBar(
    rating: Int?,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (1..MAX_RATING).forEach { star ->
            val filled = rating != null && star <= rating
            IconButton(onClick = { onRate(if (filled && star == rating) 0 else star) }) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (star == 1) "Rate" else "Rate $star",
                    tint = if (filled) RATED_STAR else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Writes the rating and hands back the number it settled on.
 *
 * The caller keeps the stars where the user put them straight away rather than waiting for the
 * server: a rating that only appears on the round trip reads as a dropped tap.
 */
class RatingViewModel(
    private val repository: SubsonicRepository
) : ViewModel() {

    fun rate(song: Song, rating: Int) {
        viewModelScope.launch { repository.setRating(id = song.id, rating = rating) }
    }
}
