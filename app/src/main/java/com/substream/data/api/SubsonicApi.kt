package com.substream.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
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
    val playlist: Playlist? = null,
    val artist: ArtistDetail? = null,
    val searchResult3: SearchResult3? = null,
    val artists: ArtistsContainer? = null,
    val playlists: PlaylistsContainer? = null,
    val starred2: StarredContainer? = null,
    val openSubsonicExtensions: List<OpenSubsonicExtension>? = null,
    val playQueue: PlayQueue? = null,
    val lyricsList: LyricsList? = null,
    val lyrics: Lyrics? = null,
    val error: SubsonicError? = null
)

/**
 * One entry of getOpenSubsonicExtensions.view. The server lists every extension it implements, so
 * the client can offer a feature only where it exists rather than failing the call and guessing.
 */
@Serializable
data class OpenSubsonicExtension(
    val name: String,
    val versions: List<Int> = emptyList()
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
    val owner: String? = null,
    val comment: String? = null,
    val duration: Int? = null,
    /** Only getPlaylist.view sends the tracks; getPlaylists.view sends a summary without them. */
    val entry: List<Song> = emptyList(),
    val starred: String? = null,
    val userRating: Int? = null
) {
    val isStarred: Boolean get() = starred != null
}

/**
 * Lyrics by song id, the songLyrics extension. Navidrome answers with the lyrics embedded in the
 * file and any sidecar file next to it, and a plugin can add fetched ones.
 *
 * [StructuredLyrics.offset] shifts every line by a constant in milliseconds, which is how a file
 * records that its timings are late.
 */
@Serializable
data class LyricsList(
    val structuredLyrics: List<StructuredLyrics> = emptyList()
)

@Serializable
data class StructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val offset: Long? = null,
    val synced: Boolean = false,
    val line: List<LyricsLine> = emptyList()
)

@Serializable
data class LyricsLine(
    val start: Long? = null,
    val value: String = ""
)

/** The original getLyrics.view answer: one blob of text for an artist and title. */
@Serializable
data class Lyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null
)

/**
 * The queue the server holds for this user, from getPlayQueue.view.
 *
 * It is the whole listening position: the tracks, which one was current and how far into it the
 * user had got, which is what lets another device carry on where this one stopped.
 */
@Serializable
data class PlayQueue(
    val current: String? = null,
    val position: Long? = null,
    val entry: List<Song> = emptyList()
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
    val starred: String? = null,
    /** 0-5, set with setRating. Navidrome stores it per user; absent means unrated. */
    val userRating: Int? = null
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
    val starred: String? = null,
    /** 0-5, set with setRating. */
    val userRating: Int? = null
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
    val starred: String? = null,
    /** 0-5, set with setRating. */
    val userRating: Int? = null
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
    val starred: String? = null,
    /** 0-5, set with setRating. */
    val userRating: Int? = null
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

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(
        @Query("id") playlistId: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
     * Creates a playlist, optionally from song ids.
     *
     * This doubles as the update call: the spec has updatePlaylist.view for that, but servers
     * disagree about which one they honour, and sending playlistId to this one is what every
     * Subsonic client in the wild does.
     */
    @FormUrlEncoded
    @POST("rest/createPlaylist.view")
    suspend fun createPlaylist(
        @Field("name") name: String,
        @Field("songId") songIds: List<String> = emptyList(),
        @Field("playlistId") playlistId: String? = null,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
     * Changes an existing playlist. Only the removals are used here: the spec's add and rename go
     * through createPlaylist.view, which every server implements, while this one is patchier.
     */
    @FormUrlEncoded
    @POST("rest/updatePlaylist.view")
    suspend fun updatePlaylist(
        @Field("playlistId") playlistId: String,
        @Field("songIdToAdd") songIdToAdd: List<String> = emptyList(),
        @Field("songIndexToRemove") songIndexToRemove: List<Int> = emptyList(),
        @Field("name") name: String? = null,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
     * Rates something 0-5, where 0 clears the rating. This is the one call that writes the number:
     * starred and played are annotations of their own.
     */
    @GET("rest/setRating.view")
    suspend fun setRating(
        @Query("id") id: String,
        @Query("rating") rating: Int,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    @GET("rest/deletePlaylist.view")
    suspend fun deletePlaylist(
        @Query("id") playlistId: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
     * Lyrics for one song by id (the songLyrics extension). `enhanced` asks for the structured
     * form rather than the flat one, which is the whole point of asking this endpoint at all.
     */
    @GET("rest/getLyricsBySongId.view")
    suspend fun getLyricsBySongId(
        @Query("id") songId: String,
        @Query("enhanced") enhanced: Boolean = true,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
     * Lyrics by artist and title, the pre-extension call. Kept as the fallback: servers that carry
     * plain lyrics answer this one, and some do not route the newer endpoint at all.
     */
    @GET("rest/getLyrics.view")
    suspend fun getLyrics(
        @Query("artist") artist: String,
        @Query("title") title: String,
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

    @GET("rest/getPlayQueue.view")
    suspend fun getPlayQueue(
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
     * Stores the queue server-side. A POST because a queue is a list of song ids and a GET would
     * have to spell them all out in the URL.
     */
    @FormUrlEncoded
    @POST("rest/savePlayQueue.view")
    suspend fun savePlayQueue(
        @Field("id") ids: List<String>,
        @Field("current") current: String?,
        @Field("position") position: Long?,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "SubStream",
        @Query("f") format: String = "json"
    ): SubsonicResponseWrapper

    /**
     * Lists the OpenSubsonic extensions this server implements, each with the versions it speaks.
     *
     * The call is public on purpose: the spec requires it to work without credentials, so the app
     * can read a server's capabilities before it has any.
     */
    @GET("rest/getOpenSubsonicExtensions.view")
    suspend fun getOpenSubsonicExtensions(
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
