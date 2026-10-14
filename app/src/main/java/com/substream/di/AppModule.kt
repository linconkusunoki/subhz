@file:OptIn(UnstableApi::class)

package com.substream.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import com.substream.data.api.ApiClientFactory
import com.substream.data.api.ServerCapabilities
import com.substream.data.preferences.PlaybackPreferences
import com.substream.data.preferences.ServerPreferences
import com.substream.data.repository.SubsonicRepository
import com.substream.player.PlayerManager
import com.substream.player.cache.MediaCacheManager
import com.substream.ui.components.AddToPlaylistViewModel
import com.substream.ui.components.LyricsViewModel
import com.substream.ui.components.RatingViewModel
import com.substream.ui.screens.AlbumDetailViewModel
import com.substream.ui.screens.AlbumsViewModel
import com.substream.ui.screens.ArtistDetailViewModel
import com.substream.ui.screens.HomeViewModel
import com.substream.ui.screens.LibraryViewModel
import com.substream.ui.screens.LoginViewModel
import com.substream.ui.screens.PingViewModel
import com.substream.ui.screens.PlaylistDetailViewModel
import com.substream.ui.screens.SearchViewModel
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
            .setUserAgent("SubStream/1.0")

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
        PlayerManager(context = androidContext(), serverPreferences = get())
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
        HomeViewModel(repository = get())
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
