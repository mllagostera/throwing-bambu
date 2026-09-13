package dev.bambu.core.net

import dev.bambu.core.G
import dev.bambu.core.MatchEvent
import dev.bambu.core.ScriptedShotSource
import dev.bambu.core.Shot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §15.12: a full match over `LoopbackTransport`, with both sides ending on the same
 * score and no divergences.
 *
 * This is the test that actually proves the design: two independent engines, each
 * simulating every shot from the same seed, exchanging nothing but the shots
 * themselves, and arriving at the same result.
 */
class RemoteMatchTest {
    /** A session that does not end when its match does would hang every caller. */
    @Test
    fun theSessionEndsWithTheMatch() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (linkA, linkB) = LoopbackTransport.pair()
                val host = remoteMatch(linkA, localPlayer = 0)
                val guest = remoteMatch(linkB, localPlayer = 1)
                val guestJob = launch(Dispatchers.Unconfined) { guest.run() }
                host.run()
                guestJob.join()
            }
        }

    @Test
    fun bothSidesFinishWithTheSameScore() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (linkA, linkB) = LoopbackTransport.pair()
                val host = remoteMatch(linkA, localPlayer = 0)
                val guest = remoteMatch(linkB, localPlayer = 1)

                val hostEnd = async(Dispatchers.Unconfined) { endOf(host) }
                val guestEnd = async(Dispatchers.Unconfined) { endOf(guest) }

                launch(Dispatchers.Unconfined) { host.run() }
                launch(Dispatchers.Unconfined) { guest.run() }

                val a = hostEnd.await()
                val b = guestEnd.await()

                assertEquals("the two devices disagree on who won", a.winner, b.winner)
                assertEquals("the two devices disagree on the score", a.scores.toList(), b.scores.toList())
                assertEquals("the host saw a divergence", 0, host.divergences)
                assertEquals("the guest saw a divergence", 0, guest.divergences)
                assertTrue("frames were dropped: ${linkA.rejected}", linkA.rejected.isEmpty())
                assertTrue("frames were dropped: ${linkB.rejected}", linkB.rejected.isEmpty())
            }
        }

    @Test
    fun bothSidesRecordTheSameHistory() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (linkA, linkB) = LoopbackTransport.pair()
                val host = remoteMatch(linkA, localPlayer = 0)
                val guest = remoteMatch(linkB, localPlayer = 1)

                val ends =
                    listOf(
                        async(Dispatchers.Unconfined) { endOf(host) },
                        async(Dispatchers.Unconfined) { endOf(guest) },
                    )
                launch(Dispatchers.Unconfined) { host.run() }
                launch(Dispatchers.Unconfined) { guest.run() }
                ends.forEach { it.await() }

                // The shot history is what a reconnecting peer replays (D-10), so the two
                // sides holding different lists would make reconnection produce a different
                // match.
                assertEquals(host.history.size, guest.history.size)
                assertEquals(host.history.sortedBy { it.turn }, guest.history.sortedBy { it.turn })
            }
        }

    private fun remoteMatch(
        link: LoopbackTransport,
        localPlayer: Int,
    ) = RemoteMatch(
        transport = link,
        localPlayer = localPlayer,
        localSource = ScriptedShotSource(SCRIPT),
        seed = SEED,
        width = G.W_MIN,
        roundsToWin = 2,
    )

    /**
     * `first` rather than `collect`: the engine's event flow never completes, so a
     * collect that only stops caring never returns.
     */
    private suspend fun endOf(match: RemoteMatch): MatchEvent.MatchEnd =
        match.engine.events.first { it is MatchEvent.MatchEnd } as MatchEvent.MatchEnd

    private companion object {
        const val SEED = 20260913L
        const val TIMEOUT_MS = 30_000L

        /** 90° at low power always comes back on the thrower, so each round ends in one shot. */
        val SCRIPT = listOf(Shot(0, 90, 20))
    }
}
