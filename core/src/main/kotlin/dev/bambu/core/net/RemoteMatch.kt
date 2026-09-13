package dev.bambu.core.net

import dev.bambu.core.MatchEngine
import dev.bambu.core.MatchEvent
import dev.bambu.core.RemoteShotSource
import dev.bambu.core.ReplayingShotSource
import dev.bambu.core.SendingShotSource
import dev.bambu.core.Shot
import dev.bambu.core.ShotSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far two simulations may disagree before it counts as a divergence (§12). */
private const val DIVERGENCE_TOLERANCE_PX = 2

/**
 * One match played across a link (§12).
 *
 * Both devices run the **same engine over the same seed**; only the shots travel. The
 * scenario, the wind and the terrain are derived on each side, so there is nothing to
 * keep in sync and nothing to disagree about.
 *
 * This class owns the single consumer of [Transport.incoming] and routes what arrives:
 * shots to the remote source, results to verification, byes to the exit.
 */
class RemoteMatch(
    private val transport: Transport,
    private val localPlayer: Int,
    localSource: ShotSource,
    val config: MatchConfig,
    /**
     * Shots already played, when resuming after a dropped link (D-10). The engine
     * replays them before asking anyone for a new one, which rebuilds the terrain, the
     * score and the turn without transmitting any of the three.
     */
    private val resumeFrom: List<Shot> = emptyList(),
) {
    private val remote = RemoteShotSource()

    /** Shots played so far, in order: what a reconnecting peer needs (§12, D-10). */
    private val _history = ArrayList<Shot>(resumeFrom)
    val history: List<Shot> get() = _history

    /** Turns still being replayed; the UI skips their animations while this is above zero. */
    val replaysRemaining: Int get() {
        val caughtUpTo = replayed.values.maxOfOrNull { it.caughtUpTo } ?: return 0
        return resumeFrom.count { it.turn >= caughtUpTo }
    }

    private val replayed = mutableMapOf<Int, ReplayingShotSource>()

    /** Turns that came from [resumeFrom]: already in the history, already reported once. */
    private val resumedTurns = resumeFrom.mapTo(HashSet()) { it.turn }

    /** Impacts this device computed, by turn, kept until the peer's RESULT arrives. */
    private val mine = HashMap<Int, Msg.Result>()
    private val theirs = HashMap<Int, Msg.Result>()

    /**
     * How many times the two devices computed a materially different impact.
     *
     * Above zero this is a determinism bug, not noise (§17.1): both sides ran the same
     * code over the same inputs, so they cannot legitimately disagree.
     */
    var divergences: Int = 0
        private set

    var closedBy: ByeReason? = null
        private set

    val engine =
        MatchEngine(
            seed = config.seed,
            width = config.width,
            sources =
                List(2) { player ->
                    val live =
                        if (player == localPlayer) SendingShotSource(localSource, transport) else remote
                    if (resumeFrom.isEmpty()) {
                        live
                    } else {
                        ReplayingShotSource(resumeFrom, live).also { replayed[player] = it }
                    }
                },
            roundsToWin = config.roundsToWin,
            startingPlayer = config.startingPlayer,
        )

    /**
     * Runs until the match ends or the peer says goodbye.
     *
     * Both helpers start **undispatched**: they run far enough to subscribe before this
     * call suspends. The engine holds its first event only until *someone* is listening,
     * and a UI collector counts — so a plainly dispatched `launch` here would let the
     * first shots be emitted while [watchEngine] is still queued, and they would be
     * missing from the history a reconnecting peer replays.
     *
     * They are cancelled once the engine is done. Both collect streams that never
     * complete on their own — a channel nobody closes, a `SharedFlow` — so without this
     * the session would outlive the match it was created for and every caller waiting on
     * it would hang.
     */
    suspend fun run() =
        coroutineScope {
            val pump = launch(start = CoroutineStart.UNDISPATCHED) { pumpLink() }
            val watch = launch(start = CoroutineStart.UNDISPATCHED) { watchEngine() }
            engine.run()
            pump.cancel()
            watch.cancel()
        }

    private suspend fun pumpLink() {
        transport.incoming.collect { msg ->
            when (msg) {
                is Msg.ShotMsg -> {
                    _history += msg.toShot()
                    remote.deliver(msg.toShot())
                }

                is Msg.Result -> verify(msg)
                is Msg.Bye -> closedBy = msg.reason

                // A peer that reconnected asks where the match stands; the answer is the
                // shot history, from which it rebuilds everything else itself.
                is Msg.Resume -> answerResume(msg)

                is Msg.History -> adoptHistory(msg.shots)
                is Msg.Ping -> transport.send(Msg.Ping(msg.nonce))
                else -> Unit
            }
        }
    }

    /**
     * Publishes what this device computed for its own shots, and records the rest.
     *
     * The shooter is the one that sends `RESULT`: it is the only side whose simulation
     * the other cannot check without it.
     */
    private suspend fun watchEngine() {
        engine.events.collect { event ->
            if (event !is MatchEvent.ShotFired) return@collect

            // A replayed turn is not news: it is already in the history and its RESULT was
            // sent before the link dropped. Re-announcing it would duplicate both.
            if (event.shot.turn in resumedTurns) return@collect
            if (event.player == localPlayer) _history += event.shot

            val result =
                Msg.Result(
                    turn = event.shot.turn,
                    outcome = OutcomeCode.of(event.result.outcome),
                    impactX = event.result.impactX,
                    impactY = event.result.impactY,
                )
            if (event.player == localPlayer) {
                transport.send(result)
            } else {
                mine[event.shot.turn] = result
                theirs.remove(event.shot.turn)?.let { compare(mine = result, theirs = it) }
            }
        }
    }

    /**
     * Answers a reconnecting peer with the turns it is missing.
     *
     * The seed is checked first. A peer asking about a different match is not a peer to
     * be helpful to: handing it this match's shots would have it replay throws that were
     * never aimed at its terrain, and it would look like a determinism bug rather than
     * like the mismatch it is.
     */
    private suspend fun answerResume(msg: Msg.Resume) {
        if (msg.seed != config.seed) {
            transport.send(Msg.Bye(ByeReason.PROTOCOL_ERROR))
            return
        }
        transport.send(Msg.History(_history.filter { it.turn >= msg.nextTurn }.sortedBy { it.turn }))
    }

    /**
     * Keeps the longer history of the two.
     *
     * The peer may know about turns this device never saw — its own shot was sent and
     * then the link dropped — and this device may know about turns the peer missed.
     * Merging by turn rather than replacing keeps whichever is more complete.
     */
    private fun adoptHistory(shots: List<Shot>) {
        val known = _history.mapTo(HashSet()) { it.turn }
        _history += shots.filterNot { it.turn in known }
        _history.sortBy { it.turn }
    }

    private fun verify(peerResult: Msg.Result) {
        val ours = mine.remove(peerResult.turn)
        if (ours == null) {
            // The peer got there first; compare once this side has simulated the turn.
            theirs[peerResult.turn] = peerResult
        } else {
            compare(mine = ours, theirs = peerResult)
        }
    }

    private fun compare(
        mine: Msg.Result,
        theirs: Msg.Result,
    ) {
        val sameOutcome = mine.outcome == theirs.outcome
        val closeEnough =
            abs(mine.impactX - theirs.impactX) <= DIVERGENCE_TOLERANCE_PX &&
                abs(mine.impactY - theirs.impactY) <= DIVERGENCE_TOLERANCE_PX
        if (!sameOutcome || !closeEnough) divergences++
    }
}
