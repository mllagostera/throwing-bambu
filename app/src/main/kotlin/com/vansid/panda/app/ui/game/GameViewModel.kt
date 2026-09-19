package com.vansid.panda.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansid.panda.app.ui.bluetooth.BluetoothSession
import com.vansid.panda.core.AiLevel
import com.vansid.panda.core.HumanShotSource
import com.vansid.panda.core.MatchEngine
import com.vansid.panda.core.Shot
import com.vansid.panda.core.net.MatchConfig
import com.vansid.panda.core.net.RemoteMatch
import com.vansid.panda.core.net.Transport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives one match and turns its events into something the UI can draw.
 *
 * The engine lives here, in `viewModelScope`, not in the composition: a rotation or a
 * trip to the background must not restart the match (T-21). The UI only reads
 * [uiState] and calls [submit].
 */
class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val human = HumanShotSource()

    /**
     * The seats a person throws from, by player index.
     *
     * Every [HumanShotSource] is its own rendezvous channel and the engine waits on
     * exactly one of them, so a shot has to reach the seat whose turn it is. A hot-seat
     * match seats two people and needs both entries; against the AI, or across a link,
     * only one seat is ours.
     *
     * [MatchSeats] builds this and the engine's source list together and refuses to
     * produce a seat the engine does not read, so `humanPlayers` cannot offer a Throw
     * button for a seat nothing is listening on.
     */
    private var seats: Map<Int, HumanShotSource> = emptyMap()
    private val animator = MatchAnimator(_uiState, viewModelScope)
    private val events = MatchEvents(_uiState, animator)
    private var engine: MatchEngine? = null
    private var remote: RemoteMatch? = null
    private var job: Job? = null

    var speedMultiplier: Int
        get() = animator.speedMultiplier
        set(value) {
            animator.speedMultiplier = value
        }

    /**
     * Starts the match. Calling it twice is a no-op: the running match wins.
     *
     * [aiLevel] decides the mode: `null` seats two people at the same device, anything
     * else puts the AI in the second seat. Both go through the same engine and the same
     * loop — only where the shots come from changes (§10).
     */
    fun start(
        seed: Long,
        width: Int,
        roundsToWin: Int,
        aiLevel: AiLevel? = null,
    ) {
        if (engine != null) return

        val seating = localSeats(human, aiLevel, seed)
        seats = seating.humanSeats
        _uiState.update { it.copy(humanPlayers = seating.humanPlayers) }

        val created = MatchEngine(seed, width, seating.sources, roundsToWin)
        engine = created
        job = viewModelScope.launch { created.events.collect { events.handle(it) } }
        viewModelScope.launch { created.run() }
    }

    /**
     * Starts a match across a link (§12).
     *
     * The shape is the same as [start] and that is the point: the engine does not know
     * a radio exists, only that one of its two sources happens to be slow. What changes
     * is who owns the engine — [RemoteMatch] does, because it also has to route results
     * and byes — and that only one seat is ours, so only one panda answers the Throw
     * button.
     */
    fun startRemote(
        transport: Transport,
        config: MatchConfig,
        localPlayer: Int,
    ) {
        if (engine != null) return

        val session = RemoteMatch(transport, localPlayer, human, config)
        remote = session
        engine = session.engine
        seats = mapOf(localPlayer to human)
        _uiState.update { it.copy(humanPlayers = seats.keys) }
        job = viewModelScope.launch { session.engine.events.collect { events.handle(it) } }
        viewModelScope.launch { session.run() }
    }

    /**
     * Hangs up when the screen goes for good.
     *
     * A networked match owns a radio, and a radio nobody is listening to still costs
     * battery and still advertises this device to the room. Leaving the game screen has
     * to end the link, not just stop drawing it.
     */
    override fun onCleared() {
        remote?.let { BluetoothSession.end() }
        remote = null
    }

    /** The UI's Throw button. Ignored unless the player is actually aiming. */
    fun submit(
        angle: Int,
        power: Int,
    ) {
        val state = _uiState.value
        if (!state.canAim) return
        // By `currentPlayer`, which is what `canAim` asks about; `Shot.turn` is the
        // match-wide counter and names nobody. Sending to the wrong seat does not fail,
        // it parks on a channel with no receiver and stays there.
        val seat = seats[state.currentPlayer] ?: return
        viewModelScope.launch {
            seat.submit(Shot(state.turn, angle, power))
        }
    }
}
