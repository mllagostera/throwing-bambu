package com.vansid.panda.app.ui.game

import androidx.compose.ui.geometry.Offset
import com.vansid.panda.app.render.BoomState
import com.vansid.panda.core.G
import com.vansid.panda.core.MatchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Trajectory points consumed per frame at 1× (§13). */
private const val POINTS_PER_FRAME = 2

/** Cane spin: the four frames of `bamboo.png` at 80 ms (`art/README.md`). */
private const val CANE_FRAME_MS = 80L
private const val CANE_FRAMES = 4

/** Explosion cadence. */
private const val BOOM_FRAME_MS = 40L

/** How long the sun keeps its `ouch` face. */
private const val SUN_OUCH_MS = 1000L

private const val TRAIL_LIMIT = 12

/**
 * Turns a finished simulation into something that moves on screen.
 *
 * It is separate from the view model on purpose: one class decides *what* the state is
 * after each engine event, this one decides *how long* it takes to get there. Mixing
 * both is how animation code ends up deciding game rules.
 */
class MatchAnimator(
    private val state: MutableStateFlow<GameUiState>,
    private val scope: CoroutineScope,
) {
    var speedMultiplier: Int = 1

    /**
     * Plays the trajectory back at screen rate.
     *
     * The path holds ~300 points at 120 Hz, so consuming two per frame at 60 fps gives
     * the real flight time (§13). [speedMultiplier] only changes how many points are
     * consumed, never the physics that produced them.
     */
    suspend fun flight(event: MatchEvent.ShotFired) {
        val path = event.result.path
        val steps = event.result.steps
        val sunX = state.value.scenario?.sunX
        var i = 0
        var sunSeen = false

        while (i < steps) {
            val frameNanos = awaitFrame()
            val x = path[i * 2]
            val y = path[i * 2 + 1]
            val caneFrame = ((frameNanos / 1_000_000L / CANE_FRAME_MS) % CANE_FRAMES).toInt()

            state.update {
                it.copy(
                    canePoint = Offset(x, y),
                    caneFrame = caneFrame,
                    trail = (it.trail + Offset(x, y)).takeLast(TRAIL_LIMIT),
                )
            }

            if (!sunSeen && sunX != null && overlapsSun(sunX, x, y)) {
                sunSeen = true
                sunOuch()
            }
            i += POINTS_PER_FRAME * speedMultiplier
        }
    }

    /** Plays explosion frames [from]..[to] inclusive, at the cadence of the art. */
    suspend fun boom(
        x: Int,
        y: Int,
        from: Int,
        to: Int,
    ) {
        for (f in from..to) {
            state.update { it.copy(boom = BoomState(x, y, f)) }
            delay(BOOM_FRAME_MS)
        }
    }

    private fun sunOuch() {
        state.update { it.copy(sunOuch = true) }
        scope.launch {
            delay(SUN_OUCH_MS)
            state.update { it.copy(sunOuch = false) }
        }
    }

    private fun overlapsSun(
        sunX: Int,
        x: Float,
        y: Float,
    ): Boolean = abs(x - sunX) <= G.SUN_W / 2 + G.CANE_R && y <= G.SUN_H + G.CANE_R
}
