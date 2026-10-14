package com.substream.data.repository

import com.substream.data.api.Album
import com.substream.data.api.AlbumDetail
import com.substream.data.api.Artist
import com.substream.data.api.ArtistDetail
import com.substream.data.api.Playlist
import com.substream.data.api.SearchResult
import com.substream.data.api.SearchResult3
import com.substream.data.api.Song
import com.substream.data.api.StarredContainer
import com.substream.data.api.SubsonicApiService
import com.substream.data.api.SubsonicPingData
import com.substream.data.preferences.ServerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Page size for the album walk in [mainArtistIds]; 500 is the server maximum. */
private const val ALBUM_PAGE = 500

/**
 * The IDs of every artist that is some album's primary artist, found by paging the whole album
 * list. An album carries exactly one artistId even when the server knows several participants,
 * so this is the only signal that separates a headliner from a guest.
 *
 * Takes the page fetcher rather than the API so the paging can be exercised without a server.
 */
internal suspend fun mainArtistIds(fetchPage: suspend (offset: Int) -> List<Album>): Set<String> {
    val ids = mutableSetOf<String>()
    val seenAlbums = mutableSetOf<String>()
    var offset = 0
    while (true) {
        val page = fetchPage(offset)
        // A server that ignores offset replays the same page forever; stop instead of spinning.
        val fresh = page.filter { seenAlbums.add(it.id) }
        if (fresh.isEmpty() && page.isNotEmpty()) break
        fresh.forEach { album -> album.artistId?.let { ids.add(it) } }
        if (page.size < ALBUM_PAGE) break
        offset += ALBUM_PAGE
    }
    return ids
}

/**
 * A listening position as the server stored it: the tracks, which one was current, and how far in.
 */
data class SavedQueue(
    val songs: List<Song> = emptyList(),
    val currentId: String? = null,
    val positionMs: Long = 0L,
) {
    /** Nothing worth restoring: no tracks, or a track the server named that is not in the list. */
    val isEmpty: Boolean get() = songs.isEmpty()

    val indexOfCurrent: Int get() = songs.indexOfFirst { song -> song.id == currentId }.coerceAtLeast(0)
}

/**
 * Repository responsible for handling data operations related to the Subsonic API.
 * This class abstracts the network logic from the UI layer.
 *
 * Credentials are never passed in as arguments: they live in [ServerPreferences] and
 * reach every call through the OkHttp auth interceptor, so no screen can accidentally
 * talk to the wrong server or with stale credentials.
 */
class SubsonicRepository(
    private val api: SubsonicApiService,
    private val serverPreferences: ServerPreferences,
) {

    /**
     * Executes the ping request against the active server to verify connectivity and
     * that the stored credentials are accepted.
     *
     * @return A Result containing the PingData if successful, or an Exception describing
     * why the server refused the credentials.
     */
    suspend fun pingServer(): Result<SubsonicPingData> = call {
        val response = api.ping().subsonicResponse
        if (response.status != "ok") {
            throw IOException(response.error?.message ?: "The server rejected these credentials")
        }
        response
    }

    /**
     * Fetches a list of albums from the Navidrome/Subsonic server.
     *
     * @param type getAlbumList2 sort order: newest, frequent, random, highest, ...
     * @param size how many albums to ask for.
     */
    suspend fun getAlbums(type: String = "newest", size: Int = 20): Result<List<Album>> = call {
        api.getAlbums(type = type, size = size).subsonicResponse.albumList2?.album.orEmpty()
    }

    /**
     * Fetches details of a specific album, including its tracklist.
     *
     * @param albumId The unique ID of the album.
     */
    suspend fun getAlbumDetails(albumId: String): Result<AlbumDetail> = call {
        api.getAlbum(albumId = albumId).subsonicResponse.album
            ?: throw IOException("Album details not found in server response")
    }

    /**
     * Executes global search for artists, albums, and songs matching the given query string.
     *
     * @param query The search term entered by the user.
     */
    suspend fun search(query: String): Result<SearchResult> = call {
        api.search3(query = query).subsonicResponse.searchResult3 ?: SearchResult3()
    }

    // Cover art URLs by coverArtId. See [getCoverArtUrl] for why this exists.
    private val coverArtCache = mutableMapOf<String, String>()
    private var coverArtCacheServerUrl: String? = null

    /**
     * Fetches the artists index from the server, flattened and sorted by name.
     *
     * A failure here is a real failure (both Navidrome and Subsonic implement getArtists.view),
     * so it is reported rather than papered over: the old fallback built artists out of album
     * IDs, which are not artist IDs and would navigate straight into a 404.
     *
     * The index is then narrowed to the artists that actually head an album. getArtists.view
     * hands back every artist the server files under the albumartist role, and a server that
     * splits ALBUMARTIST on "feat."/"ft."/"&" files the guest there too. An album's singular
     * artistId is the only field that still means "main artist", so that is the filter. It is
     * best effort: a server that cannot be walked keeps the full index rather than the screen.
     */
    suspend fun getArtists(): Result<List<Artist>> = call {
        val artists = api.getArtists().subsonicResponse.artists?.index
            ?.flatMap { it.artist }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
        val mainIds = runCatching { mainArtistIds { offset -> albumPage(offset) } }.getOrDefault(emptySet())
        if (mainIds.isEmpty()) artists else artists.filter { it.id in mainIds }
    }

    private suspend fun albumPage(offset: Int): List<Album> =
        api.getAlbums(type = "alphabeticalByArtist", size = ALBUM_PAGE, offset = offset)
            .subsonicResponse.albumList2?.album.orEmpty()

    /**
     * Fetches details of a specific artist, including their albums.
     *
     * @param artistId The unique ID of the artist.
     */
    suspend fun getArtistDetails(artistId: String): Result<ArtistDetail> = call {
        api.getArtist(artistId = artistId).subsonicResponse.artist
            ?: throw IOException("Artist details not found in server response")
    }

    /**
     * Fetches an artist's songs. getArtist.view returns no tracklist, so this searches by artist
     * name and keeps only songs whose artistId matches, which drops the same-named artists the
     * search also returns.
     *
     * A server that indexes songs without an artistId contributes nothing here, which is why an
     * empty list is a success and not an error.
     *
     * @param artistId The unique ID of the artist, used to filter the search results.
     * @param artistName The artist name to search for.
     */
    suspend fun getArtistSongs(artistId: String, artistName: String): Result<List<Song>> = call {
        search(artistName).getOrNull()?.song
            ?.filter { it.artistId == artistId }
            .orEmpty()
    }

    /**
     * Fetches the playlists stored on the server.
     */
    suspend fun getPlaylists(): Result<List<Playlist>> = call {
        api.getPlaylists().subsonicResponse.playlists?.playlist.orEmpty()
    }

    /**
     * Reads one playlist with its tracks (getPlaylist.view). getPlaylists.view sends the same
     * playlists without the tracks, which is why the list screen is not enough.
     */
    suspend fun getPlaylist(playlistId: String): Result<Playlist> = call {
        api.getPlaylist(playlistId = playlistId).subsonicResponse.playlist
            ?: throw IOException("Playlist details not found in server response")
    }

    /**
     * Creates a playlist, optionally seeded with songs.
     */
    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Result<Playlist> = call {
        val response = api.createPlaylist(name = name, songIds = songIds).subsonicResponse
        response.playlist
            ?: throw IOException(response.error?.message ?: "The server did not return the new playlist")
    }

    /**
     * Renames a playlist. The server replaces the whole tracklist when song ids are sent, so the
     * rename deliberately sends none: that keeps the tracks.
     */
    suspend fun renamePlaylist(playlistId: String, name: String): Result<Playlist> = call {
        val response = api.createPlaylist(name = name, playlistId = playlistId).subsonicResponse
        response.playlist
            ?: throw IOException(response.error?.message ?: "The server did not return the playlist")
    }

    /**
     * Appends songs to a playlist. createPlaylist.view with a playlistId and no song ids is the
     * rename; with song ids and no name it is the add, which is how every client does it.
     */
    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> = call {
        if (songIds.isEmpty()) return@call Unit
        val response = api.createPlaylist(
            name = "",
            songIds = songIds,
            playlistId = playlistId,
        ).subsonicResponse
        if (response.status != "ok") {
            throw IOException(response.error?.message ?: "Could not add to the playlist")
        }
    }

    /**
     * Removes one track from a playlist. updatePlaylist.view is the only call that takes a
     * position rather than a song id, and positions are what the call needs.
     */
    suspend fun removeSongFromPlaylist(playlistId: String, index: Int): Result<Unit> = call {
        val response = api.updatePlaylist(
            playlistId = playlistId,
            songIndexToRemove = listOf(index)
        ).subsonicResponse
        if (response.status != "ok") {
            throw IOException(response.error?.message ?: "Could not remove the song")
        }
    }

    /**
     * Deletes a playlist.
     */
    suspend fun deletePlaylist(playlistId: String): Result<Unit> = call {
        val response = api.deletePlaylist(playlistId = playlistId).subsonicResponse
        if (response.status != "ok") {
            throw IOException(response.error?.message ?: "Could not delete the playlist")
        }
    }

    /**
     * Reads the queue the server is holding for this user, for carrying on from another device.
     *
     * An empty result is not an error: it is what a server that has no queue yet, or one that does
     * not implement the endpoint, answers with.
     */
    suspend fun getPlayQueue(): Result<SavedQueue> = call {
        val queue = api.getPlayQueue().subsonicResponse.playQueue
            ?: return@call SavedQueue()
        SavedQueue(
            songs = queue.entry,
            currentId = queue.current,
            positionMs = queue.position?.coerceAtLeast(0L) ?: 0L,
        )
    }

    /**
     * Stores the queue server-side so it can be picked up again here or on another device.
     *
     * Only the current track's position is stored, which is what the server expects: the position
     * is meaningful for one track, not for a list.
     */
    suspend fun savePlayQueue(songs: List<Song>, currentId: String?, positionMs: Long): Result<Unit> = call {
        api.savePlayQueue(ids = songs.map { song -> song.id }, current = currentId, position = positionMs)
    }

    /**
     * Fetches everything starred via getStarred2.view: the albums, the artists and the songs all
     * come in one response, so a screen that wants more than one of them asks once.
     */
    suspend fun getStarred(): Result<StarredContainer> = call {
        api.getStarred().subsonicResponse.starred2 ?: StarredContainer()
    }

    /**
     * Toggles the favorite / starred status of a song, album or artist on the Subsonic server.
     *
     * @param id The unique ID of the item (song, album, or artist).
     * @param isStarred The CURRENT starred state. If true, it will unstar; if false, it will star.
     * @param isAlbum Set to true if starring an album ID instead of a song ID.
     * @param isArtist Set to true if starring an artist ID instead of a song ID.
     * @return A Result containing the new boolean starred status if successful.
     */
    suspend fun toggleStar(
        id: String,
        isStarred: Boolean,
        isAlbum: Boolean = false,
        isArtist: Boolean = false,
    ): Result<Boolean> = call {
        val targetStarred = !isStarred
        val response = if (targetStarred) {
            api.star(
                id = if (isAlbum || isArtist) null else id,
                albumId = if (isAlbum) id else null,
                artistId = if (isArtist) id else null
            )
        } else {
            api.unstar(
                id = if (isAlbum || isArtist) null else id,
                albumId = if (isAlbum) id else null,
                artistId = if (isArtist) id else null
            )
        }
        if (response.subsonicResponse.status != "ok") {
            throw IOException("Error toggling star status")
        }
        targetStarred
    }

    /**
     * Records a play of a song so the server can build listening history and "recently played".
     *
     * Runs on [Dispatchers.IO] like every other call here and reports failures as a
     * [Result] rather than throwing, because a play that never reaches the server is a
     * bookkeeping miss, not something the listener should be shown.
     *
     * @param songId The unique ID of the song that was played.
     * @param playedAtMillis When the song started playing, in epoch millis. Not "now": the
     * server timestamps the play from this value, so it must be the moment playback began.
     */
    suspend fun scrobble(songId: String, playedAtMillis: Long): Result<Unit> = call {
        val response = api.scrobble(
            songId = songId,
            time = playedAtMillis,
            submission = true
        ).subsonicResponse
        if (response.status != "ok") {
            throw IOException(response.error?.message ?: "The server refused the play")
        }
    }

    /**
     * Announces the song the listener is on, so the server can show it as now playing.
     *
     * Goes to `scrobble.view` with `submission = false` rather than to Subsonic's standalone
     * `nowPlaying.view`: Navidrome does not route that endpoint and answers 404, while this is
     * both what it implements and what the spec designates for a non-submission notification.
     *
     * `submission = false` never counts a play — the server only increments play counts on a
     * submission — so this is safe to send as often as the track changes.
     *
     * @param songId The unique ID of the song that started playing.
     * @param playedAtMillis When it started playing, in epoch millis. The same value [scrobble]
     * later carries, because the server keys both entries off it.
     */
    suspend fun nowPlaying(songId: String, playedAtMillis: Long): Result<Unit> = call {
        val response = api.scrobble(
            songId = songId,
            time = playedAtMillis,
            submission = false
        ).subsonicResponse
        if (response.status != "ok") {
            throw IOException(response.error?.message ?: "The server refused the now playing announcement")
        }
    }

    /**
     * Constructs a full authenticated URL to fetch cover art for a given coverArt ID.
     * Returns null when nobody is signed in.
     *
     * URLs are cached per coverArtId and size because every URL embeds a freshly generated random
     * salt, so without this each recomposition hands Coil a different string for the same image and it
     * refetches and flickers. Same reason PlayerManager caches its own copy. The cache is
     * dropped when the active server changes, so a cached URL never outlives its credentials.
     *
     * @param size requested edge length in pixels. Navidrome resizes server-side, so grids and
     * shelves should ask for what they draw instead of pulling full-resolution covers.
     */
    fun getCoverArtUrl(coverArtId: String, size: Int = 0): String? {
        val config = serverPreferences.activeServer ?: return null
        if (coverArtCacheServerUrl != config.url) {
            coverArtCache.clear()
            coverArtCacheServerUrl = config.url
        }
        return coverArtCache.getOrPut("$size:$coverArtId") {
            config.authenticatedUrl(
                "getCoverArt.view",
                "id" to coverArtId,
                "size" to size.toString()
            )
        }
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}