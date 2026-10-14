package com.substream.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.substream.data.api.ServerCapabilities
import com.substream.data.preferences.ServerPreferences
import com.substream.player.PlayerManager
import com.substream.ui.components.PlayerBar
import com.substream.ui.screens.AlbumDetailScreen
import com.substream.ui.screens.AlbumsScreen
import com.substream.ui.screens.ArtistDetailScreen
import com.substream.ui.screens.HomeScreen
import com.substream.ui.screens.LibraryScreen
import com.substream.ui.screens.LoginScreen
import com.substream.ui.screens.NowPlayingScreen
import com.substream.ui.screens.SearchScreen
import com.substream.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Root of the app: shows [LoginScreen] until a server is stored, then the main
 * Material 3 Scaffold with Bottom Navigation Bar, fixed Mini Player above the bottom
 * bar, and a NavHost for app navigation.
 *
 * The branch is keyed on `ServerPreferences.config`, so saving credentials from the
 * login form (or logging out from Settings) swaps the whole tree without a navigation
 * event, and the main NavHost is disposed on logout.
 */
@Composable
fun MainScreen(
    serverPreferences: ServerPreferences = koinInject(),
    serverCapabilities: ServerCapabilities = koinInject(),
) {
    val config by serverPreferences.config.collectAsState()

    // Once per signed-in server, and re-read whenever the credentials change: what the app offers
    // depends on what the server implements.
    LaunchedEffect(config?.url) {
        serverCapabilities.refresh()
    }

    if (config == null) {
        LoginScreen()
    } else {
        MainNavScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavScreen(
    navController: NavHostController = rememberNavController(),
    playerManager: PlayerManager = koinInject(),
) {
    val playerState by playerManager.playerState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // List of bottom navigation bar tabs
    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Library,
        BottomNavItem.Search,
        BottomNavItem.Settings,
    )

    // The full screen player is a ModalBottomSheet rather than a NavHost destination so the
    // mini bar expands into it with Material's spring: sheet slides up over a scrim, swipe down
    // (or back, or the chevron) collapses it back onto the still-mounted bar.
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    val playerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Single entry point to switch tabs. Using it for *every* jump to a tab
    // (bottom bar clicks AND in-screen shortcuts) keeps ViewModel state alive
    // via saveState/restoreState instead of pushing duplicate destinations.
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Tapping a tab in the bottom bar always lands that tab on its home screen, even when
    // the tab is already selected or the user left it deep in the stack (an album detail,
    // Settings > change server) or scrolled halfway down a list. Same pop as [navigateToTab]
    // but without restoreState: the tab comes back as a fresh entry, so Library's pager
    // lands on the first page and every list starts at the top.
    val navigateToTabRoot: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
        }
    }

    // Artists are linked from a dozen places (artist grids, album and song subtitles, the search
    // results) but only the screens that carry an artistId can offer the link, so every caller
    // passes the same id + name pair and the name is dropped here. ArtistDetailScreen re-reads
    // the name from the server rather than trusting the caller's copy.
    val navigateToArtist: (String, String) -> Unit = { artistId, _ ->
        navController.navigate(Screen.ArtistDetail.createRoute(artistId))
    }

    Scaffold(
        // This Scaffold owns the window insets for everything below it, so destinations
        // pass contentWindowInsets = WindowInsets(0.dp) and manage only their TopAppBar:
        //  - status bar: handled here (zeroed) + again by each TopAppBar, hence the zero.
        //  - navigation bar: the bottomBar below already sits on it; letting a nested
        //    Scaffold add it too leaves a gap between the tab bar and every list, and
        //    clips the last row. Modifier.padding does not consume insets, which is why
        //    each destination has to opt out explicitly.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                // Mini Player Bar. Stays mounted while the sheet is open (it is fully covered
                // by it) so collapsing the sheet reveals it instantly, with no re-entry animation.
                PlayerBar(
                    playerState = playerState,
                    onPlayPauseClick = { playerManager.togglePlayPause() },
                    onStopClick = { playerManager.stop() },
                    getCoverArtUrl = { coverArt -> playerManager.getCoverArtUrl(coverArt) },
                    onExpand = { isPlayerExpanded = true },
                )

                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { navigateToTabRoot(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                )
                            },
                            label = { Text(text = item.title) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
            ) {
                // 1. Reconfigure the server (Settings > Change server)
                composable(Screen.Login.route) {
                    LoginScreen(onSaved = { navController.popBackStack() })
                }

                // 2. Home
                composable(Screen.Home.route) {
                    HomeScreen(
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                        onArtistClick = navigateToArtist,
                        onSearchClick = {
                            navigateToTab(Screen.Search.route)
                        },
                    )
                }

                // 2. Library (Albums / Artists / Playlists / Favorites)
                composable(Screen.Library.route) {
                    LibraryScreen(
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                        onArtistClick = navigateToArtist,
                    )
                }

                // 3. Search
                composable(Screen.Search.route) {
                    SearchScreen(
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                        onArtistClick = navigateToArtist,
                        onBackClick = { navController.popBackStack() },
                    )
                }

                // 4. Settings
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onChangeServer = { navController.navigate(Screen.Login.route) },
                    )
                }

                // 5. Album Detail (with albumId argument)
                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(
                        navArgument(Screen.AlbumDetail.ARG_ALBUM_ID) { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getString(Screen.AlbumDetail.ARG_ALBUM_ID) ?: ""
                    AlbumDetailScreen(
                        albumId = albumId,
                        onBackClick = { navController.popBackStack() },
                        onArtistClick = navigateToArtist,
                    )
                }

                // 6. Artist Detail (with artistId argument)
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(
                        navArgument(Screen.ArtistDetail.ARG_ARTIST_ID) { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getString(Screen.ArtistDetail.ARG_ARTIST_ID) ?: ""
                    ArtistDetailScreen(
                        artistId = artistId,
                        onBackClick = { navController.popBackStack() },
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                    )
                }
            }
        }
    }

    // Player Expandido (Now Playing), as a sheet over the whole app.
    if (isPlayerExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isPlayerExpanded = false },
            sheetState = playerSheetState,
            // Uncapped width: the player is full screen, including on tablets/foldables.
            sheetMaxWidth = Dp.Unspecified,
            // NowPlayingScreen's own Scaffold owns the insets from here (same reason the main
            // Scaffold zeroes them), so the sheet must not add them a second time.
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            NowPlayingScreen(
                // hide() suspends until the collapse animation is done, so the sheet is only
                // unmounted once it is off screen.
                onBackClick = {
                    scope.launch {
                        playerSheetState.hide()
                        isPlayerExpanded = false
                    }
                },
                // The artist line links out of the sheet, so the sheet has to be gone before
                // navigating or it stays on top of the artist screen it just opened. hide()
                // suspending is what makes the ordering correct.
                onArtistClick = { artistId, _ ->
                    scope.launch {
                        playerSheetState.hide()
                        isPlayerExpanded = false
                        navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
