package com.vansid.panda.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The link lifecycle both radio transports run on (§12).
 *
 * Everything here is what a radio would otherwise have to be present to prove. The
 * transports themselves are thin enough that if these hold, what is left is the Nearby
 * or RFCOMM call itself.
 */
class LinkLifecycleTest {
    @Test
    fun aHostAdvertisesThenConnects() {
        val link = LinkLifecycle()
        assertEquals(LinkState.IDLE, link.state.value)

        link.advertising()
        assertEquals(LinkState.ADVERTISING, link.state.value)

        assertTrue(link.requestConnection("peer-1"))
        assertEquals(LinkState.CONNECTING, link.state.value)

        link.connected("peer-1")
        assertEquals(LinkState.CONNECTED, link.state.value)
        assertEquals("peer-1", link.endpoint)
    }

    @Test
    fun aGuestDiscoversThenConnects() {
        val link = LinkLifecycle()
        link.discovering()
        assertEquals(LinkState.DISCOVERING, link.state.value)

        link.requestConnection("peer-1")
        link.connected("peer-1")
        assertEquals(LinkState.CONNECTED, link.state.value)
    }

    /** `P2P_POINT_TO_POINT` means one peer, ever. */
    @Test
    fun aSecondSuitorIsRefused() {
        val link = LinkLifecycle()
        link.advertising()

        assertTrue(link.requestConnection("peer-1"))
        assertFalse("a second endpoint was accepted", link.requestConnection("peer-2"))

        link.connected("peer-1")
        assertFalse("a second endpoint was accepted while connected", link.requestConnection("peer-2"))
        assertEquals("peer-1", link.endpoint)
        assertEquals(LinkState.CONNECTED, link.state.value)
    }

    /** Both sides inviting at once delivers the initiation twice; the peer is still ours. */
    @Test
    fun theSameSuitorKnockingTwiceIsStillAccepted() {
        val link = LinkLifecycle()
        link.advertising()

        assertTrue(link.requestConnection("peer-1"))
        assertTrue("our own pending peer was refused", link.requestConnection("peer-1"))
    }

    @Test
    fun aRefusedHostGoesBackToAdvertising() {
        val link = LinkLifecycle()
        link.advertising()
        link.requestConnection("peer-1")

        link.failed("peer-1")
        assertEquals(LinkState.ADVERTISING, link.state.value)
        assertNull("a failed attempt kept the endpoint", link.endpoint)

        // And the host is free to try again with somebody else.
        assertTrue(link.requestConnection("peer-2"))
    }

    @Test
    fun aRefusedGuestGoesBackToDiscovering() {
        val link = LinkLifecycle()
        link.discovering()
        link.requestConnection("peer-1")

        link.failed("peer-1")
        assertEquals(LinkState.DISCOVERING, link.state.value)
    }

    /**
     * The distinction D-10 rests on: a link that dropped is LOST, not IDLE.
     *
     * LOST had a match in it and can be resumed by replaying the history; IDLE never did.
     * Collapsing the two would make a dropped match indistinguishable from no match.
     */
    @Test
    fun aDroppedLinkIsLostAndAClosedOneIsIdle() {
        val dropped = LinkLifecycle()
        dropped.advertising()
        dropped.requestConnection("peer-1")
        dropped.connected("peer-1")
        dropped.disconnected("peer-1")
        assertEquals(LinkState.LOST, dropped.state.value)
        assertNull(dropped.endpoint)

        val hungUp = LinkLifecycle()
        hungUp.advertising()
        hungUp.requestConnection("peer-1")
        hungUp.connected("peer-1")
        hungUp.closed()
        assertEquals(LinkState.IDLE, hungUp.state.value)
    }

    /** A lost link can take a reconnection: that is what makes T-46 reachable. */
    @Test
    fun aLostLinkCanBeReconnected() {
        val link = LinkLifecycle()
        link.advertising()
        link.requestConnection("peer-1")
        link.connected("peer-1")
        link.disconnected("peer-1")

        assertTrue(link.requestConnection("peer-1"))
        link.connected("peer-1")
        assertEquals(LinkState.CONNECTED, link.state.value)
    }

    /** Callbacks about somebody else must not move a healthy link. */
    @Test
    fun eventsForAnotherEndpointAreIgnored() {
        val link = LinkLifecycle()
        link.advertising()
        link.requestConnection("peer-1")
        link.connected("peer-1")

        link.connected("peer-2")
        link.failed("peer-2")
        link.disconnected("peer-2")

        assertEquals(LinkState.CONNECTED, link.state.value)
        assertEquals("peer-1", link.endpoint)
    }
}
