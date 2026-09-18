package com.vansid.panda.core.net

import com.vansid.panda.core.G
import com.vansid.panda.core.MatchEngine
import com.vansid.panda.core.MatchEvent
import com.vansid.panda.core.ReplayingShotSource
import com.vansid.panda.core.ScriptedShotSource
import com.vansid.panda.core.Shot
import com.vansid.panda.core.ShotSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A9 (D-10): replaying the shot history rebuilds a state identical to the uninterrupted one.
 *
 * This is what makes reconnection cheap. Nothing about the terrain, the score or whose
 * turn it is has to be serialised, sent or agreed upon — the shots are replayed and
 * determinism does the rest. If this test ever fails, reconnection is not the feature
 * that broke: determinism is.
 */
class ResumeTest {
    @Test
    fun replayingTheHistoryRebuildsTheSameMatch() {
        val uninterrupted = play()
        val history = shotsFrom(uninterrupted)
        assertTrue("the match needs several shots to prove anything", history.size >= 3)

        // Cut the link after the second shot and rebuild from what was recorded.
        for (cut in 1 until history.size) {
            val resumed = play(resumeFrom = history.take(cut))
            assertEquals(
                "resuming after $cut shot(s) produced a different match",
                uninterrupted.map(::describe),
                resumed.map(::describe),
            )
        }
    }

    @Test
    fun aResumedMatchEndsOnTheSameScore() {
        val uninterrupted = play().filterIsInstance<MatchEvent.MatchEnd>().single()
        val resumed =
            play(resumeFrom = shotsFrom(play()).take(2))
                .filterIsInstance<MatchEvent.MatchEnd>()
                .single()

        assertEquals(uninterrupted.winner, resumed.winner)
        assertEquals(uninterrupted.scores.toList(), resumed.scores.toList())
    }

    @Test
    fun theTerrainIsRebuiltCraterForCrater() {
        val uninterrupted = play()
        val resumed = play(resumeFrom = shotsFrom(uninterrupted).take(2))

        // The fingerprint covers every pixel of mask and colour, so two terrains that
        // agree on it agree on every crater ever opened.
        val before = uninterrupted.filterIsInstance<MatchEvent.RoundStart>().map { it.scenario.terrain.fingerprint() }
        val after = resumed.filterIsInstance<MatchEvent.RoundStart>().map { it.scenario.terrain.fingerprint() }
        assertEquals(before, after)
    }

    @Test
    fun aWrongHistoryProducesADifferentMatch() {
        // The guard against a test that would pass on anything: feed it the wrong shots
        // and the comparison must notice.
        val uninterrupted = play()
        val tampered =
            shotsFrom(
                uninterrupted,
            ).take(2).map { Shot(it.turn, it.angle, (it.power + 20).coerceAtMost(100)) }
        val resumed = play(resumeFrom = tampered)

        assertNotEquals(uninterrupted.map(::describe), resumed.map(::describe))
    }

    /** A peer that reconnects asks with RESUME and is answered with the history (§12). */
    @Test
    fun resumeIsAnsweredWithTheHistory() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (linkA, linkB) = LoopbackTransport.pair()
                val host =
                    RemoteMatch(linkA, 0, ScriptedShotSource(SCRIPT), MatchConfig(SEED, G.W_MIN, roundsToWin = 2))
                val guest =
                    RemoteMatch(linkB, 1, ScriptedShotSource(SCRIPT), MatchConfig(SEED, G.W_MIN, roundsToWin = 2))

                val guestJob = launch(Dispatchers.Unconfined) { guest.run() }
                host.run()
                guestJob.join()

                // Both finished the match, so both hold the whole history.
                assertTrue("the host recorded nothing", host.history.isNotEmpty())
                assertEquals(
                    host.history.sortedBy { it.turn },
                    guest.history.sortedBy { it.turn },
                )
            }
        }

    /**
     * The one that covers the wiring: two [RemoteMatch]es over a link, cut and resumed.
     *
     * The tests above prove the *engine* replays correctly, but they build their sources
     * by hand. This one goes through [RemoteMatch] itself, which is where a resumed match
     * is actually assembled — and where forgetting to wrap the live source in a
     * [ReplayingShotSource] would go unnoticed by every other test in this file.
     */
    @Test
    fun aLinkedMatchResumesIntoTheSameMatch() {
        val (uninterrupted, played) = playLinked()
        val history = played.history.sortedBy { it.turn }
        assertTrue("the match needs several shots to prove anything", history.size >= 3)

        for (cut in 1 until history.size) {
            val (resumed, session) = playLinked(resumeFrom = history.take(cut))
            assertEquals(
                "resuming a linked match after $cut shot(s) produced a different match",
                uninterrupted.map(::describe),
                resumed.map(::describe),
            )
            assertEquals("the replay never finished catching up", 0, session.replaysRemaining)
            assertEquals("a replayed turn was recorded twice", history, session.history.sortedBy { it.turn })
        }
    }

    /** Replayed turns are not re-announced: no duplicate shots, no duplicate results. */
    @Test
    fun aResumedSessionDoesNotResendWhatItReplayed() {
        val (_, played) = playLinked()
        val history = played.history.sortedBy { it.turn }

        val (_, resumed) = playLinked(resumeFrom = history)
        assertEquals(history, resumed.history.sortedBy { it.turn })
        assertEquals("the peers disagreed on a replayed turn", 0, resumed.divergences)
    }

    /** Runs a whole match across a loopback link, returning the host's events and session. */
    private fun playLinked(resumeFrom: List<Shot> = emptyList()): Pair<List<MatchEvent>, RemoteMatch> =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (linkA, linkB) = LoopbackTransport.pair()
                val config = MatchConfig(SEED, G.W_MIN, roundsToWin = 2)
                val host = RemoteMatch(linkA, 0, ScriptedShotSource(SCRIPT), config, resumeFrom)
                val guest = RemoteMatch(linkB, 1, ScriptedShotSource(SCRIPT), config, resumeFrom)

                val events = mutableListOf<MatchEvent>()
                val collector = launch(Dispatchers.Unconfined) { host.engine.events.collect { events += it } }
                val guestJob = launch(Dispatchers.Unconfined) { guest.run() }
                host.run()
                guestJob.join()
                collector.cancel()
                events to host
            }
        }

    /** Plays a whole match, optionally replaying a history first. */
    private fun play(resumeFrom: List<Shot> = emptyList()): List<MatchEvent> =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val engine =
                    MatchEngine(
                        seed = SEED,
                        width = G.W_MIN,
                        sources = List(2) { source(resumeFrom) },
                        roundsToWin = 2,
                    )
                val events = mutableListOf<MatchEvent>()
                val collector = launch(Dispatchers.Unconfined) { engine.events.collect { events += it } }
                engine.run()
                collector.cancel()
                events
            }
        }

    private fun source(resumeFrom: List<Shot>): ShotSource {
        val live = ScriptedShotSource(SCRIPT)
        return if (resumeFrom.isEmpty()) live else ReplayingShotSource(resumeFrom, live)
    }

    private fun shotsFrom(events: List<MatchEvent>): List<Shot> =
        events.filterIsInstance<MatchEvent.ShotFired>().map { it.shot }

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

    private companion object {
        const val SEED = 20260913L
        const val TIMEOUT_MS = 30_000L

        /** Two shots into the terrain, then an own goal that closes the round. */
        val SCRIPT = listOf(Shot(0, 45, 50), Shot(0, 30, 40), Shot(0, 90, 20))
    }
}
