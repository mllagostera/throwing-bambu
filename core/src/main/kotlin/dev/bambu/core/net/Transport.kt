package dev.bambu.core.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow

enum class LinkState { IDLE, ADVERTISING, DISCOVERING, CONNECTING, CONNECTED, LOST }

data class Peer(
    val id: String,
    val nick: String,
)

/**
 * A link to one other device (§12).
 *
 * The interface lives in `core` rather than in the Android module because the protocol
 * is pure logic: frames in, frames out, no radio involved. Keeping it here is what makes
 * a whole match over the wire testable on a plain JVM, and leaves `transport` for the
 * part that genuinely needs Android — Nearby Connections and RFCOMM.
 *
 * [incoming] has a single consumer by design: the match session pumps it and routes each
 * message. Two collectors on the same link would split the stream between them.
 */
interface Transport {
    val incoming: Flow<Msg>
    val state: StateFlow<LinkState>

    suspend fun advertise(nick: String)

    fun discover(): Flow<Peer>

    suspend fun connect(peer: Peer)

    suspend fun send(msg: Msg)

    fun close()
}

/**
 * Two transports wired to each other in memory (§12).
 *
 * Frames go through [Codec] rather than being handed over as objects: a loopback that
 * skipped the encoding would test the match, not the protocol, and the encoding is
 * exactly the part that has to survive a real radio.
 */
class LoopbackTransport private constructor(
    private val outbound: Channel<ByteArray>,
    private val inbound: Channel<ByteArray>,
) : Transport {
    private val _state = MutableStateFlow(LinkState.CONNECTED)

    /** Frames that did not decode; a real link drops them, and tests can assert on them. */
    val rejected = ArrayList<String>()

    override val state: StateFlow<LinkState> = _state.asStateFlow()

    override val incoming: Flow<Msg> =
        inbound.receiveAsFlow().mapNotNull { frame ->
            when (val decoded = Codec.decode(frame)) {
                is Decoded.Ok -> decoded.msg
                // A real link drops what it cannot parse; the reason is kept for tests.
                is Decoded.Rejected -> {
                    rejected += decoded.reason
                    null
                }
            }
        }

    override suspend fun advertise(nick: String) {
        _state.value = LinkState.CONNECTED
    }

    override fun discover(): Flow<Peer> = emptyFlow()

    override suspend fun connect(peer: Peer) {
        _state.value = LinkState.CONNECTED
    }

    override suspend fun send(msg: Msg) {
        outbound.send(Codec.encode(msg))
    }

    override fun close() {
        _state.value = LinkState.IDLE
        outbound.close()
    }

    companion object {
        /** A connected pair: what one sends, the other receives. */
        fun pair(): Pair<LoopbackTransport, LoopbackTransport> {
            val aToB = Channel<ByteArray>(Channel.BUFFERED)
            val bToA = Channel<ByteArray>(Channel.BUFFERED)
            return LoopbackTransport(aToB, bToA) to LoopbackTransport(bToA, aToB)
        }
    }
}
