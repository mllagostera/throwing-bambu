package dev.bambu.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4 (D-04): el lienzo lógico escalado nunca puede ser más ancho que la pantalla.
 * La versión original de `logicalWidth` fallaba en cualquier móvil en vertical.
 */
class LayoutTest {
    @Test
    fun scaledCanvasAlwaysFitsOnScreen() {
        for ((w, h) in REAL_SCREENS) {
            for ((sw, sh) in listOf(w to h, h to w)) {
                val scale = logicalScale(sw, sh)
                val width = logicalWidth(sw, sh)
                assertTrue("escala inválida en ${sw}x$sh: $scale", scale >= 1)
                assertTrue(
                    "el lienzo se sale de la pantalla en ${sw}x$sh: ${width * scale} > $sw",
                    width * scale <= sw,
                )
                assertTrue("altura desbordada en ${sw}x$sh", G.H * scale <= sh)
                assertTrue("anchura fuera del contrato: $width", width in G.W_MIN..G.W_MAX)
            }
        }
    }

    @Test
    fun scaleIsAnIntegerNumberOfPixels() {
        // Píxel entero siempre (§0.4): nada de escalados fraccionarios.
        val scale = logicalScale(2400, 1080)
        assertTrue(scale == 5)
    }

    private companion object {
        val REAL_SCREENS =
            listOf(
                1080 to 1920,
                1080 to 2400,
                1080 to 2340,
                1440 to 3120,
                1440 to 2560,
                720 to 1280,
                720 to 1600,
                800 to 1280,
                1200 to 1920,
                1600 to 2560,
                2048 to 2732,
                960 to 540,
            )
    }
}
