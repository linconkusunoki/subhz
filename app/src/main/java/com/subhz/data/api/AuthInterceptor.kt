package com.subhz.data.api

import com.subhz.data.preferences.ServerPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Rewrites every outgoing request to the server the user is signed in to and attaches
 * the Subsonic authentication parameters.
 *
 * This is what makes the Retrofit client dynamic: the service is built once against a
 * placeholder base URL, and the active server (host, port, sub-path, credentials) is
 * resolved per request from [ServerPreferences].
 */
class AuthInterceptor(
    private val serverPreferences: ServerPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val config = serverPreferences.activeServer
            ?: throw IOException("No server configured. Sign in from Settings.")

        val base = config.url.toHttpUrl()
        val original = chain.request().url
        val endpoint = original.encodedPath.trimStart('/')
        val pathPrefix = base.encodedPath.trimEnd('/')

        val auth = SubsonicAuthUtil.generateTokenAndSalt(config.password)
        val url = original.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(if (pathPrefix.isEmpty()) "/$endpoint" else "$pathPrefix/$endpoint")
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", auth.token)
            .addQueryParameter("s", auth.salt)
            .build()

        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}