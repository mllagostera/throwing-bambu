package dev.bambu.app.ui.game

import androidx.compose.ui.geometry.Offset
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
    val trail: List<Offset> = emptyList(),
    val sunHit: Boolean = false,
    /** Last shot of each player, for the "Repeat previous" shortcut. */
    val lastShots: Map<Int, Shot> = emptyMap(),
    val roundWinner: Int? = null,
    val matchWinner: Int? = null,
) {
    val canAim: Boolean get() = phase == GamePhase.AIMING
}
