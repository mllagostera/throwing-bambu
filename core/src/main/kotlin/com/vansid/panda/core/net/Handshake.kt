package com.vansid.panda.core.net

import com.vansid.panda.core.G
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** How long either side waits for the other to answer before giving up (§12). */
const val HANDSHAKE_TIMEOUT_MS = 10_000L

/**
 * What the host brings to the table: everything it decides on its own.
 *
 * One value rather than three arguments because they travel together and mean one
 * thing — "this is the match I propose" — and because the width, the only part that is
 * negotiated, then stays visibly separate from the parts that are not.
 *
 * @param startingPlayer defaults to a coin flip taken from the seed. Always starting the
 *   host would be a standing advantage, and the seed is already shared, so the flip costs
 *   nothing and needs no extra message.
 */
data class MatchProposal(
    val seed: Long,
    val roundsToWin: Int = DEFAULT_ROUNDS_TO_WIN,
    val startingPlayer: Int = (seed and 1L).toInt(),
)

/** What the two devices agreed on — or why they could not (§12). */
sealed interface Handshake {
    data class Agreed(
        val config: MatchConfig,
        val peerNick: String,
    ) : Handshake

    data class Failed(
        val reason: String,
    ) : Handshake
}

/**
 * The host's half of the handshake (§12).
 *
 * **The host is the authority.** It generates the seed, decides the width and decides
 * who throws first, and the guest takes all three verbatim. There is no negotiation to
 * get wrong because there is no negotiation: one side decides, the other obeys, and the
 * two simulations start from identical inputs.
 *
 * The width is the *smaller* of the two logical canvases. A guest handed a playfield
 * wider than its screen would crop it (D-04), and cropping is not a cosmetic problem
 * here — a panda off the edge of one device and on-screen for the other is two players
 * looking at different matches.
 */
suspend fun hostHandshake(
    transport: Transport,
    nick: String,
    localWidth: Int,
    proposal: MatchProposal,
    timeoutMs: Long = HANDSHAKE_TIMEOUT_MS,
): Handshake {
    transport.send(Msg.Hello(nick, localWidth))
    val theirs = awaitHello(transport, timeoutMs) ?: return Handshake.Failed("no HELLO from the peer")

    val width = minOf(localWidth, theirs.width).coerceIn(G.W_MIN, G.W_MAX)
    val config = MatchConfig(proposal.seed, width, proposal.roundsToWin, proposal.startingPlayer)
    transport.send(Msg.MatchStart(config.seed, config.width, config.startingPlayer, config.roundsToWin))
    return Handshake.Agreed(config, theirs.nick)
}

/**
 * The guest's half (§12).
 *
 * Note what it does **not** do with a width outside the legal range: it refuses rather
 * than clamping. Clamping would leave the guest playing a 320-pixel field while the host
 * played a 500-pixel one, and every shot would land somewhere else on each screen — a
 * divergence invented by the code that was trying to be helpful.
 */
suspend fun guestHandshake(
    transport: Transport,
    nick: String,
    localWidth: Int,
    timeoutMs: Long = HANDSHAKE_TIMEOUT_MS,
): Handshake {
    transport.send(Msg.Hello(nick, localWidth))
    val theirs = awaitHello(transport, timeoutMs)
    val start = theirs?.let { await<Msg.MatchStart>(transport, timeoutMs) }
    return when {
        theirs == null -> Handshake.Failed("no HELLO from the peer")
        start == null -> Handshake.Failed("no MATCH_START from the host")
        start.width !in G.W_MIN..G.W_MAX ->
            Handshake.Failed("the host asked for an impossible width: ${start.width}")

        start.startingPlayer !in 0..1 ->
            Handshake.Failed("the host named a player that does not exist: ${start.startingPlayer}")

        else ->
            Handshake.Agreed(
                MatchConfig(start.seed, start.width, start.roundsToWin, start.startingPlayer),
                theirs.nick,
            )
    }
}

private suspend fun awaitHello(
    transport: Transport,
    timeoutMs: Long,
): Msg.Hello? = await<Msg.Hello>(transport, timeoutMs)

private suspend inline fun <reified T : Msg> await(
    transport: Transport,
    timeoutMs: Long,
): T? =
    try {
        withTimeout(timeoutMs) { transport.incoming.first { it is T } as T }
    } catch (_: TimeoutCancellationException) {
        null
    }
