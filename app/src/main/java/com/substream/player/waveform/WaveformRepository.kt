@file:OptIn(UnstableApi::class)

package com.substream.player.waveform

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import com.substream.data.preferences.ServerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Produces and remembers the waveform of a track.
 *
 * There is no waveform in the Subsonic API and none in Navidrome, so the shape has to be recovered
 * on the device by decoding the audio. That is expensive, so a result is written to disk (keyed by
 * server and song) and kept in a small in-memory cache while the app is open. A track is analysed
 * at most once per server until the file cache is evicted.
 *
 * The bytes are read through the same [CacheDataSource] the player uses, which means the analysis
 * is served from the local media cache when the track has already been played, and otherwise warms
 * that cache for playback — the download is not wasted on a second copy.
 */
class WaveformRepository(
    context: Context,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val serverPreferences: ServerPreferences,
) {

    private val cacheDir = File(context.cacheDir, "waveforms").apply { mkdirs() }

    private val memoryCache = object : LruCache<String, FloatArray>(MEMORY_TRACKS) {}

    // One decode at a time: it is CPU heavy and the temp file name is shared per track.
    private val analysisLock = Mutex()

    /** The peaks if they are already in memory; never touches the disk, safe to call on the UI thread. */
    fun memoryCached(songId: String): FloatArray? {
        val key = cacheKey(songId) ?: return null
        return memoryCache.get(key)
    }

    /**
     * Whether this track already has a waveform, in memory or on disk. Cheap enough for the
     * prefetcher to ask before queueing work: it only stats a file, it never reads or decodes.
     */
    fun isCached(songId: String): Boolean {
        val key = cacheKey(songId) ?: return false
        return memoryCache.get(key) != null || File(cacheDir, "$key.peaks").isFile
    }

    /**
     * Returns the waveform for a track, analysing it if necessary.
     *
     * [streamUrl] is the authenticated stream URL the player would use, so the decoded audio is the
     * exact audio that plays. Returns an empty array when there is nothing to draw; callers fall
     * back rather than showing an error.
     */
    suspend fun getPeaks(songId: String, streamUrl: String): FloatArray {
        val key = cacheKey(songId) ?: return FloatArray(0)
        memoryCache.get(key)?.let { return it }

        return analysisLock.withLock {
            memoryCache.get(key)?.let { return@withLock it }

            readFromDisk(key)?.let { peaks ->
                memoryCache.put(key, peaks)
                return@withLock peaks
            }

            val source = File(cacheDir, "$key.audio")
            try {
                download(streamUrl, source)
                val peaks = WaveformExtractor.extract(source)
                if (peaks.isNotEmpty()) {
                    memoryCache.put(key, peaks)
                    runCatching { writeToDisk(key, peaks) }
                }
                peaks
            } finally {
                source.delete()
            }
        }
    }

    private suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        val dataSource = cacheDataSourceFactory.createDataSource()
        try {
            dataSource.open(DataSpec(Uri.parse(url)))
            BufferedOutputStream(FileOutputStream(target)).use { out ->
                val buffer = ByteArray(DOWNLOAD_BUFFER)
                while (true) {
                    val read = dataSource.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    out.write(buffer, 0, read)
                    ensureActive()
                }
            }
        } finally {
            runCatching { dataSource.close() }
        }
    }

    private fun readFromDisk(key: String): FloatArray? {
        val file = File(cacheDir, "$key.peaks")
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readInt() != FORMAT_MAGIC) return null
                val count = input.readInt()
                if (count <= 0 || count > MAX_PEAKS) return null
                FloatArray(count) { input.readFloat() }
            }
        }.getOrNull()
    }

    private fun writeToDisk(key: String, peaks: FloatArray) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(File(cacheDir, "$key.peaks")))).use { out ->
            out.writeInt(FORMAT_MAGIC)
            out.writeInt(peaks.size)
            for (peak in peaks) out.writeFloat(peak)
        }
    }

    /**
     * Cache identity for a track: the server it came from plus the song id.
     *
     * The same id can exist on two servers, and playback quality does not change a waveform's
     * shape, so neither the host's token nor the bitrate belongs in here. Null while logged out.
     */
    private fun cacheKey(songId: String): String? {
        val server = serverPreferences.activeServer ?: return null
        return sha1("${server.url}|$songId")
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val MAX_PEAKS = 4096
        const val MEMORY_TRACKS = 8
        const val DOWNLOAD_BUFFER = 128 * 1024

        // Bumped when the bar count changed: the magic doubles as a format version, so peaks
        // written by an older build are ignored and rebuilt instead of drawn at the wrong density.
        const val FORMAT_MAGIC = 0x53574634 // "SWF4"
    }
}
