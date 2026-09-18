package com.vansid.panda.core.net

import com.vansid.panda.core.G
import com.vansid.panda.core.HumanShotSource
import com.vansid.panda.core.Shot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A9 (D-10), the handshake half: `RESUME` asks, `HISTORY` answers.
 *
 * [ResumeTest] proves that replaying a history rebuilds the same match. This proves the
 * reconnecting device can actually *get* that history, including the turns it never saw
 * because the link died while they were in flight.
 */
class ResyncTest {
    @Test
    fun aReconnectingPeerIsGivenTheTurnsItIsMissing() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (link, peer) = LoopbackTransport.pair()
                val running = startResponder(link, HISTORY)

                // The reconnecting side kept the first two turns and lost the rest.
                val resumed = resyncHistory(peer, CONFIG, known = HISTORY.take(2))
                running.cancel()

                assertEquals(HISTORY, resumed)
            }
        }

    @Test
    fun aPeerWithNothingLeftIsGivenTheWholeMatch() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (link, peer) = LoopbackTransport.pair()
                val running = startResponder(link, HISTORY)

                val resumed = resyncHistory(peer, CONFIG)
                running.cancel()

                assertEquals(HISTORY, resumed)
            }
        }

    @Test
    fun aPeerOnAnotherMatchIsRefused() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (link, peer) = LoopbackTransport.pair()
                val running = startResponder(link, HISTORY)

                // Same protocol, different match: the answer must be a refusal, not shots.
                val resumed = resyncHistory(peer, CONFIG.copy(seed = CONFIG.seed + 1))
                running.cancel()

                assertNull("a mismatched seed was answered with a history", resumed)
            }
        }

    @Test
    fun aPeerThatNeverAnswersTimesOut() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (_, peer) = LoopbackTransport.pair()
                assertNull(resyncHistory(peer, CONFIG, timeoutMs = SHORT_TIMEOUT_MS))
            }
        }

    /** The gap matters, not the high-water mark: a hole in the middle must be asked for. */
    @Test
    fun theRequestStartsAtTheFirstGapNotTheLastTurn() {
        assertEquals(0, firstMissingTurn(emptyList()))
        assertEquals(3, firstMissingTurn(HISTORY.take(3)))
        assertEquals(2, firstMissingTurn(HISTORY.take(2) + HISTORY[4]))
    }

    @Test
    fun mergingKeepsEveryTurnEitherSideKnows() {
        val mine = listOf(Shot(0, 45, 50), Shot(2, 90, 20))
        val theirs = listOf(Shot(1, 30, 40), Shot(2, 10, 10), Shot(3, 60, 30))
        val merged = mergeHistories(mine, theirs)

        assertEquals(listOf(0, 1, 2, 3), merged.map { it.turn })
        assertEquals("the local copy of a shared turn was overwritten", 90, merged[2].angle)
    }

    /**
     * A session that holds [history] and is waiting for the next shot.
     *
     * It has to still be running: answering `RESUME` is the job of the live link pump,
     * so a match that already ended has nothing listening and nothing to say. The local
     * source is a human who never throws, which parks the engine exactly where a real
     * one sits while the other device reconnects.
     */
    private fun CoroutineScope.startResponder(
        link: LoopbackTransport,
        history: List<Shot>,
    ): Job {
        val match = RemoteMatch(link, 0, HumanShotSource(), CONFIG, resumeFrom = history)
        return launch(Dispatchers.Unconfined) { match.run() }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val SHORT_TIMEOUT_MS = 200L
        val CONFIG = MatchConfig(seed = 20260913L, width = G.W_MIN, roundsToWin = 2)

        /** Two shots into the terrain, then an own goal that closes the round, and on. */
        val SCRIPT = listOf(Shot(0, 45, 50), Shot(0, 30, 40), Shot(0, 90, 20))

        /** Five turns of that script: what a match looks like when the link drops. */
        val HISTORY = List(5) { turn -> SCRIPT[turn % SCRIPT.size].let { Shot(turn, it.angle, it.power) } }
    }
}
