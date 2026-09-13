package dev.bambu.core

/**
 * Deterministic xorshift64\* generator (§4).
 *
 * Neither `java.util.Random` nor `kotlin.random.Random` is used: neither guarantees the
 * same sequence across platform versions, and the whole match — scenario, wind and the
 * AI's noise — depends on two devices getting byte-for-byte the same values.
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

    /** Integer in `0 until bound`. */
    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()

    /** Integer in `a..b`, both included. */
    fun nextIntRange(
        a: Int,
        b: Int,
    ): Int = a + nextInt(b - a + 1)

    /** Float in `[0, 1)` with 24 bits of precision, exact in binary. */
    fun nextFloat(): Float = ((nextLong() ushr 40) / 16777216f)

    /**
     * Standard normal via Box-Muller.
     *
     * `u1` is resampled while it is zero: `ln(0)` is infinite and the AI's shot would
     * come out with a NaN angle. It happens once every 16.7 M calls, which at two per
     * turn is rare but not impossible, and it would be a miserable bug to diagnose.
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
 * Wind for a turn (§4). Derived from the seed, never transmitted: both devices compute
 * it and a whole class of synchronisation bugs disappears.
 *
 * `turn` is the match-wide counter, not the one within a round.
 */
fun windForTurn(
    seed: Long,
    turn: Int,
): Int = Rng(seed * 0x100000001B3L + turn).nextIntRange(-10, 10)
