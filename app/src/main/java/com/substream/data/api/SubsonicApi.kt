package com.substream.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

// =====================================================================
// 1. DATA CLASSES (The Types)
// =====================================================================

@Serializable
data class SubsonicResponseWrapper(
    @SerialName("subsonic-response") val subsonicResponse: SubsonicResponse
)

@Serializable
data class SubsonicResponse(
    val status: String,
    val version: String,
    val albumList2: AlbumListContainer? = null,
    val album: AlbumDetail? = null,
    val artist: ArtistDetail? = null,
    val searchResult3: SearchResult3? = null,
    val artists: ArtistsContainer? = null,
    val playlists: PlaylistsContainer? = null,
    val starred2: StarredContainer? = null,
    val error: SubsonicError? = null
)

/** Subsonic replies with HTTP 200 and status="failed" when credentials are rejected. */
@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String? = null
)

// Alias for backward compatibility with existing ping references
typealias SubsonicPingData = SubsonicResponse

@Serializable
data class AlbumListContainer(
    val album: List<Album> = emptyList()
)

/** getArtists.view groups artists alphabetically by index name. */
@Serializable
data class ArtistsContainer(
    val ignoredArticles: String = "",
    val index: List<ArtistIndex> = emptyList()
)

@Serializable
data class ArtistIndex(
    val name: String,
    val artist: List<Artist> = emptyList()
)

@Serializable
data class PlaylistsContainer(
    val playlist: List<Playlist> = emptyList()
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val owner: String? = null
)

@Serializable
data class StarredContainer(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList()
)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList()
)

typealias SearchResult = SearchResult3

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int? = null,
    val starred: String? = null
) {
    val isStarred: Boolean get() = starred != null
}

/**
 * getArtist.view returns an artist and its albums. The spec sends no tracklist with it, so the
 * songs tab is filled from a separate search (see SubsonicRepository.getArtistSongs).
 */
@Serializable
data class ArtistDetail(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int? = null,
    val album: List<Album> = emptyList(),
    val starred: String? = null
) {
    val isStarred: Boolean get() = starred != null
}

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val year: Int? = null,
    val starred: String? = null
) {
    val isStarred: Boolean get() = starred != null
}

@Serializable
data class AlbumDetail(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val song: List<Song> = emptyList(),
    val starred: String? = null
) {
    val isStarred: Boolean get() = starred != null
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val album: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val path: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val starred: String? = null
) {
    val isStarred: Boolean get() = starred != null
}

// =====================================================================
// 2. THE INTERFACE (The Endpoints)
// =====================================================================

interface SubsonicApiService {

    @GET("rest/ping.view")
    suspend fun ping(
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/getAlbumList2.view")
    suspend fun getAlbums(
        @Query("type") type: String = "newest",
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(
        @Query("id") albumId: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 20,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/getArtists.view")
    suspend fun getArtists(
        @Query("type") type: String = "artists",
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/getArtist.view")
    suspend fun getArtist(
        @Query("id") artistId: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/getStarred2.view")
    suspend fun getStarred(
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/star.view")
    suspend fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
 * Records a play, or announces one in progress.
 *
 * `time` is when the song started, in epoch millis, not when the play was noticed — the server
 * files the play under that timestamp, so submitting "now" would push every play forward by
 * however long the first half of the track took.
 *
 * `submission = true` writes listening history. `submission = false` is only the transient
 * "now playing" ping, superseded by whatever the listener plays next.
 *
 * Both are the same call on purpose. Subsonic also defines a standalone `nowPlaying.view`, but
 * Navidrome does not route it and answers 404; `submission = false` is what it (and the spec)
 * actually route a now-playing announcement to, so it is the portable choice.
 */
@GET("rest/scrobble.view")
suspend fun scrobble(
    @Query("id") songId: String,
    @Query("time") time: Long,
    @Query("submission") submission: Boolean = true,
    @Query("v") version: String = "1.16.1",
    @Query("c") client: String = "SubStream",
    @Query("f") format: String = "json"
): SubsonicResponseWrapper

    @GET("rest/unstar.view")
    suspend fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper
}
