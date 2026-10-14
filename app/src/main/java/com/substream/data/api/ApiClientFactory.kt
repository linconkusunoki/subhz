package com.substream.data.api

import com.substream.BuildConfig
import com.substream.data.preferences.ServerPreferences
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Factory object to build and configure the Retrofit instance.
 */
object ApiClientFactory {

    /**
     * Retrofit requires a base URL at build time. This placeholder is never dialled:
     * [AuthInterceptor] rewrites every request to the server stored in
     * [serverPreferences], so the service stays a singleton across logins.
     */
    private const val PLACEHOLDER_BASE_URL = "http://substream.invalid/"

    fun createService(serverPreferences: ServerPreferences): SubsonicApiService {
        // Debug builds log the whole request so the API calls are inspectable in Logcat.
        // Release builds log nothing: the Subsonic token rides in the query string, there is no
        // header form of it, and redactHeader cannot hide a query parameter. So the only way to
        // keep a credential out of the log is to not log the URL at all.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(serverPreferences))
            .addInterceptor(logging)
            .build()

        val networkJson = Json { ignoreUnknownKeys = true }

        val retrofit = Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(SubsonicApiService::class.java)
    }
}