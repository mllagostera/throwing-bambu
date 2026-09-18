package com.vansid.panda.core.net

private const val MS_PER_SECOND = 1000

/** What the link should do next. */
sealed interface LinkAction {
    /** Nothing has been heard for a while: prod the peer. */
    data object SendPing : LinkAction

    /** Enough pings went unanswered; the link is gone (§12). */
    data object DeclareLost : LinkAction

    /** The player whose turn it is has gone quiet for too long. */
    data object TurnTimedOut : LinkAction
}

/**
 * Decides when a quiet link is a dead link (§12).
 *
 * A pure state machine over an injected clock rather than a coroutine full of delays:
 * the rules are "ping every 10 s, give up after 3 unanswered, abandon a turn after 90 s",
 * and testing those with real waits would mean a two-minute test suite that still only
 * covers the happy path.
 */
class LinkWatchdog(
    private val pingIntervalMs: Long = 10_000,
    private val maxMissedPings: Int = 3,
    private val turnTimeoutMs: Long = 90_000,
) {
    private var lastHeardMs = 0L
    private var lastPingMs = 0L
    private var missedPings = 0
    private var turnStartedMs = 0L
    private var lost = false

    fun start(nowMs: Long) {
        lastHeardMs = nowMs
        lastPingMs = nowMs
        turnStartedMs = nowMs
        missedPings = 0
        lost = false
    }

    /** Anything at all from the peer counts as a sign of life. */
    fun onMessage(nowMs: Long) {
        lastHeardMs = nowMs
        missedPings = 0
    }

    /** A new turn starts its own 90 s clock, independent of the keep-alive. */
    fun onTurnStart(nowMs: Long) {
        turnStartedMs = nowMs
    }

    fun tick(nowMs: Long): LinkAction? =
        when {
            lost -> null
            nowMs - turnStartedMs >= turnTimeoutMs -> LinkAction.TurnTimedOut
            nowMs - lastHeardMs < pingIntervalMs -> null
            missedPings >= maxMissedPings -> {
                lost = true
                LinkAction.DeclareLost
            }
            nowMs - lastPingMs >= pingIntervalMs -> {
                lastPingMs = nowMs
                missedPings++
                LinkAction.SendPing
            }
            else -> null
        }

    /** Seconds left before the current turn is abandoned; the UI counts down from 75 s. */
    fun secondsLeftInTurn(nowMs: Long): Int =
        ((turnTimeoutMs - (nowMs - turnStartedMs)) / MS_PER_SECOND).toInt().coerceAtLeast(0)
}
