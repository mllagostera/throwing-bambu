package dev.bambu.app.ui.game

import androidx.compose.ui.geometry.Offset
import dev.bambu.app.render.BoomState
import dev.bambu.app.render.TerrainBitmap
import dev.bambu.core.Scenario
import dev.bambu.core.Shot

enum class GamePhase { LOADING, AIMING, THROWING, ROUND_OVER, MATCH_OVER }

/**
 * Everything the game screen needs to draw itself.
 *
 * One immutable snapshot rather than a handful of separate flows: the HUD, the canvas
 * and the controls always show the same instant of the match.
 */
data class GameUiState(
    val phase: GamePhase = GamePhase.LOADING,
    val scenario: Scenario? = null,
    val terrain: TerrainBitmap? = null,
    val terrainVersion: Int = 0,
    val currentPlayer: Int = 0,
    val wind: Int = 0,
    val turn: Int = 0,
    val scores: List<Int> = listOf(0, 0),
    val round: Int = 0,
    val canePoint: Offset? = null,
    val caneFrame: Int = 0,
    val trail: List<Offset> = emptyList(),
    val boom: BoomState? = null,
    /** The sun pulls its `ouch` face for a second after being hit. */
    val sunOuch: Boolean = false,
    /** Pose per player, so the thrower raises an arm while its cane is in the air. */
    val pandaPoses: Map<Int, Int> = emptyMap(),
    /** Last shot of each player, for the "Repeat previous" shortcut. */
    val lastShots: Map<Int, Shot> = emptyMap(),
    val roundWinner: Int? = null,
    val matchWinner: Int? = null,
    /** Which seats a person is sitting in; the rest are driven by the AI. */
    val humanPlayers: Set<Int> = setOf(0, 1),
) {
    /** Aiming is only offered when it is a person's turn: the AI aims for itself. */
    val canAim: Boolean get() = phase == GamePhase.AIMING && currentPlayer in humanPlayers

    val waitingForAi: Boolean get() = phase == GamePhase.AIMING && currentPlayer !in humanPlayers
}
