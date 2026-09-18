package com.vansid.panda.app.ui.bluetooth

import com.vansid.panda.core.net.MatchConfig
import com.vansid.panda.core.net.Transport

/**
 * The agreed link, handed from the pairing screen to the game screen.
 *
 * A process-wide holder rather than a navigation argument, because a live socket is not
 * something that can be serialised into a route — and rebuilding it on the other side
 * would mean pairing twice. It is deliberately tiny and deliberately cleared by whoever
 * finishes with it: a stale [link] here is a radio nobody is listening to.
 */
object BluetoothSession {
    var link: Transport? = null
        private set

    var config: MatchConfig? = null
        private set

    var localPlayer: Int = 0
        private set

    fun begin(
        link: Transport,
        config: MatchConfig,
        localPlayer: Int,
    ) {
        this.link = link
        this.config = config
        this.localPlayer = localPlayer
    }

    /** Forgets the link without touching it: the pairing screen still owns it. */
    fun clear() {
        link = null
        config = null
        localPlayer = 0
    }

    /** Hangs up and forgets it: the match is over, or its screen is gone. */
    fun end() {
        link?.close()
        clear()
    }
}
