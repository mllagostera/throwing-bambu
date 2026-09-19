package com.vansid.panda.app.ui.game

import com.vansid.panda.core.AiLevel
import com.vansid.panda.core.AiOpponent
import com.vansid.panda.core.AiShotSource
import com.vansid.panda.core.HumanShotSource
import com.vansid.panda.core.Rng
import com.vansid.panda.core.ShotSource

/**
 * Who sits where for one match.
 *
 * [sources] is what the engine reads, in player order. [humanSeats] is the subset a
 * person throws from, and every value in it must be the very object at that index in
 * [sources] — not an equal one, the same one. That identity is the whole reason this
 * type exists.
 *
 * It is enforced in [init] rather than trusted, because the two lists were once built
 * separately from the same condition and drifted: seat 1 held a [HumanShotSource] the
 * engine never read from, so the second player's Throw button fed a rendezvous channel
 * with no receiver. Nothing failed — the shot simply parked there and the match stopped.
 */
internal class MatchSeats(
    val sources: List<ShotSource>,
    val humanSeats: Map<Int, HumanShotSource>,
) {
    init {
        require(humanSeats.all { (player, seat) -> sources.getOrNull(player) === seat }) {
            "a human seat must be the source the engine reads for that player"
        }
    }

    /** The seats the UI may offer a Throw button for. */
    val humanPlayers: Set<Int> get() = humanSeats.keys
}

/**
 * Seats a match played on this device. Seat 0 is always [human]; [aiLevel] decides seat
 * 1 — `null` puts a second person there, anything else the computer (§10).
 */
internal fun localSeats(
    human: HumanShotSource,
    aiLevel: AiLevel?,
    seed: Long,
): MatchSeats {
    val opponent: ShotSource =
        if (aiLevel == null) {
            HumanShotSource()
        } else {
            AiShotSource(AiOpponent(aiLevel, Rng(seed)))
        }
    return MatchSeats(
        sources = listOf(human, opponent),
        humanSeats =
            buildMap {
                put(0, human)
                if (opponent is HumanShotSource) put(1, opponent)
            },
    )
}
