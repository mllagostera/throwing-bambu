package com.vansid.panda.app.ui.bluetooth

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansid.panda.app.R
import com.vansid.panda.core.net.Handshake
import com.vansid.panda.core.net.LinkState
import com.vansid.panda.core.net.MatchProposal
import com.vansid.panda.core.net.Peer
import com.vansid.panda.core.net.Transport
import com.vansid.panda.core.net.guestHandshake
import com.vansid.panda.core.net.hostHandshake
import com.vansid.panda.transport.NearbyTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Which side of the pairing the player chose. */
enum class PairingRole { HOST, GUEST }

data class PairingUiState(
    val role: PairingRole? = null,
    val link: LinkState = LinkState.IDLE,
    val peers: List<Peer> = emptyList(),
    val peerNick: String? = null,
    @StringRes val error: Int? = null,
    val ready: Boolean = false,
)

/**
 * Drives the pairing: advertise or discover, connect, shake hands (§12).
 *
 * The seed is drawn **once, here, by the host** and travels in `MATCH_START`. Everything
 * else about the match — the scenario, the terrain, the wind — is derived from it on
 * both devices, so this single number is the whole of what has to be agreed.
 */
class PairingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var transport: Transport? = null

    fun host(
        context: Context,
        nick: String,
        localWidth: Int,
    ) = begin(context, nick, PairingRole.HOST) { link ->
        link.advertise(nick)
        link.state.first { it == LinkState.CONNECTED }
        hostHandshake(link, nick, localWidth, MatchProposal(seed = Random.nextLong()))
    }

    fun joinAs(
        context: Context,
        nick: String,
    ) {
        val link = openLink(context, nick) ?: return
        _uiState.update { it.copy(role = PairingRole.GUEST, error = null) }
        viewModelScope.launch {
            link.discover().collect { peer ->
                // Nearby re-announces an endpoint it has already reported; keeping the
                // list keyed by id stops the same panda appearing three times.
                _uiState.update { state ->
                    if (state.peers.any { it.id == peer.id }) state else state.copy(peers = state.peers + peer)
                }
            }
        }
        watchLink(link)
    }

    fun connectTo(
        peer: Peer,
        nick: String,
        localWidth: Int,
    ) {
        val link = transport ?: return
        viewModelScope.launch {
            runCatching {
                link.connect(peer)
                link.state.first { it == LinkState.CONNECTED }
                guestHandshake(link, nick, localWidth)
            }.fold(onSuccess = ::finish, onFailure = { fail(R.string.pair_error_link) })
        }
    }

    /** Hangs up and forgets everything. The player pressed Cancel, or left the screen. */
    fun cancel() {
        transport?.close()
        transport = null
        BluetoothSession.clear()
        _uiState.value = PairingUiState()
    }

    override fun onCleared() {
        // A pairing screen that is gone must not leave a radio advertising behind it.
        // The session survives on purpose when a match was agreed: it is the link the
        // game screen is about to play over.
        if (!_uiState.value.ready) cancel()
    }

    private fun begin(
        context: Context,
        nick: String,
        role: PairingRole,
        block: suspend (Transport) -> Handshake,
    ) {
        val link = openLink(context, nick) ?: return
        _uiState.update { it.copy(role = role, error = null) }
        watchLink(link)
        viewModelScope.launch {
            runCatching { block(link) }
                .fold(onSuccess = ::finish, onFailure = { fail(R.string.pair_error_link) })
        }
    }

    private fun openLink(
        context: Context,
        nick: String,
    ): Transport? {
        transport?.let { return it }
        val created = NearbyTransport(context, nick)
        transport = created
        return created
    }

    private fun watchLink(link: Transport) {
        viewModelScope.launch {
            link.state.collect { state ->
                _uiState.update { it.copy(link = state) }
            }
        }
    }

    private fun finish(result: Handshake) {
        // A failure's reason is deliberately not shown: it names a protocol field, which
        // tells a player nothing they can act on. It belongs in the log, not on screen.
        val agreed = result as? Handshake.Agreed ?: return fail(R.string.pair_error_handshake)
        val link = transport ?: return
        BluetoothSession.begin(
            link = link,
            config = agreed.config,
            localPlayer = if (_uiState.value.role == PairingRole.HOST) 0 else 1,
        )
        _uiState.update { it.copy(peerNick = agreed.peerNick, ready = true) }
    }

    private fun fail(
        @StringRes message: Int,
    ) {
        transport?.close()
        transport = null
        _uiState.update { it.copy(error = message, role = null, peers = emptyList(), link = LinkState.IDLE) }
    }
}
