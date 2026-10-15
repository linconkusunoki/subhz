@file:OptIn(UnstableApi::class)

package com.subhz.player.waveform

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Decodes a local audio file to PCM and keeps only what a waveform needs.
 *
 * This is a full decode of the track, not a sample of it, which is what makes the result line up
 * with playback: the peaks are indexed by frame count from the first sample, so time maps to
 * position exactly. It runs on [Dispatchers.Default] and is cancellable between buffers, so
 * skipping a track abandons the analysis instead of finishing it.
 *
 * Reads the source through a [ParcelFileDescriptor] rather than a path: the temp file it is handed
 * may be a transcoded stream whose extension says nothing about the real container, and the
 * extractor sniffs the bytes instead of trusting the name.
 */
internal object WaveformExtractor {

    /** How many windows describe one second of audio. 50 = 20 ms, fine enough to keep transients. */
    private const val WINDOWS_PER_SECOND = 50

    private const val CODEC_TIMEOUT_US = 10_000L

    /** Seconds of audio each drawn bar covers. Larger = chunkier, calmer waveform. */
    private const val SECONDS_PER_BAR = 3.0

    /** Floor for very short clips so they still draw as more than a couple of bars. */
    private const val MIN_BARS = 24

    /** Ceiling for very long tracks, keeping the array and the drawing bounded. */
    private const val MAX_BARS = 600

    /**
     * Decodes [file] and returns the track's waveform, or an empty array when the file holds no
     * decodable audio. Never throws for a bad file: an analysis that fails is a missing waveform,
     * not something worth crashing the player over.
     *
     * The number of bars is derived from the decoded length rather than fixed, so the waveform
     * reads at a consistent density on any track: one bar per [SECONDS_PER_BAR] seconds. A fixed
     * count would look sparse on a long track and noisy on a short one.
     */
    suspend fun extract(file: File): FloatArray = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var descriptor: ParcelFileDescriptor? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            extractor.setDataSource(descriptor.fileDescriptor)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext FloatArray(0)

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext FloatArray(0)
            val sampleRate = format.optInt(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            val channelCount = format.optInt(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            val pcmEncoding = format.optInt(
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            val sampler = PeakSampler(
                channelCount = channelCount,
                framesPerWindow = (sampleRate / WINDOWS_PER_SECOND).coerceAtLeast(1),
            )

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            drainCodec(codec, extractor, sampler, pcmEncoding)

            val seconds = if (sampleRate > 0) sampler.frameCount.toDouble() / sampleRate else 0.0
            val bars = (seconds / SECONDS_PER_BAR)
                .roundToInt()
                .coerceIn(MIN_BARS, MAX_BARS)
            sampler.finish(bars)
        } catch (e: CancellationException) {
            // A skipped track cancels the decode; that must propagate, not read as "no audio".
            throw e
        } catch (_: Exception) {
            FloatArray(0)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { descriptor?.close() }
        }
    }

    private suspend fun drainCodec(
        codec: MediaCodec,
        extractor: MediaExtractor,
        sampler: PeakSampler,
        pcmEncoding: Int,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            // Cancellation lands between buffers: a decode of a long track must be abandonable.
            currentCoroutineContext().ensureActive()

            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    val size = if (inputBuffer == null) -1 else extractor.readSampleData(inputBuffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        feed(sampler, outputBuffer, pcmEncoding)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }
    }

    private fun feed(sampler: PeakSampler, buffer: java.nio.ByteBuffer, pcmEncoding: Int) {
        val ordered = buffer.order(ByteOrder.nativeOrder())
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            sampler.feed(ordered.asFloatBuffer())
        } else {
            sampler.feed(ordered.asShortBuffer())
        }
    }
}

/** [MediaFormat.getString] style read that falls back instead of throwing when the key is absent. */
private fun MediaFormat.optInt(key: String, fallback: Int): Int =
    if (containsKey(key)) getInteger(key) else fallback
