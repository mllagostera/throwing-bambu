package com.vansid.panda.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Everything the UI needs to know about a match in progress (§11).
 *
 * `RoundEnd` is not a `data class`: it holds an array (D-06).
 */
sealed interface MatchEvent {
    data class RoundStart(
        val round: Int,
        val scenario: Scenario,
        val wind: Int,
    ) : MatchEvent

    data class TurnStart(
        val player: Int,
        val wind: Int,
        val turn: Int,
    ) : MatchEvent

    data class ShotFired(
        val player: Int,
        val shot: Shot,
        val result: ShotResult,
    ) : MatchEvent

    data class TerrainChanged(
        val cx: Int,
        val cy: Int,
        val r: Int,
    ) : MatchEvent

    class RoundEnd(
        val winner: Int,
        val scores: IntArray,
    ) : MatchEvent

    data class MatchEnd(
        val winner: Int,
        val scores: IntArray,
    ) : MatchEvent
}

/**
 * Scenario seed for a round. Derived from the match seed, never transmitted, for the
 * same reason as the wind (§4): both devices compute it and there is nothing to keep in
 * sync. The multiplier differs from the one in [windForTurn] so the two streams do not
 * walk in step.
 */
fun scenarioSeedForRound(
    seed: Long,
    round: Int,
): Long = seed * ROUND_SEED_MULTIPLIER + round

/** Deliberately different from the multiplier in [windForTurn]. */
private const val ROUND_SEED_MULTIPLIER = 0x27220A95L

/**
 * The match loop (§11), shared by the three modes.
 *
 * `TurnStart` → `sources[current].nextShot(...)` → `simulate` → `ShotFired` → apply the
 * crater → evaluate → switch turns. The UI only consumes [events] and animates.
 */
class MatchEngine(
    val seed: Long,
    val width: Int,
    private val sources: List<ShotSource>,
    val roundsToWin: Int = 3,
    private val startingPlayer: Int = 0,
) {
    // No buffer on purpose: `emit` suspends until the collector has taken the event, so
    // the engine runs at the pace of whoever is watching. That is what keeps the crater
    // from opening before the UI has finished animating the throw that caused it.
    private val _events = MutableSharedFlow<MatchEvent>()

    val events: Flow<MatchEvent> = _events.asSharedFlow()

    private val scores = IntArray(G.PLAYERS)

    /** Match-wide monotonic turn counter: the one [windForTurn] and the protocol use. */
    var turn: Int = 0
        private set

    /**
     * Runs the match to completion.
     *
     * It waits for a subscriber before emitting: a `SharedFlow` with no replay drops
     * whatever is emitted before anyone collects, and losing `RoundStart` would leave
     * the UI without a scenario to draw.
     */
    suspend fun run() {
        _events.subscriptionCount.first { it > 0 }

        var round = 0
        while (scores[0] < roundsToWin && scores[1] < roundsToWin) {
            playRound(round)
            round++
        }

        val winner = if (scores[0] > scores[1]) 0 else 1
        _events.emit(MatchEvent.MatchEnd(winner, scores.copyOf()))
    }

    private suspend fun playRound(round: Int) {
        val scenario = generate(scenarioSeedForRound(seed, round), width)
        // The first player alternates each round, as in the original.
        var current = (startingPlayer + round) % G.PLAYERS

        _events.emit(MatchEvent.RoundStart(round, scenario, windForTurn(seed, turn)))

        while (true) {
            val wind = windForTurn(seed, turn)
            _events.emit(MatchEvent.TurnStart(current, wind, turn))

            val shot = sources[current].nextShot(scenario, current, wind, turn)
            val result = simulate(scenario, current, shot, wind)
            _events.emit(MatchEvent.ShotFired(current, shot, result))

            val loser = applyImpact(scenario, result)
            turn++

            if (loser != null) {
                val winner = 1 - loser
                scores[winner]++
                scenario.pandas[loser].alive = false
                _events.emit(MatchEvent.RoundEnd(winner, scores.copyOf()))
                return
            }
            current = 1 - current
        }
    }

    /**
     * Applies the crater and reports who lost the round, or `null` if the round goes on.
     *
     * Physics does not mutate the terrain (§8), so this is the only place where the
     * scenario changes. An own goal counts: the round is lost by whoever got hit.
     */
    private suspend fun applyImpact(
        scenario: Scenario,
        result: ShotResult,
    ): Int? =
        when (val outcome = result.outcome) {
            is Outcome.HitTerrain -> {
                crater(scenario, outcome.x, outcome.y)
                null
            }

            is Outcome.HitPanda -> {
                crater(scenario, result.impactX, result.impactY)
                outcome.player
            }

            Outcome.OffScreen, Outcome.TimeOut -> null
        }

    private suspend fun crater(
        scenario: Scenario,
        cx: Int,
        cy: Int,
    ) {
        scenario.terrain.blast(cx, cy, G.CRATER_R)
        _events.emit(MatchEvent.TerrainChanged(cx, cy, G.CRATER_R))
    }
}
