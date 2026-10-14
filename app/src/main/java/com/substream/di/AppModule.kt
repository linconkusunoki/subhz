@file:OptIn(UnstableApi::class)

package com.substream.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import com.substream.data.api.ApiClientFactory
import com.substream.data.api.ServerCapabilities
import com.substream.data.preferences.ServerPreferences
import com.substream.data.repository.SubsonicRepository
import com.substream.player.PlayerManager
import com.substream.player.cache.MediaCacheManager
import com.substream.ui.screens.AlbumDetailViewModel
import com.substream.ui.screens.AlbumsViewModel
import com.substream.ui.screens.ArtistDetailViewModel
import com.substream.ui.screens.HomeViewModel
import com.substream.ui.screens.LibraryViewModel
import com.substream.ui.screens.LoginViewModel
import com.substream.ui.screens.PingViewModel
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
