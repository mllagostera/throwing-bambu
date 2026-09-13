package dev.bambu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioGenTest {
    /** §15.2: el mismo par (semilla, anchura) produce exactamente el mismo terreno. */
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

    /** §15.3: ningún tejado invade la banda de cielo y los gorilas nunca comparten edificio. */
    @Test
    fun invariantsHoldAcrossManySeeds() {
        forEachScenario { seed, width, s ->
            for (b in s.terrain.buildings) {
                assertTrue(
                    "tejado sobre SKY_BAND (semilla $seed, ancho $width): roofY=${b.roofY}",
                    b.roofY >= G.SKY_BAND,
                )
                assertTrue("altura fuera de rango: ${b.height}", b.height in G.BUILD_H_MIN..G.BUILD_H_MAX)
            }

            val (left, right) = s.gorillas
            assertNotEquals("los dos gorilas en el mismo edificio", buildingOf(s, left.x), buildingOf(s, right.x))
            assertTrue("los gorilas no están en orden", left.x < right.x)
        }
    }

    /** A2 (§17.4): el fallo clásico — un gorila más ancho que su propio edificio. */
    @Test
    fun gorillasNeverStandOnTooNarrowBuildings() {
        forEachScenario { seed, width, s ->
            for (g in s.gorillas) {
                val b = s.terrain.buildings[buildingOf(s, g.x)]
                assertTrue(
                    "gorila más ancho que su edificio (semilla $seed, ancho $width): ${b.width}",
                    b.width >= G.GORILLA_W + 4,
                )
                assertTrue("el gorila se sale por la izquierda", g.x - G.GORILLA_W / 2 >= b.x)
                assertTrue("el gorila se sale por la derecha", g.x + G.GORILLA_W / 2 <= b.x + b.width)
                assertEquals("los pies no apoyan en el tejado", b.roofY, g.roofY)
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
                assertEquals("hueco entre edificios", buildings[i - 1].x + buildings[i - 1].width, buildings[i].x)
            }
            // El suelo bajo cada tejado es sólido: nada de edificios huecos.
            for (b in buildings) {
                assertTrue(s.terrain.solid(b.x + b.width / 2, G.H - 1))
                assertTrue(s.terrain.solid(b.x + b.width / 2, b.roofY))
                assertTrue("hay terreno por encima del tejado", !s.terrain.solid(b.x + b.width / 2, b.roofY - 1))
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
