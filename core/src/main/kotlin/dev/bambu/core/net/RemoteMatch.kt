package dev.bambu.core.net

import dev.bambu.core.MatchEngine
import dev.bambu.core.MatchEvent
import dev.bambu.core.RemoteShotSource
import dev.bambu.core.SendingShotSource
import dev.bambu.core.Shot
import dev.bambu.core.ShotSource
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
    val seed: Long,
    val width: Int,
    val roundsToWin: Int,
    startingPlayer: Int = 0,
) {
    private val remote = RemoteShotSource()

    /** Shots played so far, in order: what a reconnecting peer needs (§12, D-10). */
    private val _history = ArrayList<Shot>()
    val history: List<Shot> get() = _history

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
            seed = seed,
            width = width,
            sources =
                List(2) { player ->
                    if (player == localPlayer) SendingShotSource(localSource, transport) else remote
                },
            roundsToWin = roundsToWin,
            startingPlayer = startingPlayer,
        )

    /**
     * Runs until the match ends or the peer says goodbye.
     *
     * The two helpers are cancelled once the engine is done. They both collect streams
     * that never complete on their own — a channel nobody closes, a `SharedFlow` — so
     * without this the session would outlive the match it was created for and every
     * caller waiting on it would hang.
     */
    suspend fun run() =
        coroutineScope {
            val pump = launch { pumpLink() }
            val watch = launch { watchEngine() }
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
