package dev.bambu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * La paleta del motor debe coincidir con el arte entregado.
 *
 * `Palette` se escribió a mano a partir de `art/`, así que sin este test cualquier
 * regeneración del arte —otro color de fachada, una variante más— dejaría el motor
 * pintando algo distinto de lo que el diseño entregó, y nadie se enteraría hasta
 * verlo en pantalla.
 *
 * Los ficheros son build output de `tools/gen_sprites.py`; este test solo los lee.
 */
class PaletteMatchesArtTest {
    @Test
    fun facadeKeyPixelsMatchTheDeliveredArt() {
        val file = artFile("sprites/facades.png") ?: return
        val image = ImageIO.read(file)

        val cells = image.width / FACADE_CELL
        assertEquals("el número de fachadas del arte ya no es el del motor", cells, Palette.N_FACADES)

        for (i in 0 until cells) {
            val facade = Palette.FACADES[i]
            val keys = listOf(facade.base, facade.lit, facade.unlit, facade.outline)
            val names = listOf("base", "encendida", "apagada", "contorno")
            for (k in keys.indices) {
                val fromArt = image.getRGB(i * FACADE_CELL + k, 0)
                assertEquals(
                    "fachada $i, color ${names[k]}: el arte dice ${hex(fromArt)} " +
                        "y el motor ${hex(Palette.argb(keys[k]))}",
                    fromArt,
                    Palette.argb(keys[k]),
                )
            }
        }
    }

    @Test
    fun skyGradientMatchesTheDeliveredArt() {
        val file = artFile("sky.txt") ?: return
        val values =
            file
                .readLines()
                .filterNot { it.startsWith("#") || it.isBlank() }
                .associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim() }

        assertEquals(hex(Palette.SKY_TOP), values["top"]?.uppercase()?.replace("#", "#"))
        assertEquals(hex(Palette.SKY_BOTTOM), values["bottom"]?.uppercase())
    }

    @Test
    fun everyPaletteColourIsAnEgaColour() {
        // Paleta cerrada (regla 1 de AGENTS.md): nada de colores inventados.
        for (facade in Palette.FACADES) {
            for (index in listOf(facade.base, facade.lit, facade.unlit, facade.outline)) {
                assertTrue("índice EGA fuera de rango: $index", index in 0..15)
            }
        }
        assertEquals(16, Palette.EGA.size)
    }

    @Test
    fun noFacadeBaseSwallowsThePlayerOrTheProjectile() {
        // Regla 7 de AGENTS.md, comprobada también desde el lado del motor.
        val green = 0xFF00AA00.toInt()
        val white = 0xFFFFFFFF.toInt()
        val bases = Palette.FACADES.map { Palette.argb(it.base) }
        assertTrue("una fachada verde se tragaría la caña de bambú", bases.none { it == green })
        assertTrue("una fachada blanca se tragaría al panda", bases.none { it == white })
        assertEquals("hay fachadas repetidas", bases.size, bases.toSet().size)
    }

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** El arte vive fuera del módulo; si no está montado, el test no aplica. */
    private fun artFile(relative: String): File? =
        listOf(File("../art/$relative"), File("art/$relative")).firstOrNull { it.isFile }

    private companion object {
        const val FACADE_CELL = 16
    }
}
