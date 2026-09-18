package com.vansid.panda.core.net

import com.vansid.panda.core.G
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agreement the two devices reach before the first throw (§12).
 *
 * Everything the match is reproducible from — seed, width, who starts, how many rounds —
 * is settled here, once, by one side. Get this wrong and determinism cannot save you:
 * two devices running identical code over different inputs produce different matches,
 * and every later check would report the symptom rather than the cause.
 */
class HandshakeTest {
    @Test
    fun bothSidesEndUpWithTheSameConfig() =
        runBlocking {
            val (host, guest) = handshake(hostWidth = 400, guestWidth = 400)

            assertTrue(host is Handshake.Agreed)
            assertTrue(guest is Handshake.Agreed)
            assertEquals(
                (host as Handshake.Agreed).config,
                (guest as Handshake.Agreed).config,
            )
        }

    /**
     * The narrower screen wins.
     *
     * A guest handed a playfield wider than its own would crop it (D-04), and a panda
     * off one device's edge but on-screen for the other is two people playing different
     * matches.
     */
    @Test
    fun theWidthIsTheSmallerOfTheTwoCanvases() =
        runBlocking {
            val (host, guest) = handshake(hostWidth = 460, guestWidth = 360)

            assertEquals(360, (host as Handshake.Agreed).config.width)
            assertEquals(360, (guest as Handshake.Agreed).config.width)
        }

    @Test
    fun theSmallerCanvasWinsFromEitherSide() =
        runBlocking {
            val (host, _) = handshake(hostWidth = 340, guestWidth = 460)
            assertEquals(340, (host as Handshake.Agreed).config.width)
        }

    /** A canvas outside the legal range is pulled into it before anyone agrees to it. */
    @Test
    fun anImpossibleLocalWidthIsClamped() =
        runBlocking {
            val (host, guest) = handshake(hostWidth = 4000, guestWidth = 4000)

            assertEquals(G.W_MAX, (host as Handshake.Agreed).config.width)
            assertEquals(G.W_MAX, (guest as Handshake.Agreed).config.width)
        }

    @Test
    fun eachSideLearnsTheOthersName() =
        runBlocking {
            val (host, guest) = handshake(hostWidth = 400, guestWidth = 400)

            assertEquals("guest-panda", (host as Handshake.Agreed).peerNick)
            assertEquals("host-panda", (guest as Handshake.Agreed).peerNick)
        }

    /** Always starting the host would be a standing advantage; the seed decides instead. */
    @Test
    fun theSeedDecidesWhoThrowsFirst() =
        runBlocking {
            val even = handshake(hostWidth = 400, guestWidth = 400, seed = 2L).first
            val odd = handshake(hostWidth = 400, guestWidth = 400, seed = 3L).first

            assertEquals(0, (even as Handshake.Agreed).config.startingPlayer)
            assertEquals(1, (odd as Handshake.Agreed).config.startingPlayer)
        }

    /**
     * A host asking for an impossible width is refused, not accommodated.
     *
     * Clamping locally would leave the guest on a 320-pixel field and the host on a
     * 500-pixel one — a divergence invented by the code trying to be helpful, and one
     * that would surface later as "the physics disagree".
     */
    @Test
    fun aGuestRefusesAnImpossibleWidthRatherThanClampingIt() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (hostLink, guestLink) = LoopbackTransport.pair()
                val guest = async(Dispatchers.Unconfined) { guestHandshake(guestLink, "guest", 400) }

                hostLink.send(Msg.Hello("rogue", 500))
                hostLink.send(Msg.MatchStart(seed = 1L, width = 500, startingPlayer = 0, roundsToWin = 3))

                val result = guest.await()
                assertTrue("a 500-pixel field was accepted", result is Handshake.Failed)
                assertTrue((result as Handshake.Failed).reason.contains("500"))
            }
        }

    @Test
    fun aGuestRefusesAPlayerThatDoesNotExist() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (hostLink, guestLink) = LoopbackTransport.pair()
                val guest = async(Dispatchers.Unconfined) { guestHandshake(guestLink, "guest", 400) }

                hostLink.send(Msg.Hello("rogue", 400))
                hostLink.send(Msg.MatchStart(seed = 1L, width = 400, startingPlayer = 7, roundsToWin = 3))

                assertTrue(guest.await() is Handshake.Failed)
            }
        }

    @Test
    fun aSilentPeerTimesOutInsteadOfHanging() =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                val (_, guestLink) = LoopbackTransport.pair()
                val result = guestHandshake(guestLink, "guest", 400, timeoutMs = SHORT_TIMEOUT_MS)

                assertTrue(result is Handshake.Failed)
            }
        }

    /** Runs both halves against each other and returns what each side came away with. */
    private suspend fun handshake(
        hostWidth: Int,
        guestWidth: Int,
        seed: Long = SEED,
    ): Pair<Handshake, Handshake> =
        withTimeout(TIMEOUT_MS) {
            val (hostLink, guestLink) = LoopbackTransport.pair()
            var guestResult: Handshake? = null
            val guestJob =
                launch(Dispatchers.Unconfined) {
                    guestResult = guestHandshake(guestLink, "guest-panda", guestWidth)
                }
            val hostResult = hostHandshake(hostLink, "host-panda", hostWidth, MatchProposal(seed))
            guestJob.join()
            hostResult to checkNotNull(guestResult)
        }

    private companion object {
        const val SEED = 20260913L
        const val TIMEOUT_MS = 30_000L
        const val SHORT_TIMEOUT_MS = 200L
    }
}
