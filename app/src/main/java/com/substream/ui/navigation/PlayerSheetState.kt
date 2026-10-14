package com.substream.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The travel of the player sheet, shared by the mini bar and the sheet itself.
 *
 * They cannot each keep their own position: a drag that starts on the bar opens the sheet, so the
 * sheet is mounted halfway through a gesture that began somewhere else. One object holds where it
 * is, and both ends ask it the same question.
 *
 * 0 is in place, 1 is entirely below the bottom edge. Everything else is a fraction of the sheet's
 * own height, so it behaves the same on a phone and on a tablet.
 */
@Stable
internal class PlayerSheetState(private val scope: CoroutineScope) {

    val offset = Animatable(1f)

    /**
     * Called when the sheet has finished travelling down and should leave the tree.
     *
     * Set by the sheet, because only it knows how to collapse itself. The bar's opening drag can
     * end in a dismissal too, and that happens while the bar holds the state and the sheet is
     * still catching up, so the sheet has to lend it a way out.
     */
    var onDismissed: () -> Unit = {}

    /** The distance all of that fraction is measured against, from the sheet's own layout. */
    var heightPx by mutableIntStateOf(1)
        private set

    /** A finger is on the sheet. While this holds, the sheet animates nothing by itself. */
    var isHeld by mutableStateOf(false)
        private set

    fun measure(height: Int) {
        heightPx = height
    }

    fun hold() {
        isHeld = true
    }

    /**
     * Moves the sheet by [deltaPx], positive downwards, and reports how much of that it took.
     *
     * Suspending work has to be a coroutine, so the new position is worked out here and applied
     * there; that is also why this can report a distance rather than just taking one.
     */
    fun pull(deltaPx: Float): Float {
        val travel = heightPx.toFloat()
        if (travel <= 1f) return 0f
        val from = offset.value
        val to = (from + deltaPx / travel).coerceIn(0f, 1f)
        scope.launch { offset.snapTo(to) }
        return (to - from) * travel
    }

    /**
     * The bar's drag has let go. Whatever the finger covered has already been shown; the rest of
     * the opening happens now, on its own, because the user has said they want it.
     *
     * Unless the drag was taken back down to where it started, which is a change of mind rather
     * than a change of pace, and closes the sheet again.
     */
    fun finishOpen() {
        isHeld = false
        if (offset.value <= PlayerSheetAbandonedFraction) {
            scope.launch { offset.animateTo(0f, PlayerSheetSpring) }
        } else {
            // Taken back down to where it started: the sheet never wanted to be here, so it has
            // to be taken out of the tree rather than left sitting on the app, invisible.
            scope.launch {
                offset.animateTo(1f, PlayerSheetSpring)
                onDismissed()
            }
        }
    }

    /**
     * A drag on the open sheet has let go. Flung hard or pulled past a third of the way down and
     * it is gone; anything less springs back to where it was.
     */
    fun finishDrag(velocityPxPerSecond: Float) {
        isHeld = false
        val dismissing = velocityPxPerSecond > PlayerSheetFlingAway ||
            offset.value > PlayerSheetDismissFraction
        scope.launch { offset.animateTo(if (dismissing) 1f else 0f, PlayerSheetSpring) }
    }

    /** Travel on its own, for the tap that opens the sheet and the chevron that closes it. */
    suspend fun travelTo(target: Float) {
        isHeld = false
        offset.animateTo(target, PlayerSheetSpring)
    }
}

/**
 * The slow, interruptible travel of the player sheet, shared by the open and the close.
 *
 * Soft and slow on purpose: Material's sheet spring is quick enough to be over before the eye has
 * caught up, which is what makes a tap feel like nothing happened. Slightly under critical damping,
 * so it arrives with a hint of weight rather than a wobble.
 */
internal val PlayerSheetSpring = androidx.compose.animation.core.spring<Float>(
    dampingRatio = 0.95f,
    stiffness = 240f,
)

/** Fling down faster than this (px/s) and the sheet is gone, wherever it had got to. */
internal const val PlayerSheetFlingAway = 1200f

/** Or dragged this far down (as a fraction of its height) and it is gone too. */
internal const val PlayerSheetDismissFraction = 0.35f

/**
 * Or pulled back this far down during the opening drag, having barely started, and it never
 * finishes opening.
 */
internal const val PlayerSheetAbandonedFraction = 0.9f

/** What the mini bar's swipe does to open the sheet, as the sheet understands it. */
interface PullToExpand {
    /** The drag has begun: bring the sheet to life under the finger, without animating it. */
    fun onGrab()

    /** Move the sheet with the finger, [deltaY] pixels, positive downwards. */
    fun onDrag(deltaY: Float)

    /** The finger has gone. The sheet decides where that leaves it. */
    fun onRelease()
}