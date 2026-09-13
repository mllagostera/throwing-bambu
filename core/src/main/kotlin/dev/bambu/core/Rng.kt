package dev.bambu.core

/**
 * Generador determinista xorshift64\* (§4).
 *
 * No se usa `java.util.Random` ni `kotlin.random.Random`: ninguno garantiza la misma
 * secuencia entre versiones de plataforma, y toda la partida —escenario, viento y
 * ruido de la IA— depende de que dos dispositivos obtengan exactamente lo mismo.
 */
class Rng(
    seed: Long,
) {
    private var s: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    fun nextLong(): Long {
        s = s xor (s ushr 12)
        s = s xor (s shl 25)
        s = s xor (s ushr 27)
        return s * -0x7ea3_5b2f_1c0d_49a7L
    }

    /** Entero en `0 until bound`. */
    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()

    /** Entero en `a..b`, ambos incluidos. */
    fun nextIntRange(
        a: Int,
        b: Int,
    ): Int = a + nextInt(b - a + 1)

    /** Float en `[0, 1)` con 24 bits de precisión, exactos en binario. */
    fun nextFloat(): Float = ((nextLong() ushr 40) / 16777216f)

    /**
     * Normal estándar por Box-Muller.
     *
     * `u1` se remuestrea mientras sea 0: `ln(0)` da infinito y el disparo de la IA
     * saldría con un ángulo NaN. Ocurre 1 de cada 16,7 M llamadas, que con dos por
     * turno es raro pero no imposible, y sería un fallo muy difícil de diagnosticar.
     */
    fun nextGaussian(): Float {
        var u1 = nextFloat()
        while (u1 <= 0f) {
            u1 = nextFloat()
        }
        val u2 = nextFloat()
        val radius = StrictMath.sqrt(-2.0 * StrictMath.log(u1.toDouble()))
        return (radius * StrictMath.cos(2.0 * StrictMath.PI * u2)).toFloat()
    }
}

/**
 * Viento del turno (§4). Derivado de la semilla, nunca transmitido: ambos dispositivos
 * lo calculan y desaparece una clase entera de bugs de sincronización.
 *
 * `turn` es el contador global de la partida, no el de la ronda.
 */
fun windForTurn(
    seed: Long,
    turn: Int,
): Int = Rng(seed * 0x100000001B3L + turn).nextIntRange(-10, 10)
