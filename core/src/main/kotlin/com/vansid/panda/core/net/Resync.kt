package com.vansid.panda.core.net

import com.vansid.panda.core.Shot
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** How long a reconnecting device waits for the peer's `HISTORY` before giving up (§12). */
const val RESYNC_TIMEOUT_MS = 5_000L

/**
 * Asks the peer where the match stands and returns the shots to rebuild it from (D-10).
 *
 * This runs **before** [RemoteMatch] exists, and that is the point: the session owns the
 * single consumer of [Transport.incoming] once it starts pumping, so the one moment a
 * second reader is safe is the moment before. It also means the engine is built with the
 * complete history already in hand, rather than being told about missing turns after it
 * has started replaying the ones it knew about.
 *
 * @param known the shots this device still remembers; the answer only has to fill the gap.
 * @return the merged history, or `null` if the peer did not answer in time — the caller
 *   then has a link but no match to resume, which is a user-visible failure and not
 *   something to paper over with a half-empty history.
 */
suspend fun resyncHistory(
    transport: Transport,
    config: MatchConfig,
    known: List<Shot> = emptyList(),
    timeoutMs: Long = RESYNC_TIMEOUT_MS,
): List<Shot>? {
    val sorted = known.sortedBy { it.turn }
    transport.send(Msg.Resume(config.seed, nextTurn = firstMissingTurn(sorted)))
    val answer =
        try {
            withTimeout(timeoutMs) { transport.incoming.first { it is Msg.History || it is Msg.Bye } }
        } catch (_: TimeoutCancellationException) {
            null
        }
    return if (answer is Msg.History) mergeHistories(sorted, answer.shots) else null
}

/**
 * The first turn not present in [sorted], counting from zero.
 *
 * Deliberately the first *gap* and not the highest turn plus one: a device that holds
 * turns 0, 1 and 4 is missing 2 and 3, and asking from turn 5 would leave the hole there
 * forever — the replay would then stop at turn 2 waiting for a shot nobody will send.
 */
internal fun firstMissingTurn(sorted: List<Shot>): Int {
    var next = 0
    for (shot in sorted) {
        if (shot.turn > next) break
        if (shot.turn == next) next++
    }
    return next
}

/** Keeps every turn either side knows about; where both know one, the local copy wins. */
internal fun mergeHistories(
    mine: List<Shot>,
    theirs: List<Shot>,
): List<Shot> {
    val known = mine.mapTo(HashSet()) { it.turn }
    return (mine + theirs.filterNot { it.turn in known }).sortedBy { it.turn }
}
