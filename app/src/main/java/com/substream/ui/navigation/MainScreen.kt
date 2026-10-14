package com.substream.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
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
import com.substream.data.repository.SubsonicRepository
import com.substream.player.PlayerManager
import com.substream.ui.components.PlayerBar
import com.substream.ui.screens.AlbumDetailScreen
import com.substream.ui.screens.AlbumsScreen
import com.substream.ui.screens.ArtistDetailScreen
import com.substream.ui.screens.HomeScreen
import com.substream.ui.screens.LibraryScreen
import com.substream.ui.screens.LoginScreen
import com.substream.ui.screens.NowPlayingScreen
import com.substream.ui.screens.PlaylistDetailScreen
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
    repository: SubsonicRepository = koinInject(),
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

    // The full screen player sits over the whole app rather than in the NavHost, so collapsing it
    // drops back onto the mini bar, which never unmounted and so never re-enters with an animation.
    //
    // It is not a ModalBottomSheet because that one animates itself in on tap: the sheet moves at
    // its own speed whether or not your thumb is involved, and it cannot be caught while it does.
    // [PlayerSheet] below moves under a spring you can interrupt, so opening it feels like pulling
    // it up rather than being shown one.
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Where the sheet is, shared with the mini bar so a drag can open it.
    val playerSheet = remember { PlayerSheetState(scope) }

    // Remembered, because PlayerBar keys its gesture detection on it: a new instance each
    // recomposition would cancel and restart the gesture that is opening the sheet.
    val pullToExpand = remember(playerSheet) {
        object : PullToExpand {
            override fun onGrab() {
                // Hold it first: the sheet must not animate itself open on the way in, the
                // finger is about to move it instead.
                playerSheet.hold()
                isPlayerExpanded = true
            }

            override fun onDrag(deltaY: Float) {
                playerSheet.pull(deltaY)
            }

            override fun onRelease() = playerSheet.finishOpen()
        }
    }

    // Picks up the queue the server is holding, so the app opens on the song that was playing
    // rather than on nothing. Restored paused at the saved position: the position is the user's,
    // but starting to play it is not.
    LaunchedEffect(Unit) {
        repository.getPlayQueue().onSuccess { queue ->
            if (queue.isEmpty) return@onSuccess
            playerManager.playSongs(
                songs = queue.songs,
                startIndex = queue.indexOfCurrent,
                startPositionMs = queue.positionMs,
                playWhenReady = false,
            )
        }
    }

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
                    pullToExpand = pullToExpand,
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
                        onPlaylistClick = { playlist ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                        },
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

                // 6. Playlist Detail (with playlistId argument)
                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(
                        navArgument(Screen.PlaylistDetail.ARG_PLAYLIST_ID) { type = NavType.StringType },
                    ),
                ) {
                    val playlistId = it.arguments?.getString(Screen.PlaylistDetail.ARG_PLAYLIST_ID) ?: ""
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        onBackClick = { navController.popBackStack() },
                    )
                }

                // 7. Artist Detail (with artistId argument)
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
        PlayerSheet(
            state = playerSheet,
            onDismiss = { isPlayerExpanded = false },
        ) { collapse ->
            NowPlayingScreen(
                onBackClick = { scope.launch { collapse() } },
                // The artist line links out of the sheet, so the sheet has to be gone before
                // navigating or it stays on top of the artist screen it just opened. Collapsing
                // before navigating is what makes the ordering correct.
                onArtistClick = { artistId, _ ->
                    scope.launch {
                        collapse()
                        navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Draws the player's content inside the sheet, and owns the gestures on it: it can be pulled down,
 * dragged, and flung away.
 *
 * Drags are handled twice over, and both halves are needed. A plain [draggable] catches the ones
 * that land on the sheet itself; a nested scroll connection catches the ones that start inside a
 * scrollable within it (the lyrics, the track list) and are left over once that scrollable has had
 * all it wants. Without the first there is no drag at all, because nested scroll alone is never
 * dispatched when nothing below it is scrollable. Without the second the sheet would follow every
 * drag inside the lyrics, which is the difference between a sheet you can flick away and one that
 * gets in the way of reading something.
 *
 * Opening is not here: the sheet is either already in place (a tap on the bar let it spring up)
 * or a finger is on it and moving it ([PlayerSheetState.finishOpen] takes it from there).
 *
 * [onDismiss] fires once the sheet has finished travelling down, never mid-flight.
 */
@Composable
private fun PlayerSheet(
    state: PlayerSheetState,
    onDismiss: () -> Unit,
    content: @Composable (collapse: suspend () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = state.offset

    // The bar's opening drag can also end in a dismissal, and that happens while the bar is the
    // only thing awake on the sheet, so the sheet hands it its own way out.
    SideEffect { state.onDismissed = onDismiss }

    // Unmount only after the sheet is off screen, so it never disappears mid-flight.
    suspend fun collapse() {
        state.travelTo(1f)
        onDismiss()
    }

    // A drag that opened the sheet is still in progress: the finger moves it, not this.
    LaunchedEffect(Unit) {
        if (!state.isHeld) state.travelTo(0f)
    }

    val dragState = rememberDraggableState { delta -> state.pull(delta) }

    val nestedScroll = remember {
        object : NestedScrollConnection {
            // Upward drags are left alone: they belong to whatever is scrolling inside the sheet.
            // Downward ones move the sheet, but only while it is not already all the way up.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y <= 0f || offset.value <= 0f) return Offset.Zero
                return Offset(0f, state.pull(available.y))
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (consumed.y <= 0f && available.y < PlayerSheetFlingAway) return Velocity.Zero
                if (available.y > PlayerSheetFlingAway || offset.value > PlayerSheetDismissFraction) {
                    collapse()
                } else {
                    state.travelTo(0f)
                }
                return available
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Dims the app underneath, fading with the sheet rather than blinking on at full strength.
        // Tapping it is the same as the chevron, the way a scrim anywhere else behaves.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - offset.value) * 0.6f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { scope.launch { collapse() } },
                ),
        )

        BackHandler { scope.launch { collapse() } }

        // The offset sits on its own Box so the sheet can be measured before it is placed: the
        // travel is a fraction of its own height, which is only known after the first layout.
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (offset.value * state.heightPx).roundToInt()) }
                .onSizeChanged { state.measure(it.height) }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = dragState,
                    onDragStarted = { state.hold() },
                    onDragStopped = { velocity ->
                        if (velocity > PlayerSheetFlingAway || offset.value > PlayerSheetDismissFraction) {
                            scope.launch { collapse() }
                        } else {
                            state.finishDrag(velocity)
                        }
                    },
                )
                .nestedScroll(nestedScroll),
        ) {
            content { collapse() }
        }
    }
}
