package com.vansid.panda.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioGenTest {
    /** §15.2: the same (seed, width) pair produces exactly the same terrain. */
    @Test
    fun generateIsReproducible() {
        val reference = generate(SEED, G.W_MIN).terrain.fingerprint()
        repeat(100) {
            assertEquals(reference, generate(SEED, G.W_MIN).terrain.fingerprint())
        }
    }

    @Test
    fun differentSeedsGiveDifferentTerrain() {
        val a = generate(SEED, G.W_MIN).terrain.fingerprint()
        val b = generate(SEED + 1, G.W_MIN).terrain.fingerprint()
        assertNotEquals(a, b)
    }

    /** §15.3: no roof invades the sky band and the pandas never share a building. */
    @Test
    fun invariantsHoldAcrossManySeeds() {
        forEachScenario { seed, width, s ->
            for (b in s.terrain.buildings) {
                assertTrue(
                    "roof above SKY_BAND (seed $seed, width $width): roofY=${b.roofY}",
                    b.roofY >= G.SKY_BAND,
                )
                assertTrue("height out of range: ${b.height}", b.height in G.BUILD_H_MIN..G.BUILD_H_MAX)
            }

            val (left, right) = s.pandas
            assertNotEquals("both pandas on the same building", buildingOf(s, left.x), buildingOf(s, right.x))
            assertTrue("pandas are not left-to-right", left.x < right.x)
        }
    }

    /** A2 (§17.4): the classic bug — a panda wider than the building it stands on. */
    @Test
    fun pandasNeverStandOnTooNarrowBuildings() {
        forEachScenario { seed, width, s ->
            for (p in s.pandas) {
                val b = s.terrain.buildings[buildingOf(s, p.x)]
                assertTrue(
                    "panda wider than its building (seed $seed, width $width): ${b.width}",
                    b.width >= G.PANDA_W + 4,
                )
                assertTrue("panda overflows on the left", p.x - G.PANDA_W / 2 >= b.x)
                assertTrue("panda overflows on the right", p.x + G.PANDA_W / 2 <= b.x + b.width)
                assertEquals("feet do not rest on the roof", b.roofY, p.roofY)
            }
        }
    }

    @Test
    fun buildingsCoverTheWholeCanvas() {
        forEachScenario { _, width, s ->
            val buildings = s.terrain.buildings
            assertEquals(0, buildings.first().x)
            assertEquals(width, buildings.last().x + buildings.last().width)
            for (i in 1 until buildings.size) {
                assertEquals(
                    "gap between buildings",
                    buildings[i - 1].x + buildings[i - 1].width,
                    buildings[i].x,
                )
            }
            // Everything below a roof is solid: no hollow buildings.
            for (b in buildings) {
                assertTrue(s.terrain.solid(b.x + b.width / 2, G.H - 1))
                assertTrue(s.terrain.solid(b.x + b.width / 2, b.roofY))
                assertTrue(
                    "there is terrain above the roof",
                    !s.terrain.solid(b.x + b.width / 2, b.roofY - 1),
                )
            }
        }
    }

    private fun buildingOf(
        s: Scenario,
        x: Int,
    ): Int = s.terrain.buildings.indexOfFirst { x >= it.x && x < it.x + it.width }

    private fun forEachScenario(check: (Long, Int, Scenario) -> Unit) {
        for (width in intArrayOf(G.W_MIN, 383, 420, G.W_MAX)) {
            for (i in 0 until 150) {
                val seed = SEED + i * 7919L
                check(seed, width, generate(seed, width))
            }
        }
    }

    private companion object {
        const val SEED = 20260913L
    }
}
