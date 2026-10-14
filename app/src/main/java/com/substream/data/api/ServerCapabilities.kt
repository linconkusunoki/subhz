package com.substream.data.api

import com.substream.data.preferences.ServerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What the active server says it can do, from getOpenSubsonicExtensions.view.
 *
 * Extensions are optional in Subsonic and mandatory in OpenSubsonic, and which ones exist varies by
 * server: Navidrome answers with a list, an older Subsonic has no such endpoint at all. Rather than
 * call an extension's endpoint and find out by failing, the app reads the list once and asks this
 * before offering the feature.
 *
 * Keyed on the server URL like the cover art cache, so capabilities read from one server can never be
 * shown against another.
 */
class ServerCapabilities(
    private val api: SubsonicApiService,
    private val serverPreferences: ServerPreferences,
) {
    private val _extensions = MutableStateFlow<Set<String>>(emptySet())
    private var loadedFor: String? = null

    /** Extension names the server reported. Empty until [refresh] succeeds, and on servers without the endpoint. */
    val extensions: StateFlow<Set<String>> = _extensions.asStateFlow()

    /**
     * Reads the extension list, once per server. Never throws: a server that does not implement the
     * endpoint is a normal answer meaning "no extensions", not a failure worth showing the user.
     */
    suspend fun refresh() {
        val serverUrl = serverPreferences.activeServer?.url ?: return
        if (loadedFor == serverUrl) return
        loadedFor = serverUrl

        _extensions.value = withContext(Dispatchers.IO) {
            runCatching {
                api.getOpenSubsonicExtensions().subsonicResponse.openSubsonicExtensions
                    .orEmpty()
                    .map { extension -> extension.name }
                    .toSet()
            }.getOrDefault(emptySet())
        }
    }
}