package com.vansid.panda.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchEngineTest {
    /** §15.11: two deterministic sources produce the same event sequence twice over. */
    @Test
    fun matchIsDeterministic() {
        val first = runMatch(SEED, MIXED_SCRIPT)
        val second = runMatch(SEED, MIXED_SCRIPT)

        assertEquals(first.map(::describe), second.map(::describe))
        assertTrue("the match produced no events", first.isNotEmpty())
    }

    @Test
    fun differentSeedsProduceDifferentMatches() {
        val a = runMatch(SEED, MIXED_SCRIPT).map(::describe)
        val b = runMatch(SEED + 1, MIXED_SCRIPT).map(::describe)
        assertNotEquals(a, b)
    }

    @Test
    fun theMatchEndsWhenSomeoneReachesRoundsToWin() {
        val events = runMatch(SEED, OWN_GOAL_SCRIPT, roundsToWin = 2)
        val end = events.filterIsInstance<MatchEvent.MatchEnd>()

        assertEquals("exactly one MatchEnd expected", 1, end.size)
        assertEquals(2, end.single().scores[end.single().winner])
        assertTrue("the loser cannot have reached roundsToWin", end.single().scores[1 - end.single().winner] < 2)
        assertTrue("MatchEnd is not the last event", events.last() is MatchEvent.MatchEnd)
    }

    /** An own goal is legal (§8) and loses the round for whoever threw it. */
    @Test
    fun anOwnGoalLosesTheRound() {
        val events = runMatch(SEED, OWN_GOAL_SCRIPT, roundsToWin = 1)

        val shot = events.filterIsInstance<MatchEvent.ShotFired>().first()
        val roundEnd = events.filterIsInstance<MatchEvent.RoundEnd>().first()

        assertEquals(Outcome.HitPanda(shot.player), shot.result.outcome)
        assertEquals("the round should go to the other player", 1 - shot.player, roundEnd.winner)
    }

    /** The first player alternates each round, as in the original. */
    @Test
    fun theOpeningPlayerAlternatesBetweenRounds() {
        val events = runMatch(SEED, OWN_GOAL_SCRIPT, roundsToWin = 2)
        val openings =
            events
                .filterIsInstance<MatchEvent.TurnStart>()
                .groupBy { it.turn }
                .toSortedMap()
                .values
                .map { it.first().player }

        // Every round ends on its first shot here, so one TurnStart per round.
        for (i in 1 until openings.size) {
            assertNotEquals("two consecutive rounds opened with the same player", openings[i - 1], openings[i])
        }
    }

    @Test
    fun theTurnCounterIsMatchWideAndMonotonic() {
        val events = runMatch(SEED, MIXED_SCRIPT, roundsToWin = 2)
        val turns = events.filterIsInstance<MatchEvent.TurnStart>().map { it.turn }

        assertEquals("the turn counter restarts or skips", turns.indices.toList(), turns)
        for (t in events.filterIsInstance<MatchEvent.TurnStart>()) {
            assertEquals("the wind does not match the turn", windForTurn(SEED, t.turn), t.wind)
        }
    }

    /** Physics does not mutate the terrain (§8): the engine is the only one that does. */
    @Test
    fun eachImpactLeavesACraterInTheTerrain() {
        var scenario: Scenario? = null
        val events = runMatch(SEED, MIXED_SCRIPT, roundsToWin = 1) { scenario = it }

        val changes = events.filterIsInstance<MatchEvent.TerrainChanged>()
        assertTrue("no crater was reported", changes.isNotEmpty())

        val terrain = scenario!!.terrain
        for (c in changes) {
            assertEquals(G.CRATER_R, c.r)
            assertTrue("the crater centre is still solid", !terrain.solid(c.cx, c.cy))
        }
    }

    /** D-01: an event published earlier must not be touched by later turns. */
    @Test
    fun publishedPathsSurviveLaterTurns() {
        val events = runMatch(SEED, MIXED_SCRIPT, roundsToWin = 1)
        val shots = events.filterIsInstance<MatchEvent.ShotFired>()
        assertTrue("at least two shots are needed to prove anything", shots.size >= 2)

        for (s in shots) {
            assertEquals("path wrongly sized", s.result.steps * 2, s.result.path.size)
        }
        // Two different shots cannot share the same buffer.
        assertTrue(
            "two shots published the same array",
            shots[0].result.path !== shots[1].result.path,
        )
    }

    private fun describe(event: MatchEvent): String =
        when (event) {
            is MatchEvent.RoundStart ->
                "RoundStart(${event.round},${event.scenario.terrain.fingerprint()},${event.wind})"
            is MatchEvent.TurnStart -> "TurnStart(${event.player},${event.wind},${event.turn})"
            is MatchEvent.ShotFired ->
                "ShotFired(${event.player},${event.shot.angle},${event.shot.power}," +
                    "${event.result.outcome},${event.result.steps})"
            is MatchEvent.TerrainChanged -> "TerrainChanged(${event.cx},${event.cy},${event.r})"
            is MatchEvent.RoundEnd -> "RoundEnd(${event.winner},${event.scores.toList()})"
            is MatchEvent.MatchEnd -> "MatchEnd(${event.winner},${event.scores.toList()})"
        }

    private fun runMatch(
        seed: Long,
        script: List<Shot>,
        roundsToWin: Int = 2,
        onScenario: (Scenario) -> Unit = {},
    ): List<MatchEvent> =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val engine =
                    MatchEngine(
                        seed = seed,
                        width = G.W_MIN,
                        sources = listOf(ScriptedShotSource(script), ScriptedShotSource(script)),
                        roundsToWin = roundsToWin,
                    )
                val events = mutableListOf<MatchEvent>()
                val collector =
                    launch(Dispatchers.Unconfined) {
                        engine.events.collect {
                            events += it
                            if (it is MatchEvent.RoundStart) onScenario(it.scenario)
                        }
                    }
                engine.run()
                collector.cancel()
                events
            }
        }

    private companion object {
        const val SEED = 20260913L
        const val TIMEOUT_MS = 30_000L

        /** 90° at low power always comes back onto the thrower: a round ends in one shot. */
        val OWN_GOAL_SCRIPT = listOf(Shot(0, 90, 20))

        /** A couple of shots into the terrain before the round is closed by an own goal. */
        val MIXED_SCRIPT = listOf(Shot(0, 45, 50), Shot(0, 30, 40), Shot(0, 90, 20))
    }
}
