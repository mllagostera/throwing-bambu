package dev.bambu.core.net

import dev.bambu.core.Outcome
import dev.bambu.core.Shot

/** Protocol version carried in byte 0 of every frame (§12). */
const val PROTOCOL_VERSION: Byte = 0x01

/** Why a peer hung up. */
enum class ByeReason(
    val code: Int,
) {
    USER_QUIT(0),
    TURN_TIMEOUT(1),
    LINK_LOST(2),
    PROTOCOL_ERROR(3),
    ;

    companion object {
        fun fromCode(code: Int): ByeReason = entries.firstOrNull { it.code == code } ?: PROTOCOL_ERROR
    }
}

/**
 * Everything that travels between two devices (§12).
 *
 * Note what is **not** here: the wind, the scenario and the terrain. All three are
 * derived from the seed on both sides, which removes an entire class of synchronisation
 * bugs — there is nothing to disagree about if nothing is sent.
 */
sealed interface Msg {
    data class Hello(
        val nick: String,
        val width: Int,
        val caps: Int = 0,
    ) : Msg

    data class MatchStart(
        val seed: Long,
        val width: Int,
        val startingPlayer: Int,
        val roundsToWin: Int,
    ) : Msg

    data class ShotMsg(
        val turn: Int,
        val angle: Int,
        val power: Int,
    ) : Msg {
        fun toShot(): Shot = Shot(turn, angle, power)
    }

    /**
     * Verification, not truth (§12): the shooter states where its own simulation landed
     * so the other side can notice a divergence instead of quietly playing a different
     * match.
     */
    data class Result(
        val turn: Int,
        val outcome: OutcomeCode,
        val impactX: Int,
        val impactY: Int,
    ) : Msg

    data object Rematch : Msg

    data class Bye(
        val reason: ByeReason,
    ) : Msg

    data class Ping(
        val nonce: Long,
    ) : Msg

    data class Resume(
        val seed: Long,
        val nextTurn: Int,
    ) : Msg

    /** The shots played so far, from which a reconnecting peer rebuilds the match. */
    data class History(
        val shots: List<Shot>,
    ) : Msg
}

/** Wire representation of an [Outcome]; the coordinates travel separately. */
enum class OutcomeCode(
    val code: Int,
) {
    OFF_SCREEN(0),
    TIME_OUT(1),
    HIT_TERRAIN(2),
    HIT_PANDA_0(3),
    HIT_PANDA_1(4),
    ;

    companion object {
        fun fromCode(code: Int): OutcomeCode? = entries.firstOrNull { it.code == code }

        fun of(outcome: Outcome): OutcomeCode =
            when (outcome) {
                is Outcome.OffScreen -> OFF_SCREEN
                is Outcome.TimeOut -> TIME_OUT
                is Outcome.HitTerrain -> HIT_TERRAIN
                is Outcome.HitPanda -> if (outcome.player == 0) HIT_PANDA_0 else HIT_PANDA_1
            }
    }

    /** Rebuilds the outcome. Coordinates only matter where the flight actually ended. */
    fun toOutcome(
        x: Int,
        y: Int,
    ): Outcome =
        when (this) {
            OFF_SCREEN -> Outcome.OffScreen
            TIME_OUT -> Outcome.TimeOut
            HIT_TERRAIN -> Outcome.HitTerrain(x, y)
            HIT_PANDA_0 -> Outcome.HitPanda(0)
            HIT_PANDA_1 -> Outcome.HitPanda(1)
        }
}
