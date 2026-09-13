package dev.bambu.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bambu.app.render.PandaFrame
import dev.bambu.app.render.TerrainBitmap
import dev.bambu.core.HumanShotSource
import dev.bambu.core.MatchEngine
import dev.bambu.core.MatchEvent
import dev.bambu.core.Outcome
import dev.bambu.core.Shot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The crater opens on frame 3 of the explosion (§13). */
private const val BOOM_CRATER_FRAME = 3
private const val BOOM_LAST_FRAME = 7

/**
 * Drives one match and turns its events into something the UI can draw.
 *
 * The engine lives here, in `viewModelScope`, not in the composition: a rotation or a
 * trip to the background must not restart the match (T-21). The UI only reads
 * [uiState] and calls [submit].
 */
class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val sources = listOf(HumanShotSource(), HumanShotSource())
    private val animator = MatchAnimator(_uiState, viewModelScope)
    private var engine: MatchEngine? = null
    private var job: Job? = null

    var speedMultiplier: Int
        get() = animator.speedMultiplier
        set(value) {
            animator.speedMultiplier = value
        }

    /** Starts the match. Calling it twice is a no-op: the running match wins. */
    fun start(
        seed: Long,
        width: Int,
        roundsToWin: Int,
    ) {
        if (engine != null) return
        val created = MatchEngine(seed, width, sources, roundsToWin)
        engine = created
        job = viewModelScope.launch { created.events.collect { handle(it) } }
        viewModelScope.launch { created.run() }
    }

    /** The UI's Throw button. Ignored unless the player is actually aiming. */
    fun submit(
        angle: Int,
        power: Int,
    ) {
        val state = _uiState.value
        if (!state.canAim) return
        viewModelScope.launch {
            sources[state.currentPlayer].submit(Shot(state.turn, angle, power))
        }
    }

    private suspend fun handle(event: MatchEvent) {
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
        _uiState.update {
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
        _uiState.update {
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
        _uiState.update {
            it.copy(
                phase = GamePhase.THROWING,
                lastShots = it.lastShots + (event.player to event.shot),
                pandaPoses = it.pandaPoses + (event.player to PandaFrame.throwing(event.player)),
            )
        }
        animator.flight(event)
        _uiState.update {
            it.copy(
                canePoint = null,
                trail = emptyList(),
                pandaPoses = it.pandaPoses + (event.player to PandaFrame.IDLE),
            )
        }
        if (leavesACrater(event)) {
            animator.boom(event.result.impactX, event.result.impactY, 0, BOOM_CRATER_FRAME - 1)
        }
    }

    private suspend fun onTerrainChanged(event: MatchEvent.TerrainChanged) {
        _uiState.update { state ->
            state.terrain?.patch(event.cx, event.cy, event.r)
            state.copy(terrainVersion = state.terrain?.version ?: state.terrainVersion)
        }
        animator.boom(event.cx, event.cy, BOOM_CRATER_FRAME, BOOM_LAST_FRAME)
        _uiState.update { it.copy(boom = null) }
    }

    private fun onRoundEnd(event: MatchEvent.RoundEnd) =
        _uiState.update {
            it.copy(
                phase = GamePhase.ROUND_OVER,
                roundWinner = event.winner,
                scores = event.scores.toList(),
                canePoint = null,
            )
        }

    private fun onMatchEnd(event: MatchEvent.MatchEnd) =
        _uiState.update {
            it.copy(
                phase = GamePhase.MATCH_OVER,
                matchWinner = event.winner,
                scores = event.scores.toList(),
            )
        }

    private fun leavesACrater(event: MatchEvent.ShotFired): Boolean =
        event.result.outcome is Outcome.HitTerrain || event.result.outcome is Outcome.HitPanda
}
