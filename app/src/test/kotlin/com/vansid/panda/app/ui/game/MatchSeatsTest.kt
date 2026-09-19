package com.vansid.panda.app.ui.game

import com.vansid.panda.core.AiLevel
import com.vansid.panda.core.HumanShotSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * The regression these guard against: two people at one device got two
 * [HumanShotSource] instances, but the Throw button only ever fed the first. On the
 * second player's turn the engine was waiting on the other one, so the shot went to a
 * channel nobody was reading and the match froze with the button still lit.
 *
 * Identity is what the assertions check, never equality. Two `HumanShotSource`s are
 * distinct objects with distinct channels, and it was the object that was wrong.
 */
class MatchSeatsTest {
    @Test
    fun bothPlayersSitOnSourcesTheEngineReads() {
        val human = HumanShotSource()

        val seats = localSeats(human, aiLevel = null, seed = 1L)

        assertEquals(setOf(0, 1), seats.humanPlayers, "a hot-seat match seats two people")
        assertSame(human, seats.sources[0], "seat 0 is the view model's own source")
        val secondSeat = assertNotNull(seats.humanSeats[1], "the second person needs a seat")
        assertSame(secondSeat, seats.sources[1], "seat 1 is what the engine reads")
    }

    @Test
    fun onlySeatZeroIsHumanAgainstTheComputer() {
        val human = HumanShotSource()

        val seats = localSeats(human, aiLevel = AiLevel.MEDIUM, seed = 1L)

        assertEquals(setOf(0), seats.humanPlayers, "the computer does not get a Throw button")
        assertSame(human, seats.sources[0])
    }

    @Test
    fun aSeatTheEngineDoesNotReadIsRejected() {
        val human = HumanShotSource()
        val unreachable = HumanShotSource()

        // Exactly the shape of the bug: seat 1 is a source the engine was never given.
        assertFailsWith<IllegalArgumentException> {
            MatchSeats(
                sources = listOf(human, HumanShotSource()),
                humanSeats = mapOf(0 to human, 1 to unreachable),
            )
        }
    }
}
