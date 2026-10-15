@file:OptIn(UnstableApi::class)

package com.subhz.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import com.subhz.data.api.ApiClientFactory
import com.subhz.data.api.ServerCapabilities
import com.subhz.data.preferences.PlaybackPreferences
import com.subhz.data.preferences.ServerPreferences
import com.subhz.data.repository.SubsonicRepository
import com.subhz.player.PlayerManager
import com.subhz.player.cache.MediaCacheManager
import com.subhz.player.waveform.WaveformPrefetcher
import com.subhz.player.waveform.WaveformRepository
import com.subhz.ui.components.AddToPlaylistViewModel
import com.subhz.ui.components.LyricsViewModel
import com.subhz.ui.components.RatingViewModel
import com.subhz.ui.components.WaveformViewModel
import com.subhz.ui.screens.AlbumDetailViewModel
import com.subhz.ui.screens.AlbumsViewModel
import com.subhz.ui.screens.ArtistDetailViewModel
import com.subhz.ui.screens.HomeViewModel
import com.subhz.ui.screens.LibraryViewModel
import com.subhz.ui.screens.LoginViewModel
import com.subhz.ui.screens.PingViewModel
import com.subhz.ui.screens.PlaylistDetailViewModel
import com.subhz.ui.screens.SearchViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module declaring all our app's dependencies.
 */
val appModule = module {

    // 1. single {} creates a Singleton (only one instance for the whole app)
    // Signed-in server credentials: encrypted at rest, shared by every layer.
    single { ServerPreferences(androidContext()) }

    // The Retrofit service resolves host + auth from ServerPreferences per request
    single {
        ApiClientFactory.createService(get())
    }

    // 2. The get() function magically finds the dependency needed
    single {
        SubsonicRepository(api = get(), serverPreferences = get())
    }

    // Device playback settings, e.g. the streaming quality ceiling.
    single { PlaybackPreferences(androidContext()) }

    // Which OpenSubsonic extensions the signed-in server implements.
    single {
        ServerCapabilities(api = get(), serverPreferences = get())
    }

    // 3. Media Cache Manager & DataSource Factory (Singleton instance)
    single {
        MediaCacheManager(context = androidContext(), cacheSizeMb = 500L)
    }

    single<CacheDataSource.Factory> {
        val mediaCacheManager = get<MediaCacheManager>()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("SubHz/1.0")

        CacheDataSource.Factory()
            .setCache(mediaCacheManager.simpleCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            // The stream URL carries a fresh random salt every time it is built, so the URL is no
            // use as a cache key: the same song would be re-downloaded on every session. Keying on
            // the song id alone fixes that, but with two quality settings it would serve the bytes
            // of whichever was cached first, so the bitrate is part of the key too.
            .setCacheKeyFactory(
                object : CacheKeyFactory {
                    override fun buildCacheKey(dataSpec: DataSpec): String {
                        val uri = dataSpec.uri
                        return uri.getQueryParameter("maxBitRate").orEmpty() +
                            ":" + uri.getQueryParameter("id").orEmpty()
                    }
                }
            )
    }

    // PlayerManager initialized with Android Context
    single {
        // playbackPreferences must come from the graph: the constructor default would build a
        // second instance, and Settings would then write to one while the player read the other,
        // so the quality setting would do nothing at all.
        PlayerManager(
            context = androidContext(),
            serverPreferences = get(),
            playbackPreferences = get(),
        )
    }

    // Decodes and caches per-track waveforms. Reads through the same media cache the player uses,
    // so an analysed track's bytes are not downloaded twice.
    single {
        WaveformRepository(
            context = androidContext(),
            cacheDataSourceFactory = get(),
            serverPreferences = get(),
        )
    }

    // Builds the current track's waveform (and the next few) as playback runs, so the UI never
    // has to decode in front of the user.
    single {
        WaveformPrefetcher(playerManager = get(), repository = get())
    }

    // 4. viewModel {} tells Koin how to build our ViewModels
    viewModel {
        LoginViewModel(repository = get(), serverPreferences = get())
    }

    viewModel {
        PingViewModel(repository = get())
    }

    viewModel {
        AlbumsViewModel(repository = get())
    }

    viewModel {
        HomeViewModel(repository = get(), playerManager = get())
    }

    viewModel {
        PlaylistDetailViewModel(repository = get())
    }

    viewModel {
        AddToPlaylistViewModel(repository = get())
    }

    viewModel {
        LyricsViewModel(repository = get())
    }

    viewModel {
        WaveformViewModel(playerManager = get(), repository = get())
    }

    viewModel {
        RatingViewModel(repository = get())
    }

    viewModel {
        AlbumDetailViewModel(repository = get(), savedStateHandle = getOrNull())
    }

    viewModel {
        ArtistDetailViewModel(repository = get(), savedStateHandle = getOrNull())
    }

    viewModel {
        SearchViewModel(repository = get())
    }

    viewModel {
        LibraryViewModel(repository = get())
    }
}
