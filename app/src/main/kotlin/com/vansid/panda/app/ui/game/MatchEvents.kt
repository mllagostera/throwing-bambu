package com.vansid.panda.app.ui.game

import com.vansid.panda.app.audio.Sfx
import com.vansid.panda.app.audio.SoundBank
import com.vansid.panda.app.render.PandaFrame
import com.vansid.panda.app.render.TerrainBitmap
import com.vansid.panda.core.MatchEvent
import com.vansid.panda.core.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The crater opens on frame 3 of the explosion (§13). */
private const val BOOM_CRATER_FRAME = 3
private const val BOOM_LAST_FRAME = 7

/**
 * Turns what the engine says happened into what the screen shows.
 *
 * Split out of [GameViewModel] because the two answer different questions. The view
 * model owns the match's lifetime — start it, feed it a shot, hang up the link. This
 * owns the translation, one function per kind of event, and it is the half that grows
 * every time the game gains something to draw.
 */
internal class MatchEvents(
    private val state: MutableStateFlow<GameUiState>,
    private val animator: MatchAnimator,
) {
    /**
     * Where the noises go, once there is a decoder to make them (T-50).
     *
     * Null until the screen attaches one, and null again in a test: a match runs and is
     * scored exactly the same either way, because nothing here reads anything back from
     * the bank.
     */
    var sounds: SoundBank? = null

    suspend fun handle(event: MatchEvent) {
        when (event) {
            is MatchEvent.RoundStart -> onRoundStart(event)
            is MatchEvent.TurnStart -> onTurnStart(event)
            is MatchEvent.ShotFired -> onShotFired(event)
            is MatchEvent.TerrainChanged -> onTerrainChanged(event)
            is MatchEvent.RoundEnd -> onRoundEnd(event)
            is MatchEvent.MatchEnd -> onMatchEnd(event)
        }
    }

    private fun onRoundStart(event: MatchEvent.RoundStart) =
        state.update {
            it.copy(
                phase = GamePhase.AIMING,
                round = event.round,
                scenario = event.scenario,
                terrain = TerrainBitmap(event.scenario.terrain),
                terrainVersion = 0,
                wind = event.wind,
                canePoint = null,
                trail = emptyList(),
                boom = null,
                sunOuch = false,
                pandaPoses = emptyMap(),
                roundWinner = null,
            )
        }

    private fun onTurnStart(event: MatchEvent.TurnStart) =
        state.update {
            it.copy(
                phase = GamePhase.AIMING,
                currentPlayer = event.player,
                wind = event.wind,
                turn = event.turn,
                canePoint = null,
                trail = emptyList(),
            )
        }

    /**
     * Animates the throw and then the first frames of the explosion.
     *
     * The split is not arbitrary: the crater opens on frame 3 (§13), and the engine
     * emits `TerrainChanged` right after this event. Playing frames 0–2 here and the
     * rest in [onTerrainChanged] is what puts the hole in the ground on the exact frame
     * the art was drawn for.
     */
    private suspend fun onShotFired(event: MatchEvent.ShotFired) {
        state.update {
            it.copy(
                phase = GamePhase.THROWING,
                lastShots = it.lastShots + (event.player to event.shot),
                pandaPoses = it.pandaPoses + (event.player to PandaFrame.throwing(event.player)),
            )
        }
        // With the pose, not before it and not after the flight: the whoosh belongs to
        // the arm coming down, and the cane is on screen from the first frame of it.
        sounds?.play(Sfx.THROW)
        animator.flight(event)
        state.update {
            it.copy(
                canePoint = null,
                trail = emptyList(),
                pandaPoses = it.pandaPoses + (event.player to PandaFrame.IDLE),
            )
        }
        // Only a hit opens a crater, so only a hit gets the first explosion frames here;
        // the rest follow the TerrainChanged the engine sends straight after.
        val hit = event.result.outcome is Outcome.HitTerrain || event.result.outcome is Outcome.HitPanda
        if (hit) {
            // On frame 0, so the bang and the first flash of the blast are the same
            // moment. Waiting for the crater on frame 3 would put the sound 120 ms late,
            // which is far enough to hear as a mistake.
            sounds?.play(Sfx.BOOM)
            animator.boom(event.result.impactX, event.result.impactY, 0, BOOM_CRATER_FRAME - 1)
        }
    }

    private suspend fun onTerrainChanged(event: MatchEvent.TerrainChanged) {
        state.update { state ->
            state.terrain?.patch(event.cx, event.cy, event.r)
            state.copy(terrainVersion = state.terrain?.version ?: state.terrainVersion)
        }
        animator.boom(event.cx, event.cy, BOOM_CRATER_FRAME, BOOM_LAST_FRAME)
        state.update { it.copy(boom = null) }
    }

    private fun onRoundEnd(event: MatchEvent.RoundEnd) =
        state.update {
            it.copy(
                phase = GamePhase.ROUND_OVER,
                roundWinner = event.winner,
                scores = event.scores.toList(),
                canePoint = null,
            )
        }

    /**
     * The match is over, and the sound says so from this device's point of view.
     *
     * Won or lost is not a property of the match, it is a property of who is watching.
     * `humanPlayers` is the set of seats somebody here throws from, so a winner inside it
     * is a win in this room: against the computer that is the one human seat, across a
     * link it is ours, and in a hot-seat match it is both — which is why two people at
     * one phone always hear the victory. Somebody at this device did win.
     */
    private fun onMatchEnd(event: MatchEvent.MatchEnd) {
        val won = event.winner in state.value.humanPlayers
        sounds?.play(if (won) Sfx.VICTORY else Sfx.DEFEAT)
        state.update {
            it.copy(
                phase = GamePhase.MATCH_OVER,
                matchWinner = event.winner,
                scores = event.scores.toList(),
            )
        }
    }
}
