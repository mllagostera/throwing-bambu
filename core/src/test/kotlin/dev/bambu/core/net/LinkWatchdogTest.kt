package dev.bambu.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The keep-alive rules of §12, checked over an injected clock instead of real waiting:
 * "ping every 10 s, give up after 3 unanswered, abandon a turn after 90 s" would
 * otherwise take minutes per case.
 */
class LinkWatchdogTest {
    @Test
    fun aQuietLinkGetsPingedAndThenDeclaredLost() {
        val watchdog = LinkWatchdog()
        watchdog.start(0)

        assertNull("pinged while the link was still fresh", watchdog.tick(9_000))
        assertEquals(LinkAction.SendPing, watchdog.tick(10_000))
        assertEquals(LinkAction.SendPing, watchdog.tick(20_000))
        assertEquals(LinkAction.SendPing, watchdog.tick(30_000))
        assertEquals("three unanswered pings should end the link", LinkAction.DeclareLost, watchdog.tick(40_000))
    }

    @Test
    fun anyMessageKeepsTheLinkAlive() {
        val watchdog = LinkWatchdog()
        watchdog.start(0)
        assertEquals(LinkAction.SendPing, watchdog.tick(10_000))

        watchdog.onMessage(11_000)
        assertNull("a live link was pinged again", watchdog.tick(15_000))
        assertEquals(LinkAction.SendPing, watchdog.tick(21_000))
    }

    @Test
    fun aSilentTurnTimesOut() {
        val watchdog = LinkWatchdog()
        watchdog.start(0)
        watchdog.onTurnStart(0)
        // Keep the link itself alive, so only the turn clock can fire.
        for (t in 10_000L..89_000L step 5_000) watchdog.onMessage(t)

        assertNull(watchdog.tick(89_500))
        assertEquals(LinkAction.TurnTimedOut, watchdog.tick(90_000))
    }

    @Test
    fun theTurnClockRestartsWithEachTurn() {
        val watchdog = LinkWatchdog()
        watchdog.start(0)
        watchdog.onTurnStart(80_000)
        // Keep the link itself alive so only the turn clock is under test.
        for (t in 80_000L..169_000L step 5_000) watchdog.onMessage(t)

        assertNull("the previous turn's clock leaked into this one", watchdog.tick(120_000))
        assertEquals(LinkAction.TurnTimedOut, watchdog.tick(170_000))
    }

    @Test
    fun theCountdownIsReadable() {
        val watchdog = LinkWatchdog()
        watchdog.start(0)
        watchdog.onTurnStart(0)
        // The UI shows a countdown from 75 s (§12), so this has to be meaningful.
        assertEquals(90, watchdog.secondsLeftInTurn(0))
        assertEquals(15, watchdog.secondsLeftInTurn(75_000))
        assertEquals(0, watchdog.secondsLeftInTurn(95_000))
    }
}
