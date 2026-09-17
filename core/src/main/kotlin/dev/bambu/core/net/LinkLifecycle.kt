package dev.bambu.core.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which side of the pairing this device is playing (§12). */
enum class LinkRole { HOST, GUEST }

/**
 * The connection lifecycle every radio transport shares (§12).
 *
 * Deliberately here and not in `transport/`: this is the part of a radio link that is
 * pure bookkeeping — who we are talking to, what state that puts the link in, and
 * whether a second suitor should be turned away — and none of it needs a radio to be
 * true. Keeping it in `core` means it can be tested on a plain JVM, and both
 * `NearbyTransport` and `RfcommTransport` get the same behaviour instead of two
 * hand-rolled state machines that drift apart.
 *
 * Not thread-safe by itself. Nearby delivers its callbacks on one thread and RFCOMM on
 * one reader thread, so the transports call this from a single place each; if that ever
 * stops being true, confine it rather than adding locks here.
 */
class LinkLifecycle {
    private val _state = MutableStateFlow(LinkState.IDLE)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    /** The endpoint being courted or talked to, once there is one. */
    var endpoint: String? = null
        private set

    /** What to fall back to when a connection attempt fails: the role we were playing. */
    private var role: LinkRole? = null

    fun advertising() {
        role = LinkRole.HOST
        _state.value = LinkState.ADVERTISING
    }

    fun discovering() {
        role = LinkRole.GUEST
        _state.value = LinkState.DISCOVERING
    }

    /**
     * Decides whether an invitation from [endpointId] should be accepted.
     *
     * The strategy is `P2P_POINT_TO_POINT`: exactly one peer, ever. A second device
     * knocking while one is already pending or connected is **refused**, not queued —
     * accepting it would leave two endpoints delivering shots into one match, and the
     * turn filter would silently discard half of them rather than report the mistake.
     *
     * Re-accepts the endpoint already pending: Nearby can deliver the initiation twice
     * when both sides invite each other at once, and refusing our own peer there would
     * drop a connection that was about to work.
     */
    fun requestConnection(endpointId: String): Boolean {
        val current = endpoint
        if (current != null && current != endpointId) return false
        endpoint = endpointId
        _state.value = LinkState.CONNECTING
        return true
    }

    /** The handshake succeeded. Ignored for any endpoint other than the pending one. */
    fun connected(endpointId: String) {
        if (endpoint != endpointId) return
        _state.value = LinkState.CONNECTED
    }

    /**
     * The handshake failed or was rejected.
     *
     * Back to whatever this device was doing before, so a refused invitation leaves the
     * host still advertising and the guest still discovering rather than stranding
     * either in a state no button can leave.
     */
    fun failed(endpointId: String) {
        if (endpoint != endpointId) return
        endpoint = null
        _state.value =
            when (role) {
                LinkRole.HOST -> LinkState.ADVERTISING
                LinkRole.GUEST -> LinkState.DISCOVERING
                null -> LinkState.IDLE
            }
    }

    /**
     * The peer went away.
     *
     * `LOST` rather than `IDLE`: the difference is the whole point of D-10. A lost link
     * had a match in it and can be resumed by replaying the history; an idle one never
     * did. The endpoint is cleared so a reconnection can come from anywhere.
     */
    fun disconnected(endpointId: String) {
        if (endpoint != endpointId) return
        endpoint = null
        _state.value = LinkState.LOST
    }

    /** This device hung up. Deliberate, so nothing to resume. */
    fun closed() {
        endpoint = null
        role = null
        _state.value = LinkState.IDLE
    }
}
