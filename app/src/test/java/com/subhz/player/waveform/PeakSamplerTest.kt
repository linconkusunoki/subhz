package com.subhz.player.waveform

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ShortBuffer

/**
 * Guards the audio-to-waveform collapse: the shape a track is drawn from must keep transients,
 * stay the same length regardless of the track, and read as a flat line when the track is silent.
 *
 * Clock and codec are out of the picture on purpose: the sampler only sees buffers of shorts here,
 * which is the contract the decoder feeds it on a device.
 */
class PeakSamplerTest {

    @Test
    fun `a full scale signal normalises to one everywhere`() {
        val sampler = PeakSampler(channelCount = 1, framesPerWindow = 2)
        sampler.feed(ShortBuffer.wrap(ShortArray(8) { Short.MAX_VALUE }))

        val peaks = sampler.finish(peakCount = 10)

        assertEquals(4, peaks.size)
        assertTrue(peaks.all { it == 1f })
    }

    @Test
    fun `silence is a flat line, not an empty result`() {
        val sampler = PeakSampler(channelCount = 1, framesPerWindow = 2)
        sampler.feed(ShortBuffer.wrap(ShortArray(8)))

        val peaks = sampler.finish(peakCount = 10)

        assertEquals(4, peaks.size)
        assertTrue(peaks.all { it == 0f })
    }

    @Test
    fun `nothing decoded yields an empty array so the caller can fall back`() {
        val sampler = PeakSampler(channelCount = 1, framesPerWindow = 2)

        assertEquals(0, sampler.finish(peakCount = 10).size)
    }

    @Test
    fun `downsampling keeps a lone transient and puts it in the right bucket`() {
        val samples = ShortArray(100)
        samples[50] = Short.MAX_VALUE

        val sampler = PeakSampler(channelCount = 1, framesPerWindow = 1)
        sampler.feed(ShortBuffer.wrap(samples))

        val peaks = sampler.finish(peakCount = 10)

        assertEquals(10, peaks.size)
        // Frame 50 of 100 falls in bucket 5; max-pooling must not average it away.
        assertEquals(1f, peaks[5], 1e-6f)
        assertTrue(peaks.filterIndexed { index, _ -> index != 5 }.all { it == 0f })
    }

    @Test
    fun `the loudest channel of a frame wins`() {
        val sampler = PeakSampler(channelCount = 2, framesPerWindow = 1)
        // Two frames: (silent left, loud right), then (silent left, silent right).
        sampler.feed(ShortBuffer.wrap(shortArrayOf(0, Short.MAX_VALUE, 0, 0)))

        val peaks = sampler.finish(peakCount = 8)

        assertArrayEquals(floatArrayOf(1f, 0f), peaks, 1e-6f)
    }

    @Test
    fun `a partial trailing window is not dropped`() {
        // Three frames, windows of two: the last frame must still produce a window.
        val sampler = PeakSampler(channelCount = 1, framesPerWindow = 2)
        sampler.feed(ShortBuffer.wrap(shortArrayOf(0, 0, Short.MAX_VALUE)))

        val peaks = sampler.finish(peakCount = 8)

        assertEquals(2, peaks.size)
        assertEquals(0f, peaks[0], 1e-6f)
        assertEquals(1f, peaks[1], 1e-6f)
    }

    @Test
    fun `frame count is whole frames only`() {
        // Four stereo frames plus one dangling sample that is not a complete frame.
        val sampler = PeakSampler(channelCount = 2, framesPerWindow = 2)
        sampler.feed(ShortBuffer.wrap(shortArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)))

        // Drives the output length, so a dangling sample must not count as a frame.
        assertEquals(4L, sampler.frameCount)
    }
}
