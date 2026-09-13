package dev.bambu.core.net

import dev.bambu.core.G
import dev.bambu.core.RemoteShotSource
import dev.bambu.core.Shot
import dev.bambu.core.generate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A8: a shot for the wrong turn is discarded, not queued (§12).
 *
 * Queueing it is the subtle version of the bug: the stale shot surfaces one turn later
 * and the match plays something nobody aimed, on both devices, with no error anywhere.
 */
class RemoteShotSourceTest {
    @Test
    fun shotsForOtherTurnsAreDiscarded() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val source = RemoteShotSource()
                val scenario = generate(SEED, G.W_MIN)

                val waiting =
                    async(Dispatchers.Unconfined) {
                        source.nextShot(scenario, me = 1, wind = 0, turn = 4)
                    }

                launch(Dispatchers.Unconfined) {
                    source.deliver(Shot(2, 10, 10)) // a resend of an old turn
                    source.deliver(Shot(9, 20, 20)) // a shot from the future
                    source.deliver(Shot(4, 45, 68)) // the one actually expected
                }

                assertEquals(Shot(4, 45, 68), waiting.await())
                assertEquals("stale shots were queued instead of dropped", 2, source.discarded)
            }
        }

    @Test
    fun theExpectedTurnIsReturnedEvenIfItArrivesFirst() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val source = RemoteShotSource()
                val scenario = generate(SEED, G.W_MIN)
                source.deliver(Shot(0, 30, 40))
                assertEquals(Shot(0, 30, 40), source.nextShot(scenario, me = 1, wind = 0, turn = 0))
                assertEquals(0, source.discarded)
            }
        }

    private companion object {
        const val SEED = 20260913L
        const val TIMEOUT_MS = 10_000L
    }
}
