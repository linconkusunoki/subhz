package com.substream.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substream.data.api.Song
import com.substream.player.PlayerManager
import com.substream.player.waveform.WaveformRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** The waveform of one track. Loading is distinct from unavailable so a wait is not a blank control. */
sealed interface WaveformState {
    data object Loading : WaveformState
    data class Loaded(val peaks: FloatArray) : WaveformState
    data object Unavailable : WaveformState
}

/**
 * A seek bar drawn as the track's waveform.
 *
 * The waveform doubles as the scrubber: pressing or dragging anywhere seeks there, and the bars
 * before the playhead are tinted. [progress] is ignored while the finger is down so the drawn
 * playhead follows the finger rather than fighting the player's position updates.
 *
 * While [peaks] is empty the track is still being analysed, so a line undulates in place of the
 * waveform — the control is never a dead element, and it stays draggable throughout.
 *
 * [onScrub] fires continuously during the gesture for a live time readout; [onSeek] fires once, on
 * release, with the fraction of the track to seek to.
 */
@Composable
fun WaveformSeekBar(
    peaks: FloatArray,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: ((Float) -> Unit)? = null,
    height: Dp = 56.dp,
) {
    var isDragging by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)

    val displayFraction = if (isDragging) scrubFraction else progress.coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    // Drives the placeholder. Only read while peaks are missing, so it costs nothing once loaded.
    val transition = rememberInfiniteTransition(label = "waveform-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    scrubFraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    currentOnScrub?.invoke(scrubFraction)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        currentOnScrub?.invoke(scrubFraction)
                        change.consume()
                    }

                    isDragging = false
                    currentOnSeek(scrubFraction)
                }
            }
    ) {
        if (peaks.isEmpty()) {
            drawWavingLine(phase, displayFraction, activeColor, inactiveColor)
            return@Canvas
        }

        val pitch = size.width / peaks.size
        val barWidth = (pitch * BAR_FILL).coerceAtLeast(1f)
        val corner = CornerRadius(barWidth / 2f)
        val centerY = size.height / 2f
        // The loudest bar stops short of the edges, leaving headroom so the shape of the track —
        // not the height of the box — is what stands out.
        val maxHalf = size.height / 2f * MAX_BAR_FILL
        val playedX = displayFraction * size.width

        val halves = FloatArray(peaks.size)
        val lefts = FloatArray(peaks.size)
        for (index in peaks.indices) {
            val peak = peaks[index].coerceIn(0f, 1f)
            halves[index] = (MIN_HALF + peak * (maxHalf - MIN_HALF)).coerceAtMost(maxHalf)
            lefts[index] = index * pitch + (pitch - barWidth) / 2f
        }

        fun drawBars(color: Color) {
            for (index in peaks.indices) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(lefts[index], centerY - halves[index]),
                    size = Size(barWidth, halves[index] * 2f),
                    cornerRadius = corner,
                )
            }
        }

        // Bars stay coarse, but the played tint is clipped at the exact playback position rather
        // than switched per bar, so the sweep glides across a bar instead of jumping by seconds.
        drawBars(inactiveColor)
        clipRect(left = 0f, top = 0f, right = playedX, bottom = size.height) {
            drawBars(activeColor)
        }
    }
}

/**
 * The analysing placeholder: a single line undulating across the control.
 *
 * Two travelling waves of different speeds are summed so the motion never looks like a loop, and
 * the amplitude tapers to nothing at both ends so the line sits flat where it starts and finishes.
 * The played half is drawn in the active colour so the placeholder still shows position.
 */
private fun DrawScope.drawWavingLine(
    phase: Float,
    playedFraction: Float,
    activeColor: Color,
    inactiveColor: Color,
) {
    val segments = 96
    val centerY = size.height / 2f
    val maxAmplitude = size.height * WAVE_AMPLITUDE
    val path = Path()

    for (index in 0..segments) {
        val t = index / segments.toFloat()
        val x = t * size.width
        val wave = sinF(t * PI_F * 1.5f + phase) * 0.6f + sinF(t * PI_F * 3f - phase * 1.7f) * 0.4f
        // Ends fixed at the centre line; the swell is in the middle.
        val taper = sinF(t * PI_F)
        val y = centerY + wave * maxAmplitude * taper
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    val stroke = Stroke(width = WAVE_STROKE.toPx(), cap = StrokeCap.Round)
    drawPath(path, inactiveColor, style = stroke)
    clipRect(left = 0f, top = 0f, right = playedFraction * size.width, bottom = size.height) {
        drawPath(path, activeColor, style = stroke)
    }
}

private fun sinF(x: Float): Float = sin(x.toDouble()).toFloat()

private val PI_F = PI.toFloat()

private const val BAR_FILL = 0.7f
private const val MIN_HALF = 2f

/** Fraction of the control's half-height the tallest bar may reach. */
private const val MAX_BAR_FILL = 0.78f
private const val WAVE_AMPLITUDE = 0.05f
private val WAVE_STROKE = 3.dp

/**
 * Loads the waveform for whatever is playing.
 *
 * A track already analysed in memory is shown immediately; everything else reports [WaveformState.Loading]
 * while it decodes, so the caller can keep a plain slider up in the meantime. Loading a new track
 * cancels the previous analysis — decoding a track the user has already skipped past helps nobody.
 */
class WaveformViewModel(
    private val playerManager: PlayerManager,
    private val repository: WaveformRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<WaveformState?>(null)
    val state: StateFlow<WaveformState?> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadedSongId: String? = null

    fun load(song: Song) {
        if (song.id == loadedSongId && loadJob?.isActive != false) return
        loadJob?.cancel()
        loadedSongId = song.id

        repository.memoryCached(song.id)?.let { peaks ->
            _state.value = WaveformState.Loaded(peaks)
            return
        }

        _state.value = WaveformState.Loading
        loadJob = viewModelScope.launch {
            val streamUrl = playerManager.getStreamUrl(song.id)
            val peaks = if (streamUrl == null) {
                FloatArray(0)
            } else {
                try {
                    repository.getPeaks(song.id, streamUrl)
                } catch (e: CancellationException) {
                    // The user moved to another track; the newer load owns the state now.
                    throw e
                } catch (_: Exception) {
                    FloatArray(0)
                }
            }
            _state.value = if (peaks.isEmpty()) {
                WaveformState.Unavailable
            } else {
                WaveformState.Loaded(peaks)
            }
        }
    }
}
