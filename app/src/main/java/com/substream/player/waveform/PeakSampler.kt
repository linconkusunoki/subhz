package com.substream.player.waveform

import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.sqrt

/**
 * Turns a stream of decoded PCM into the fixed set of amplitudes a waveform is drawn from.
 *
 * Each window of audio collapses to its RMS (root-mean-square) level — the energy of that slice,
 * not the single loudest sample in it. Energy is what makes a waveform look like a song: a
 * peak-hold saturates a loud, heavily limited master into one continuous block, while RMS lets a
 * quiet verse read as short bars against a loud chorus. The whole track's shape is kept in a few
 * kilobytes instead of holding the entire PCM in memory.
 *
 * Kept free of Android types on purpose so the collapsing and normalising can be unit tested
 * without a device: [feed] takes plain [ShortBuffer]/[FloatBuffer].
 */
internal class PeakSampler(
    private val channelCount: Int,
    private val framesPerWindow: Int,
) {
    private val windows = ArrayList<Float>(INITIAL_WINDOWS)
    private var framesInWindow = 0
    private var samplesInWindow = 0
    private var sumOfSquares = 0.0

    /** Total whole frames seen, which the extractor turns into a duration to size the output by. */
    var frameCount: Long = 0L
        private set

    /** Consumes 16-bit signed PCM, interleaved across [channelCount] channels. */
    fun feed(samples: ShortBuffer) {
        val channels = channelCount.coerceAtLeast(1)
        while (samples.remaining() >= channels) {
            repeat(channels) {
                val value = samples.get() / SHORT_FULL_SCALE
                sumOfSquares += value.toDouble() * value
            }
            advanceWindow(channels)
        }
    }

    /** Consumes float PCM, interleaved across [channelCount] channels, samples in -1f..1f. */
    fun feed(samples: FloatBuffer) {
        val channels = channelCount.coerceAtLeast(1)
        while (samples.remaining() >= channels) {
            repeat(channels) {
                val value = samples.get().coerceIn(-1f, 1f)
                sumOfSquares += value.toDouble() * value
            }
            advanceWindow(channels)
        }
    }

    private fun advanceWindow(samplesPerFrame: Int) {
        frameCount++
        framesInWindow++
        samplesInWindow += samplesPerFrame
        if (framesInWindow >= framesPerWindow) closeWindow()
    }

    private fun closeWindow() {
        val rms = if (samplesInWindow > 0) {
            sqrt(sumOfSquares / samplesInWindow).toFloat()
        } else {
            0f
        }
        windows.add(rms)
        framesInWindow = 0
        samplesInWindow = 0
        sumOfSquares = 0.0
    }

    /**
     * Finishes the track: flushes the trailing partial window, reduces the windows to [peakCount]
     * buckets by keeping the loudest value per bucket (so a transient never disappears just because
     * it was short), and scales the result so the loudest bucket is 1f.
     *
     * An empty array means "nothing was decoded" and is the caller's signal that the source was
     * unusable; silence decodes to a full-length array of zeros, which still draws as a flat line.
     */
    fun finish(peakCount: Int): FloatArray {
        if (framesInWindow > 0) closeWindow()
        if (windows.isEmpty() || peakCount <= 0) return FloatArray(0)

        val raw = windows.toFloatArray()
        val reduced = if (raw.size > peakCount) maxPool(raw, peakCount) else raw
        return normalize(reduced)
    }

    private companion object {
        const val INITIAL_WINDOWS = 1024
        const val SHORT_FULL_SCALE = 32768f
    }
}

/**
 * Reduces [source] to exactly [buckets] values by max-pooling: bucket `i` covers the source range
 * `[i*len/buckets, (i+1)*len/buckets)`. Max rather than average because averaging flattens the
 * drum hits and drops that make a waveform recognisable.
 */
internal fun maxPool(source: FloatArray, buckets: Int): FloatArray {
    if (buckets <= 0) return FloatArray(0)
    if (source.size <= buckets) return source.copyOf()
    val out = FloatArray(buckets)
    for (i in 0 until buckets) {
        val start = (i.toLong() * source.size / buckets).toInt()
        val end = (((i + 1).toLong() * source.size / buckets).toInt()).coerceAtMost(source.size)
        var max = 0f
        for (j in start until end) {
            if (source[j] > max) max = source[j]
        }
        out[i] = max
    }
    return out
}

/** Scales [peaks] so its loudest value is 1f. Silence is returned untouched. */
internal fun normalize(peaks: FloatArray): FloatArray {
    var max = 0f
    for (peak in peaks) {
        if (peak > max) max = peak
    }
    if (max <= 0f) return peaks
    for (i in peaks.indices) {
        peaks[i] = peaks[i] / max
    }
    return peaks
}
