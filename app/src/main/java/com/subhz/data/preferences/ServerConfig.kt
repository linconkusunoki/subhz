package com.subhz.data.preferences

import com.subhz.data.api.SubsonicAuthUtil
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The credentials of the server the user is signed in to.
 *
 * This is the single source of truth for "where do we talk to" and "as whom":
 * the OkHttp interceptor, the repository and the player all read it instead of
 * compile-time constants.
 */
data class ServerConfig(
    val url: String,
    val username: String,
    val password: String,
) {
    /**
     * Builds a fully authenticated Subsonic URL, e.g.
     * `https://music.example.com/navidrome/rest/stream.view?id=42&u=alice&t=...`
     *
     * Used for the endpoints Media3/Coil fetch directly (streaming, cover art);
     * Retrofit calls get the same parameters injected by [AuthInterceptor].
     */
    fun authenticatedUrl(endpoint: String, vararg query: Pair<String, String>): String {
        val auth = SubsonicAuthUtil.generateTokenAndSalt(password)
        return buildString {
            append(url).append("/rest/").append(endpoint)
            append("?u=").append(username)
            append("&t=").append(auth.token)
            append("&s=").append(auth.salt)
            append("&v=").append(API_VERSION)
            append("&c=").append(CLIENT)
            query.forEach { (key, value) -> append('&').append(key).append('=').append(value) }
        }
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT = "SubHz"

        /**
         * Builds a config from raw user input, normalising the URL: a missing scheme
         * defaults to https, trailing slashes are dropped. Returns null when the input
         * is not a usable server address.
         */
        fun parse(url: String, username: String, password: String): ServerConfig? {
            val trimmed = url.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            return runCatching { withScheme.toHttpUrl() }
                .map { ServerConfig(it.toString().trimEnd('/'), username.trim(), password) }
                .getOrNull()
        }
    }
}