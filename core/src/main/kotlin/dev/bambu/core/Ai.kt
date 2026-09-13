package dev.bambu.core

import kotlin.math.sqrt

/**
 * Difficulty (§9).
 *
 * The three levels share one search; what separates them is how much noise is added to
 * its answer, and whether the answer is refined first. A "worse" AI that aimed with a
 * different algorithm would miss in ways that look like bugs rather than like a weaker
 * opponent.
 */
enum class AiLevel(
    val sigmaAngle: Float,
    val sigmaPower: Float,
    val refine: Boolean,
) {
    EASY(10f, 12f, false),
    MEDIUM(4f, 5f, true),
    HARD(1f, 1.5f, true),
}

// Coarse grid (§9): 16 angles x 17 powers = 272 simulations.
private const val COARSE_ANGLE_MIN = 10
private const val COARSE_ANGLE_MAX = 85
private const val COARSE_POWER_MIN = 20
private const val COARSE_STEP = 5

/** Refinement window around the best coarse result, step 1. */
private const val REFINE_SPAN = 4

/**
 * The computer opponent (§9).
 *
 * It aims by brute force: simulate every shot on a coarse grid, keep the one that lands
 * closest to the enemy, refine around it, then blur the answer according to the level.
 *
 * There is deliberately **no analytical ballistics solution**: wind and intervening
 * buildings invalidate it, and it would add nothing the search does not already give in
 * a couple of milliseconds.
 */
class AiOpponent(
    private val level: AiLevel,
    private val rng: Rng,
) {
    /** Reused across every simulation: the AI is the only place a borrowed path is safe (D-01). */
    private val scratch = FloatArray(G.MAX_STEPS * 2)

    fun chooseShot(
        scenario: Scenario,
        me: Int,
        wind: Int,
        turn: Int,
    ): Shot {
        val best = coarseSearch(scenario, me, wind)
        val refined = if (level.refine) refineAround(best, scenario, me, wind) else best

        val angle =
            (refined.angle + rng.nextGaussian() * level.sigmaAngle)
                .toInt()
                .coerceIn(G.ANGLE_MIN, G.ANGLE_MAX)
        val power =
            (refined.power + rng.nextGaussian() * level.sigmaPower)
                .toInt()
                .coerceIn(G.POWER_MIN, G.POWER_MAX)

        return Shot(turn, angle, power)
    }

    private fun coarseSearch(
        scenario: Scenario,
        me: Int,
        wind: Int,
    ): Aim {
        var best = Aim(COARSE_ANGLE_MIN, COARSE_POWER_MIN, Float.MAX_VALUE)
        var angle = COARSE_ANGLE_MIN
        while (angle <= COARSE_ANGLE_MAX) {
            var power = COARSE_POWER_MIN
            while (power <= G.POWER_MAX) {
                val score = score(scenario, me, angle, power, wind)
                if (score < best.score) best = Aim(angle, power, score)
                power += COARSE_STEP
            }
            angle += COARSE_STEP
        }
        return best
    }

    private fun refineAround(
        around: Aim,
        scenario: Scenario,
        me: Int,
        wind: Int,
    ): Aim {
        var best = around
        for (angle in (around.angle - REFINE_SPAN)..(around.angle + REFINE_SPAN)) {
            if (angle !in G.ANGLE_MIN..G.ANGLE_MAX) continue
            for (power in (around.power - REFINE_SPAN)..(around.power + REFINE_SPAN)) {
                if (power !in G.POWER_MIN..G.POWER_MAX) continue
                val score = score(scenario, me, angle, power, wind)
                if (score < best.score) best = Aim(angle, power, score)
            }
        }
        return best
    }

    /**
     * How bad a shot is: the distance from where it landed to the enemy's centre.
     *
     * Leaving the screen, timing out or hitting yourself score worst possible. Scoring
     * an own goal by distance would make the AI walk into one whenever the enemy stands
     * behind it.
     */
    private fun score(
        scenario: Scenario,
        me: Int,
        angle: Int,
        power: Int,
        wind: Int,
    ): Float {
        val result = simulateInto(scenario, me, Shot(0, angle, power), wind, scratch)
        val outcome = result.outcome
        return when {
            outcome is Outcome.HitPanda && outcome.player != me -> 0f
            outcome is Outcome.HitPanda -> Float.MAX_VALUE
            outcome is Outcome.OffScreen || outcome is Outcome.TimeOut -> Float.MAX_VALUE
            else -> distanceToEnemy(scenario, me, result)
        }
    }

    private fun distanceToEnemy(
        scenario: Scenario,
        me: Int,
        result: ShotResult,
    ): Float {
        val enemy = scenario.pandas[1 - me]
        val dx = (result.impactX - enemy.x).toFloat()
        val dy = (result.impactY - (enemy.roofY - G.PANDA_H / 2)).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    private data class Aim(
        val angle: Int,
        val power: Int,
        val score: Float,
    )
}
