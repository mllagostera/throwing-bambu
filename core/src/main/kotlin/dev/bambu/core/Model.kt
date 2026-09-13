package dev.bambu.core

import kotlin.math.abs

/**
 * Un edificio del skyline.
 *
 * No es `data class` a propósito (D-06): con un array dentro, `equals` compararía por
 * identidad y los tests de determinismo pasarían o fallarían por el motivo equivocado.
 */
class Building(
    val x: Int,
    val width: Int,
    val height: Int,
    val paletteIdx: Int,
    /** Ventanas encendidas/apagadas, orden fila-mayor de arriba abajo. */
    val windows: BooleanArray,
    /** Columnas de la rejilla de ventanas; `windows.size == rows * cols`. */
    val windowCols: Int,
) {
    /** Píxel del tejado: donde apoyan los pies del gorila. */
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
 * Terreno destructible: una máscara de sólidos y un índice de paleta por píxel.
 *
 * El origen está arriba a la izquierda y la `y` crece hacia abajo, como en el lienzo.
 */
class Terrain(
    val width: Int,
    /** `width * G.H`, `true` = sólido. */
    val mask: BooleanArray,
    /** `width * G.H`, índice EGA por píxel; [Palette.EMPTY] donde no hay terreno. */
    val color: ByteArray,
    val buildings: List<Building>,
) {
    /** Fuera del lienzo no hay nada sólido: el cielo está abierto por los lados y por arriba. */
    fun solid(
        x: Int,
        y: Int,
    ): Boolean = x >= 0 && x < width && y >= 0 && y < G.H && mask[y * width + x]

    /** Abre un cráter circular. La física nunca llama a esto: solo el motor de partida. */
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
     * Primer píxel sólido del segmento `(x0,y0) → (x1,y1)`, recorrido con DDA entero (D-03).
     *
     * Muestrear solo el extremo del paso no basta: el avance real llega a 2,5 px y el
     * terreno, tras varios cráteres, deja istmos de 1–2 px que el plátano atravesaría.
     * Devuelve `true` y escribe el píxel en [out]; `false` si el segmento está despejado.
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
     * Huella FNV-1a de 64 bits sobre máscara y color. Es lo que comparan los tests de
     * determinismo: comparar objetos no sirve, y volcar dos arrays de 92 000 elementos
     * en cada aserción tampoco.
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

/** `x` es el centro horizontal; `roofY`, el píxel del tejado donde apoya los pies. */
data class Gorilla(
    val player: Int,
    val x: Int,
    val roofY: Int,
    var alive: Boolean = true,
)

data class Scenario(
    val width: Int,
    val terrain: Terrain,
    val gorillas: List<Gorilla>,
    val sunX: Int,
)

/** `angle` en 0..90 grados, `power` en 1..100. `turn` es el contador global de la partida. */
data class Shot(
    val turn: Int,
    val angle: Int,
    val power: Int,
)

/**
 * Cómo termina un vuelo.
 *
 * No existe `HitSun` (D-02): el sol cambia de expresión pero no detiene el proyectil,
 * así que ese resultado sería inalcanzable. El impacto en el sol viaja en
 * [ShotResult.sunHit].
 */
sealed interface Outcome {
    data class HitGorilla(
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
 * Trayectoria y desenlace de un disparo.
 *
 * El `path` que publica el motor de partida es propio y está recortado a `steps * 2`
 * (D-01): la UI lo anima durante segundos mientras la IA lanza cientos de simulaciones,
 * y un búfer compartido se sobrescribiría a mitad de animación. Solo
 * [simulateInto] devuelve un `path` prestado.
 */
class ShotResult(
    /** Pares x,y intercalados: `path[2i]`, `path[2i+1]`. */
    val path: FloatArray,
    val steps: Int,
    val outcome: Outcome,
    val impactX: Int,
    val impactY: Int,
    val sunHit: Boolean,
)
