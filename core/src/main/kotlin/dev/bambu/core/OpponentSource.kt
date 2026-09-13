package dev.bambu.core

import kotlinx.coroutines.channels.Channel

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
