package com.substream.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Defines all navigation destinations/routes in the SubStream app.
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object AlbumDetail : Screen("album_detail/{albumId}") {
        const val ARG_ALBUM_ID = "albumId"
        fun createRoute(albumId: String): String = "album_detail/$albumId"
    }
    data object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        const val ARG_PLAYLIST_ID = "playlistId"
        fun createRoute(playlistId: String): String = "playlist_detail/$playlistId"
    }

    data object ArtistDetail : Screen("artist_detail/{artistId}") {
        const val ARG_ARTIST_ID = "artistId"
        fun createRoute(artistId: String): String = "artist_detail/$artistId"
    }
}

/**
 * Represents items displayed in the Bottom Navigation Bar.
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Home : BottomNavItem(
        route = Screen.Home.route,
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    )

    data object Library : BottomNavItem(
        route = Screen.Library.route,
        title = "Library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
    )

    data object Search : BottomNavItem(
        route = Screen.Search.route,
        title = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
    )

    data object Settings : BottomNavItem(
        route = Screen.Settings.route,
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    )
}