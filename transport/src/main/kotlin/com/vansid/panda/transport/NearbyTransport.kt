package com.vansid.panda.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.vansid.panda.core.net.Codec
import com.vansid.panda.core.net.Decoded
import com.vansid.panda.core.net.LinkLifecycle
import com.vansid.panda.core.net.LinkState
import com.vansid.panda.core.net.Msg
import com.vansid.panda.core.net.Peer
import com.vansid.panda.core.net.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.tasks.await

/** Identifies this game to Nearby; only devices using the same string can find each other. */
private const val SERVICE_ID = "com.vansid.panda.throwingbambu"

/** One peer, ever — which is exactly what a two-player match wants (§12). */
private val STRATEGY = Strategy.P2P_POINT_TO_POINT

/**
 * A link over Nearby Connections (§12).
 *
 * Thin on purpose. Everything that can be decided without a radio — who we are talking
 * to, what state that puts the link in, whether a second suitor gets turned away — lives
 * in [LinkLifecycle], in `core`, where it is tested on a plain JVM. What is left here is
 * the part only a device can prove: the Nearby calls themselves and the threading around
 * their callbacks.
 *
 * @param nick how this device introduces itself. [advertise] overrides it; a guest never
 *   calls [advertise], so without this the host's peer list would have nothing to show.
 */
class NearbyTransport(
    context: Context,
    nick: String,
    private val serviceId: String = SERVICE_ID,
) : Transport {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val lifecycle = LinkLifecycle()

    /**
     * Frames as they arrive, before decoding.
     *
     * Buffered rather than rendezvous: Nearby delivers on its own thread and cannot be
     * made to wait, so a frame that arrived while the match was busy animating has to go
     * somewhere. The turn filter in `RemoteShotSource` is what stops a late one being
     * played, not back-pressure here.
     */
    private val frames = Channel<ByteArray>(Channel.BUFFERED)

    private var localNick = nick

    /** Frames that did not decode. A real link drops them; the reason is kept for triage. */
    val rejected = ArrayList<String>()

    override val state: StateFlow<LinkState> = lifecycle.state

    override val incoming: Flow<Msg> =
        frames.receiveAsFlow().mapNotNull { frame ->
            when (val decoded = Codec.decode(frame)) {
                is Decoded.Ok -> decoded.msg
                is Decoded.Rejected -> {
                    rejected += decoded.reason
                    null
                }
            }
        }

    override suspend fun advertise(nick: String) {
        localNick = nick
        lifecycle.advertising()
        client
            .startAdvertising(
                nick,
                serviceId,
                connectionCallback,
                AdvertisingOptions.Builder().setStrategy(STRATEGY).build(),
            ).await()
    }

    /**
     * Discovery runs for as long as the flow is collected, and stops when it is not.
     *
     * Scanning is expensive in battery and in radio time, so tying it to the collector's
     * lifetime means leaving the pairing screen actually stops the scan — rather than
     * leaving it running behind a match, which is the classic way to make a Nearby app
     * feel broken on the second try.
     */
    override fun discover(): Flow<Peer> =
        callbackFlow {
            lifecycle.discovering()
            val callback =
                object : EndpointDiscoveryCallback() {
                    override fun onEndpointFound(
                        endpointId: String,
                        info: DiscoveredEndpointInfo,
                    ) {
                        trySend(Peer(endpointId, info.endpointName))
                    }

                    override fun onEndpointLost(endpointId: String) = Unit
                }
            client
                .startDiscovery(
                    serviceId,
                    callback,
                    DiscoveryOptions.Builder().setStrategy(STRATEGY).build(),
                ).await()
            awaitClose { client.stopDiscovery() }
        }

    override suspend fun connect(peer: Peer) {
        client.requestConnection(localNick, peer.id, connectionCallback).await()
    }

    override suspend fun send(msg: Msg) {
        val endpoint = lifecycle.endpoint ?: error("send() with no connected peer")
        client.sendPayload(endpoint, Payload.fromBytes(Codec.encode(msg))).await()
    }

    override fun close() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        lifecycle.closed()
        frames.close()
    }

    private val connectionCallback =
        object : ConnectionLifecycleCallback() {
            /**
             * Both sides auto-accept. There is no code to compare because there is
             * nothing to protect: the whole conversation is shots and turn numbers, and
             * a wrong peer is caught by the seed check in `RESUME` rather than by making
             * two people read digits to each other before a game of artillery.
             */
            override fun onConnectionInitiated(
                endpointId: String,
                info: ConnectionInfo,
            ) {
                if (lifecycle.requestConnection(endpointId)) {
                    client.acceptConnection(endpointId, payloadCallback)
                } else {
                    client.rejectConnection(endpointId)
                }
            }

            override fun onConnectionResult(
                endpointId: String,
                result: ConnectionResolution,
            ) {
                if (result.status.isSuccess) {
                    lifecycle.connected(endpointId)
                    // One peer is all this match wants, and scanning past that point
                    // only costs battery and confuses the other devices in the room.
                    client.stopAdvertising()
                    client.stopDiscovery()
                } else {
                    lifecycle.failed(endpointId)
                }
            }

            override fun onDisconnected(endpointId: String) {
                lifecycle.disconnected(endpointId)
            }
        }

    private val payloadCallback =
        object : PayloadCallback() {
            override fun onPayloadReceived(
                endpointId: String,
                payload: Payload,
            ) {
                val bytes = payload.asBytes() ?: return
                frames.trySend(bytes)
            }

            /** Every frame here is a handful of bytes; there is no transfer to follow. */
            override fun onPayloadTransferUpdate(
                endpointId: String,
                update: PayloadTransferUpdate,
            ) = Unit
        }
}
