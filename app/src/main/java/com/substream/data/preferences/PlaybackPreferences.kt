package com.substream.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Playback preferences, kept out of [ServerPreferences] because they are about this device rather
 * than about the account: the same server is streamed at different quality on wifi and on mobile.
 *
 * Plain SharedPreferences like the server config's unencrypted half. Nothing here is a secret, and
 * a listener keeps [maxBitRate] in step with the stored value so the player reads it without asking
 * SharedPreferences on every song.
 */
class PlaybackPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _maxBitRate = MutableStateFlow(prefs.getInt(KEY_MAX_BIT_RATE, DEFAULT_MAX_BIT_RATE))
    val maxBitRate: StateFlow<Int> = _maxBitRate.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_MAX_BIT_RATE) {
                _maxBitRate.value = prefs.getInt(KEY_MAX_BIT_RATE, DEFAULT_MAX_BIT_RATE)
            }
        }
    }

    /**
     * Asks the server to transcode above this ceiling, in kbps. [DEFAULT_MAX_BIT_RATE] means "no
     * ceiling", which is the original file untouched.
     */
    fun setMaxBitRate(kbps: Int) {
        prefs.edit().putInt(KEY_MAX_BIT_RATE, kbps).apply()
    }

    private companion object {
        const val PREFS_NAME = "substream_playback"
        const val KEY_MAX_BIT_RATE = "max_bit_rate"
        const val DEFAULT_MAX_BIT_RATE = 0
    }
}