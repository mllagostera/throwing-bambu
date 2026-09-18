package com.vansid.panda.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AiTest {
    @Test
    fun everyShotIsWithinTheContractRanges() {
        for (level in AiLevel.entries) {
            val ai = AiOpponent(level, Rng(SEED))
            for (i in 0 until 25) {
                val scenario = generate(SEED + i, G.W_MIN)
                val shot = ai.chooseShot(scenario, me = 0, wind = windForTurn(SEED, i), turn = i)
                assertTrue("angle out of range: ${shot.angle}", shot.angle in G.ANGLE_MIN..G.ANGLE_MAX)
                assertTrue("power out of range: ${shot.power}", shot.power in G.POWER_MIN..G.POWER_MAX)
                assertEquals("the shot does not carry its turn", i, shot.turn)
            }
        }
    }

    /** Same seed, same scenario, same shot: an AI match has to be reproducible too. */
    @Test
    fun theAiIsDeterministic() {
        val scenario = generate(SEED, G.W_MIN)
        val first = AiOpponent(AiLevel.HARD, Rng(99L)).chooseShot(scenario, 0, 3, 0)
        val second = AiOpponent(AiLevel.HARD, Rng(99L)).chooseShot(scenario, 0, 3, 0)
        assertEquals(first, second)
    }

    /** Harder levels aim tighter: that is the only thing the level changes (§9). */
    @Test
    fun higherLevelsScatterLess() {
        val scenario = generate(SEED, G.W_MIN)
        val spread =
            AiLevel.entries.associateWith { level ->
                val ai = AiOpponent(level, Rng(SEED))
                val shots = List(60) { ai.chooseShot(scenario, 0, 0, it) }
                val meanAngle = shots.map { it.angle }.average()
                shots.map { abs(it.angle - meanAngle) }.average()
            }
        assertTrue(
            "HARD scatters as much as MEDIUM: ${spread[AiLevel.HARD]} vs ${spread[AiLevel.MEDIUM]}",
            spread.getValue(AiLevel.HARD) < spread.getValue(AiLevel.MEDIUM),
        )
        assertTrue(
            "MEDIUM scatters as much as EASY: ${spread[AiLevel.MEDIUM]} vs ${spread[AiLevel.EASY]}",
            spread.getValue(AiLevel.MEDIUM) < spread.getValue(AiLevel.EASY),
        )
    }

    /**
     * Without noise the search alone should be close on an open field.
     *
     * This is the search under test, not the difficulty: HARD still adds a degree or so
     * of blur on top, which is what makes it beatable.
     */
    @Test
    fun theSearchLandsNearTheEnemyOnOpenGround() {
        val scenario = TestScenarios.empty(opponentX = 380, opponentAlive = true)
        val ai = AiOpponent(AiLevel.HARD, Rng(SEED))
        val shot = ai.chooseShot(scenario, me = 0, wind = 0, turn = 0)
        val result = simulate(scenario, 0, shot, 0)

        val enemy = scenario.pandas[1]
        val missBy = abs(result.impactX - enemy.x)
        assertTrue("HARD missed by $missBy px on an open field", missBy < 40)
    }

    @Test
    fun theAiDoesNotWalkIntoAnOwnGoal() {
        // Scoring own goals by distance would make the AI shoot itself whenever the
        // enemy happens to sit behind it.
        val scenario = TestScenarios.empty(opponentX = 380, opponentAlive = true)
        val ai = AiOpponent(AiLevel.HARD, Rng(SEED))
        var ownGoals = 0
        for (turn in 0 until 30) {
            val shot = ai.chooseShot(scenario, 0, windForTurn(SEED, turn), turn)
            if (simulate(scenario, 0, shot, windForTurn(SEED, turn)).outcome == Outcome.HitPanda(0)) {
                ownGoals++
            }
        }
        assertEquals("the AI threw at itself", 0, ownGoals)
    }

    @Test
    fun bothPlayersCanBeDrivenByTheAi() {
        // Player 1 throws leftwards; the same angle range has to work for it.
        val scenario = generate(SEED, G.W_MAX)
        val shot = AiOpponent(AiLevel.HARD, Rng(SEED)).chooseShot(scenario, me = 1, wind = 0, turn = 0)
        val result = simulate(scenario, 1, shot, 0)
        assertNotEquals("player 1 immediately lost its own shot", Outcome.OffScreen, result.outcome)
    }

    @Test
    fun theShotSourcePausesBeforeAnswering() =
        runBlocking {
            var asked = 0
            val source =
                AiShotSource(
                    opponent = AiOpponent(AiLevel.EASY, Rng(SEED)),
                    thinkingDelayMs = {
                        asked++
                        0L
                    },
                )
            val scenario = generate(SEED, G.W_MIN)
            val shot = source.nextShot(scenario, me = 1, wind = 0, turn = 7)
            assertEquals(7, shot.turn)
            assertEquals("the pause was not applied", 1, asked)
        }

    @Test
    fun thinkingTimeStaysInTheReadableRange() {
        for (turn in 0 until 50) {
            val ms = defaultThinkingDelay(turn)
            assertTrue("thinking time out of range: $ms", ms in 600L..1200L)
        }
    }

    private companion object {
        const val SEED = 20260913L
    }
}
