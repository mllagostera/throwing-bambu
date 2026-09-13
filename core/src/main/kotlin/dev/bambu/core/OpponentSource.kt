package dev.bambu.core

import dev.bambu.core.net.Msg
import dev.bambu.core.net.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Where a shot comes from (§10).
 *
 * This is the piece that unifies the three game modes. [MatchEngine] does not know
 * which implementation it is facing: if you end up writing three match loops, the
 * design has broken.
 */
interface ShotSource {
    suspend fun nextShot(
        scenario: Scenario,
        me: Int,
        wind: Int,
        turn: Int,
    ): Shot
}

/**
 * A local player. Suspends until the UI pushes a shot through [submit].
 *
 * The channel is rendezvous on purpose: a shot typed while it is not this player's turn
 * has nowhere to sit, so it cannot surface one turn later as a phantom throw.
 */
class HumanShotSource : ShotSource {
    private val shots = Channel<Shot>(Channel.RENDEZVOUS)

    override suspend fun nextShot(
        scenario: Scenario,
        me: Int,
        wind: Int,
        turn: Int,
    ): Shot = shots.receive()

    /** Called from the UI when the player presses Throw. Suspends until the engine takes it. */
    suspend fun submit(shot: Shot) = shots.send(shot)
}

/**
 * A fixed script of shots, for tests and for replaying a match.
 *
 * It is the deterministic source test §15.11 needs: same script, same event sequence.
 */
class ScriptedShotSource(
    private val shots: List<Shot>,
) : ShotSource {
    private var index = 0

    override suspend fun nextShot(
        scenario: Scenario,
        me: Int,
        wind: Int,
        turn: Int,
    ): Shot {
        val shot = shots[index % shots.size]
        index++
        return Shot(turn, shot.angle, shot.power)
    }
}

/** Thinking time bounds, so a turn reads as a turn and not as a twitch (§9). */
private const val THINK_MIN_MS = 600L
private const val THINK_SPREAD_MS = 600L

/** Any odd number coprime with the spread; it only has to make consecutive turns differ. */
private const val THINK_STRIDE = 173L

/**
 * The computer player.
 *
 * The search runs on `Dispatchers.Default` — it is a few hundred simulations, cheap but
 * not free, and it has no business on the main thread — followed by an artificial pause.
 * Without the pause the AI answers instantly and the round is over before a human has
 * registered whose turn it was.
 */
class AiShotSource(
    private val opponent: AiOpponent,
    private val thinkingDelayMs: (Int) -> Long = ::defaultThinkingDelay,
) : ShotSource {
    override suspend fun nextShot(
        scenario: Scenario,
        me: Int,
        wind: Int,
        turn: Int,
    ): Shot {
        val shot = withContext(Dispatchers.Default) { opponent.chooseShot(scenario, me, wind, turn) }
        delay(thinkingDelayMs(turn))
        return shot
    }
}

/**
 * Derived from the turn rather than drawn at random: the pause varies between turns
 * without touching the RNG the shots come from, which keeps a match reproducible.
 */
fun defaultThinkingDelay(turn: Int): Long = THINK_MIN_MS + (turn * THINK_STRIDE) % THINK_SPREAD_MS

/**
 * The other device's player.
 *
 * It does not read the link itself: the match session pumps the transport and hands
 * shots over with [deliver]. One consumer for the link, one place that knows the
 * protocol.
 *
 * A shot whose turn is not the one being waited for is **discarded, not queued** (§12).
 * Queueing it would let a resend surface a turn later and play a shot nobody aimed.
 */
class RemoteShotSource : ShotSource {
    private val shots = Channel<Shot>(Channel.BUFFERED)

    /** Number of shots dropped for arriving with an unexpected turn. */
    var discarded: Int = 0
        private set

    suspend fun deliver(shot: Shot) = shots.send(shot)

    override suspend fun nextShot(
        scenario: Scenario,
        me: Int,
        wind: Int,
        turn: Int,
    ): Shot {
        while (true) {
            val shot = shots.receive()
            if (shot.turn == turn) return shot
            discarded++
        }
    }
}

/**
 * Wraps a local source so every shot it produces is also sent to the other device.
 *
 * The engine stays unaware that a link exists: it asks for a shot and gets one (§10).
 */
class SendingShotSource(
    private val local: ShotSource,
    private val transport: Transport,
) : ShotSource {
    override suspend fun nextShot(
        scenario: Scenario,
        me: Int,
        wind: Int,
        turn: Int,
    ): Shot {
        val shot = local.nextShot(scenario, me, wind, turn)
        transport.send(Msg.ShotMsg(shot.turn, shot.angle, shot.power))
        return shot
    }
}
