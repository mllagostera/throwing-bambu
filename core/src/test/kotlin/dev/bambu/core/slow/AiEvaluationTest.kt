package dev.bambu.core.slow

import dev.bambu.core.AiLevel
import dev.bambu.core.AiOpponent
import dev.bambu.core.G
import dev.bambu.core.Outcome
import dev.bambu.core.Rng
import dev.bambu.core.Scenario
import dev.bambu.core.generate
import dev.bambu.core.simulate
import dev.bambu.core.windForTurn
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The acceptance criterion for M3, measured rather than assumed.
 *
 * "The AI on HARD hits within ≤3 turns in ≥80 % of generated scenarios" is not something
 * a unit test can assert on one board: it is a statistic over many. This harness plays
 * the AI against a stationary opponent across [SCENARIOS] generated maps and reports the
 * hit rate per level.
 *
 * It lives under `slow/` so the Gradle build leaves it out of CI by default; run it with
 * `./gradlew :core:test -PrunSlowTests` when the AI changes.
 */
class AiEvaluationTest {
    @Test
    fun hardHitsWithinThreeTurnsMostOfTheTime() {
        val results = AiLevel.entries.associateWith { evaluate(it) }

        for ((level, r) in results) {
            println("AI-EVAL|$level: ${r.hitRatePercent}% within $MAX_TURNS turns, mean ${r.meanTurns} turns")
        }

        val hard = results.getValue(AiLevel.HARD)
        assertTrue(
            "HARD only hit in ${hard.hitRatePercent}% of scenarios within $MAX_TURNS turns",
            hard.hitRatePercent >= REQUIRED_PERCENT,
        )
        // A difficulty ladder that does not climb is not a ladder.
        assertTrue(
            "HARD is not better than EASY (${hard.hitRatePercent}% vs ${results.getValue(
                AiLevel.EASY,
            ).hitRatePercent}%)",
            hard.hitRatePercent > results.getValue(AiLevel.EASY).hitRatePercent,
        )
    }

    private fun evaluate(level: AiLevel): Evaluation {
        var hits = 0
        var turnsUsed = 0
        for (i in 0 until SCENARIOS) {
            val seed = BASE_SEED + i * 7919L
            val turns = turnsToHit(level, seed)
            if (turns != null) {
                hits++
                turnsUsed += turns
            } else {
                turnsUsed += MAX_TURNS
            }
        }
        return Evaluation(
            hitRatePercent = hits * 100 / SCENARIOS,
            meanTurns = "%.2f".format(turnsUsed.toDouble() / SCENARIOS),
        )
    }

    /**
     * Plays up to [MAX_TURNS] shots and returns how many it took to hit, or `null`.
     *
     * The crater is applied after each miss, exactly as the match engine does: the AI has
     * to cope with the terrain it is carving, which is the situation it faces in a real
     * match.
     */
    private fun turnsToHit(
        level: AiLevel,
        seed: Long,
    ): Int? {
        val scenario: Scenario = generate(seed, G.W_MIN)
        val ai = AiOpponent(level, Rng(seed))

        for (turn in 0 until MAX_TURNS) {
            val wind = windForTurn(seed, turn)
            val shot = ai.chooseShot(scenario, me = 0, wind = wind, turn = turn)
            val result = simulate(scenario, 0, shot, wind)
            when (val outcome = result.outcome) {
                // An own goal ends the round just as a hit does, but in the wrong direction.
                is Outcome.HitPanda -> return (turn + 1).takeIf { outcome.player == 1 }
                is Outcome.HitTerrain -> scenario.terrain.blast(outcome.x, outcome.y, G.CRATER_R)
                else -> Unit
            }
        }
        return null
    }

    private data class Evaluation(
        val hitRatePercent: Int,
        val meanTurns: String,
    )

    private companion object {
        const val SCENARIOS = 200
        const val MAX_TURNS = 3
        const val REQUIRED_PERCENT = 80
        const val BASE_SEED = 20260913L
    }
}
