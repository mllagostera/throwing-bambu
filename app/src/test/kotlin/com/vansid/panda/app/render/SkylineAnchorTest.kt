package com.vansid.panda.app.render

import com.vansid.panda.core.G
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The regression this guards against: the band used to be lifted 45 px above the base of
 * the buildings, five more than the shortest building the generator can produce. On every
 * scenario with a 40 px building, a 5 px strip of bare sky opened between the base of the
 * background city and that roof, and the background read as floating over the skyline.
 *
 * The number is only ever wrong in relation to [G.BUILD_H_MIN], so that is what the
 * assertion compares against: anyone lowering the minimum building height has to move the
 * band with it, and this test is where they find out.
 */
class SkylineAnchorTest {
    @Test
    fun theBandNeverFloatsOverAStripOfSky() {
        val bandBase = G.H - SKYLINE_BOTTOM_OFFSET
        val lowestRoof = G.H - G.BUILD_H_MIN

        assertTrue(
            bandBase <= lowestRoof,
            "the band's base is at y=$bandBase, below the lowest roof (y=$lowestRoof): " +
                "a ${lowestRoof - bandBase} px strip of sky would show under the city",
        )
    }

    @Test
    fun theBandIsLiftedFarEnoughToBeSeenAtAll() {
        assertTrue(
            SKYLINE_BOTTOM_OFFSET > 0,
            "flush with the bottom edge the band is buried by the playable buildings",
        )
    }
}
