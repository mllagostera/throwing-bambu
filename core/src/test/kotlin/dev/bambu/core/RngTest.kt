package dev.bambu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test §15.1. Los valores esperados no salen de ejecutar este mismo código: se
 * calcularon con una implementación independiente de xorshift64\* (en Python) a partir
 * de la definición del §4. Si esta secuencia cambia, cambian todas las partidas.
 */
class RngTest {
    @Test
    fun nextLongReproducesKnownSequence() {
        val rng = Rng(12345L)
        assertEquals(2288052754377729556L, rng.nextLong())
        assertEquals(5146384899607486740L, rng.nextLong())
        assertEquals(-1044009175986990732L, rng.nextLong())
        assertEquals(4593456849570825761L, rng.nextLong())
        assertEquals(-6445803566448886877L, rng.nextLong())
    }

    @Test
    fun seedOneAndZeroAreStable() {
        val one = Rng(1L)
        assertEquals(2532300887602411097L, one.nextLong())
        assertEquals(4152556393845530201L, one.nextLong())

        // La semilla 0 se sustituye por una constante: xorshift se quedaría clavado en 0.
        val zero = Rng(0L)
        assertEquals(-1541515596852347406L, zero.nextLong())
        assertEquals(198525909433649115L, zero.nextLong())
    }

    @Test
    fun derivedGeneratorsAreStable() {
        val ints = Rng(12345L)
        assertEquals(listOf(78, 70, 42, 80, 69), List(5) { ints.nextInt(100) })

        val f = Rng(12345L)
        assertEquals(0.12403554f, f.nextFloat(), 1e-7f)
        assertEquals(0.27898604f, f.nextFloat(), 1e-7f)
        assertEquals(0.94340414f, f.nextFloat(), 1e-7f)
    }

    @Test
    fun sameSeedGivesSameSequence() {
        val a = Rng(-98765L)
        val b = Rng(-98765L)
        repeat(1000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun rangesStayInsideBounds() {
        val rng = Rng(7L)
        repeat(10000) {
            val v = rng.nextIntRange(-10, 10)
            assertTrue("viento fuera de rango: $v", v in -10..10)
            val f = rng.nextFloat()
            assertTrue("nextFloat fuera de [0,1): $f", f >= 0f && f < 1f)
        }
    }

    @Test
    fun gaussianIsFiniteAndCentred() {
        val rng = Rng(4242L)
        var sum = 0.0
        val n = 20000
        repeat(n) {
            val g = rng.nextGaussian()
            assertTrue("gaussiana no finita: $g", g.isFinite())
            sum += g
        }
        // Media muestral: con n = 20 000 el error estándar es ~0,007.
        assertEquals(0.0, sum / n, 0.05)
    }

    @Test
    fun windIsDerivedNotTransmitted() {
        assertEquals(listOf(-1, -1, -8, -6, -1, 5), List(6) { windForTurn(999L, it) })
        // Mismo turno, misma semilla: los dos dispositivos calculan lo mismo.
        repeat(50) { turn ->
            assertEquals(windForTurn(31337L, turn), windForTurn(31337L, turn))
        }
    }
}
