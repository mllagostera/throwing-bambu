package dev.bambu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The engine's palette must match the delivered art.
 *
 * `Palette` was written by hand from `art/`, so without this test any regeneration of
 * the art — a different facade colour, one more variant — would leave the engine
 * painting something other than what design delivered, and nobody would notice until
 * they saw it on screen.
 *
 * The files are build output of `tools/gen_sprites.py`; this test only reads them.
 */
class PaletteMatchesArtTest {
    @Test
    fun facadeKeyPixelsMatchTheDeliveredArt() {
        val file = artFile("sprites/facades.png") ?: return
        val image = ImageIO.read(file)

        val cells = image.width / FACADE_CELL
        assertEquals("the art no longer has the same number of facades as the engine", cells, Palette.N_FACADES)

        for (i in 0 until cells) {
            val facade = Palette.FACADES[i]
            val keys = listOf(facade.base, facade.lit, facade.unlit, facade.outline)
            val names = listOf("base", "lit", "unlit", "outline")
            for (k in keys.indices) {
                val fromArt = image.getRGB(i * FACADE_CELL + k, 0)
                assertEquals(
                    "facade $i, color ${names[k]}: the art says ${hex(fromArt)} " +
                        "and the engine ${hex(Palette.argb(keys[k]))}",
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
        // Closed palette (rule 1 of AGENTS.md): no invented colours.
        for (facade in Palette.FACADES) {
            for (index in listOf(facade.base, facade.lit, facade.unlit, facade.outline)) {
                assertTrue("EGA index out of range: $index", index in 0..15)
            }
        }
        assertEquals(16, Palette.EGA.size)
    }

    @Test
    fun noFacadeBaseSwallowsThePlayerOrTheProjectile() {
        // Rule 7 of AGENTS.md, checked from the engine side as well.
        val green = 0xFF00AA00.toInt()
        val white = 0xFFFFFFFF.toInt()
        val bases = Palette.FACADES.map { Palette.argb(it.base) }
        assertTrue("a green facade would swallow the bamboo cane", bases.none { it == green })
        assertTrue("a white facade would swallow the panda", bases.none { it == white })
        assertEquals("duplicate facades", bases.size, bases.toSet().size)
    }

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** The art lives outside the module; if it is not mounted, the test does not apply. */
    private fun artFile(relative: String): File? =
        listOf(File("../art/$relative"), File("art/$relative")).firstOrNull { it.isFile }

    private companion object {
        const val FACADE_CELL = 16
    }
}
