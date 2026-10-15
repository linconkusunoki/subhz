package com.subhz.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Colour of a filled heart. */
val FavoriteColor = Color(0xFFE91E63)

/**
 * The favourite mark, a heart.
 *
 * It was a star, which by then meant two unrelated things: this, and a rating out of five. The
 * rating kept the stars, so the two stopped looking alike and each is read for what it is.
 *
 * [unfilledTint] exists for the marks drawn over album artwork, where the surface colour would be
 * invisible against a dark photo.
 */
@Composable
fun FavoriteIcon(
    filled: Boolean,
    modifier: Modifier = Modifier,
    unfilledTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = if (filled) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = if (filled) "Unfavorite" else "Favorite",
        tint = if (filled) FavoriteColor else unfilledTint,
        modifier = modifier,
    )
}