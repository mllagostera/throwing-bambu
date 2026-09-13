package dev.bambu.core

import kotlin.math.abs

/**
 * One building of the skyline.
 *
 * Deliberately not a `data class` (D-06): with an array inside, `equals` would compare
 * by identity and the determinism tests would pass or fail for the wrong reason.
 */
class Building(
    val x: Int,
    val width: Int,
    val height: Int,
    val paletteIdx: Int,
    /** Windows lit/unlit, row-major from the top. */
    val windows: BooleanArray,
    /** Columns of the window grid; `windows.size == rows * cols`. */
    val windowCols: Int,
) {
    /** Roof pixel: where the panda's feet rest. */
    val roofY: Int get() = G.H - height

    override fun equals(other: Any?): Boolean =
        other is Building &&
            x == other.x &&
            width == other.width &&
            height == other.height &&
            paletteIdx == other.paletteIdx &&
            windowCols == other.windowCols &&
            windows.contentEquals(other.windows)

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + paletteIdx
        result = 31 * result + windowCols
        result = 31 * result + windows.contentHashCode()
        return result
    }
}

/**
 * Destructible terrain: a mask of solid pixels and one palette index per pixel.
 *
 * The origin is top-left and `y` grows downwards, like the canvas.
 */
class Terrain(
    val width: Int,
    /** `width * G.H`, `true` = solid. */
    val mask: BooleanArray,
    /** `width * G.H`, EGA index per pixel; [Palette.EMPTY] where there is no terrain. */
    val color: ByteArray,
    val buildings: List<Building>,
) {
    /** Nothing is solid outside the canvas: the sky is open at the sides and above. */
    fun solid(
        x: Int,
        y: Int,
    ): Boolean = x >= 0 && x < width && y >= 0 && y < G.H && mask[y * width + x]

    /** Opens a circular crater. Physics never calls this: only the match engine does. */
    fun blast(
        cx: Int,
        cy: Int,
        r: Int,
    ) {
        val r2 = r * r
        for (y in (cy - r)..(cy + r)) {
            if (y < 0 || y >= G.H) continue
            val dy = y - cy
            for (x in (cx - r)..(cx + r)) {
                if (x < 0 || x >= width) continue
                val dx = x - cx
                if (dx * dx + dy * dy <= r2) {
                    val i = y * width + x
                    mask[i] = false
                    color[i] = Palette.EMPTY
                }
            }
        }
    }

    /**
     * First solid pixel along the segment `(x0,y0) → (x1,y1)`, walked with an integer
     * DDA (D-03).
     *
     * Sampling only the end of the step is not enough: the real advance reaches 2.5 px
     * and the terrain, after a few craters, leaves 1–2 px isthmuses the cane would fly
     * straight through. Returns `true` and writes the pixel into [out]; `false` if the
     * segment is clear.
     */
    fun firstSolidOnSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        out: IntArray,
    ): Boolean {
        var cx = x0.toInt()
        var cy = y0.toInt()
        val ex = x1.toInt()
        val ey = y1.toInt()

        val dx = abs(ex - cx)
        val dy = -abs(ey - cy)
        val sx = if (cx < ex) 1 else -1
        val sy = if (cy < ey) 1 else -1
        var err = dx + dy

        while (true) {
            if (solid(cx, cy)) {
                out[0] = cx
                out[1] = cy
                return true
            }
            if (cx == ex && cy == ey) return false
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                cx += sx
            }
            if (e2 <= dx) {
                err += dx
                cy += sy
            }
        }
    }

    /**
     * FNV-1a 64-bit fingerprint over mask and colour. This is what the determinism
     * tests compare: comparing objects is useless, and dumping two 92 000-element
     * arrays on every assertion is worse.
     */
    fun fingerprint(): Long {
        var h = FNV_OFFSET_BASIS
        for (i in mask.indices) {
            h = (h xor (if (mask[i]) 1L else 0L)) * FNV_PRIME
            h = (h xor color[i].toLong()) * FNV_PRIME
        }
        return h
    }

    private companion object {
        const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001B3L
    }
}

/** `x` is the horizontal centre; `roofY`, the roof pixel the feet rest on. */
data class Panda(
    val player: Int,
    val x: Int,
    val roofY: Int,
    var alive: Boolean = true,
)

data class Scenario(
    val width: Int,
    val terrain: Terrain,
    val pandas: List<Panda>,
    val sunX: Int,
)

/** `angle` in 0..90 degrees, `power` in 1..100. `turn` is the match-wide counter. */
data class Shot(
    val turn: Int,
    val angle: Int,
    val power: Int,
)

/**
 * How a flight ends.
 *
 * There is no `HitSun` (D-02): the sun changes expression but does not stop the cane,
 * so that outcome would be unreachable. Hitting the sun travels in [ShotResult.sunHit].
 */
sealed interface Outcome {
    data class HitPanda(
        val player: Int,
    ) : Outcome

    data class HitTerrain(
        val x: Int,
        val y: Int,
    ) : Outcome

    data object OffScreen : Outcome

    data object TimeOut : Outcome
}

/**
 * Trajectory and outcome of one shot.
 *
 * The `path` published by the match engine is owned and trimmed to `steps * 2` (D-01):
 * the UI animates it for seconds while the AI runs hundreds of simulations, and a
 * shared buffer would be overwritten mid-animation. Only [simulateInto] returns a
 * borrowed `path`.
 */
class ShotResult(
    /** Interleaved x,y pairs: `path[2i]`, `path[2i+1]`. */
    val path: FloatArray,
    val steps: Int,
    val outcome: Outcome,
    val impactX: Int,
    val impactY: Int,
    val sunHit: Boolean,
)
