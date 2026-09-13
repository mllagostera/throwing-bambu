package dev.bambu.core

/**
 * Closed EGA-16 palette and the colours derived from the delivered art (`art/`).
 *
 * It lives in `core` as ARGB integers: they are data, not Android resources, and the
 * scenario generator needs to know how many facades there are.
 *
 * The values are **not invented here**: they come from `art/palette/ega16.gpl` (the
 * index order is normative), from the four key pixels of each cell of
 * `art/sprites/facades.png`, and from `art/sky.txt`. If the art is regenerated and they
 * change, they change here.
 */
object Palette {
    /** The 16 EGA colours in the normative order of `art/palette/ega16.gpl`. */
    val EGA =
        intArrayOf(
            0xFF000000.toInt(), // 0 black
            0xFF0000AA.toInt(), // 1 blue
            0xFF00AA00.toInt(), // 2 green
            0xFF00AAAA.toInt(), // 3 cyan
            0xFFAA0000.toInt(), // 4 red
            0xFFAA00AA.toInt(), // 5 magenta
            0xFFAA5500.toInt(), // 6 brown
            0xFFAAAAAA.toInt(), // 7 light grey
            0xFF555555.toInt(), // 8 dark grey
            0xFF5555FF.toInt(), // 9 light blue
            0xFF55FF55.toInt(), // 10 light green
            0xFF55FFFF.toInt(), // 11 light cyan
            0xFFFF5555.toInt(), // 12 light red
            0xFFFF55FF.toInt(), // 13 light magenta
            0xFFFFFF55.toInt(), // 14 yellow
            0xFFFFFFFF.toInt(), // 15 white
        )

    // Named EGA indices, so loose numbers do not spread through the code.
    const val BLACK: Byte = 0
    const val BLUE: Byte = 1
    const val GREEN_INDEX: Byte = 2
    const val CYAN: Byte = 3
    const val RED: Byte = 4
    const val MAGENTA: Byte = 5
    const val BROWN: Byte = 6
    const val DARK_GREY: Byte = 8
    const val LIGHT_BLUE: Byte = 9
    const val LIGHT_GREEN_INDEX: Byte = 10
    const val LIGHT_RED_INDEX: Byte = 12
    const val YELLOW: Byte = 14

    /**
     * Value of [Terrain.color] where there is no terrain. It is not a colour: the
     * renderer paints the sky gradient on those pixels.
     */
    const val EMPTY: Byte = -1

    /** Sky gradient (`art/sky.txt`); the engine interpolates between the two. */
    val SKY_TOP = EGA[BLUE.toInt()]
    val SKY_BOTTOM = EGA[LIGHT_BLUE.toInt()]

    /**
     * One facade variant, as `facades.png` encodes it in pixels (0,0) to (3,0) of each
     * 16×16 cell.
     *
     * [outline] is not used by the generator yet: §7 paints base and windows. It is
     * here for the building edges when the M4 renderer arrives.
     */
    data class Facade(
        val base: Byte,
        val lit: Byte,
        val unlit: Byte,
        val outline: Byte,
    )

    /**
     * The five delivered facades. **Five, not six**: the number comes from the art, not
     * from a guess, and changing it alters how much the RNG consumes in `ScenarioGen`.
     *
     * No base is green (it would swallow the bamboo cane) or white (it would swallow
     * the panda); `tools/verify_assets.py` checks that on every regeneration.
     */
    val FACADES =
        arrayOf(
            Facade(base = CYAN, lit = YELLOW, unlit = BLUE, outline = BLACK),
            Facade(base = RED, lit = YELLOW, unlit = BLACK, outline = DARK_GREY),
            Facade(base = DARK_GREY, lit = YELLOW, unlit = BLUE, outline = BLACK),
            Facade(base = MAGENTA, lit = YELLOW, unlit = BLUE, outline = BLACK),
            Facade(base = BROWN, lit = YELLOW, unlit = BLUE, outline = BLACK),
        )

    val N_FACADES = FACADES.size

    /** ARGB colour for an index of [Terrain.color]. */
    fun argb(index: Byte): Int = EGA[index.toInt()]
}
