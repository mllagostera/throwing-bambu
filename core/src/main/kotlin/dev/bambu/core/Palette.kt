package dev.bambu.core

/**
 * Paleta cerrada EGA-16 y colores derivados del arte entregado (`art/`).
 *
 * Vive en `core` como enteros ARGB: son datos, no recursos de Android, y el generador
 * de escenario necesita saber cuántas fachadas hay.
 *
 * Los valores **no se inventan aquí**: salen de `art/palette/ega16.gpl` (el orden de
 * los índices es normativo), de los cuatro píxeles clave de cada celda de
 * `art/sprites/facades.png` y de `art/sky.txt`. Si el arte se regenera y cambian,
 * cambian aquí.
 */
object Palette {
    /** Los 16 colores EGA en el orden normativo de `art/palette/ega16.gpl`. */
    val EGA =
        intArrayOf(
            0xFF000000.toInt(), // 0 negro
            0xFF0000AA.toInt(), // 1 azul
            0xFF00AA00.toInt(), // 2 verde
            0xFF00AAAA.toInt(), // 3 cian
            0xFFAA0000.toInt(), // 4 rojo
            0xFFAA00AA.toInt(), // 5 magenta
            0xFFAA5500.toInt(), // 6 marrón
            0xFFAAAAAA.toInt(), // 7 gris claro
            0xFF555555.toInt(), // 8 gris oscuro
            0xFF5555FF.toInt(), // 9 azul claro
            0xFF55FF55.toInt(), // 10 verde claro
            0xFF55FFFF.toInt(), // 11 cian claro
            0xFFFF5555.toInt(), // 12 rojo claro
            0xFFFF55FF.toInt(), // 13 magenta claro
            0xFFFFFF55.toInt(), // 14 amarillo
            0xFFFFFFFF.toInt(), // 15 blanco
        )

    // Índices EGA con nombre, para no repetir números sueltos.
    const val BLACK: Byte = 0
    const val BLUE: Byte = 1
    const val CYAN: Byte = 3
    const val RED: Byte = 4
    const val MAGENTA: Byte = 5
    const val BROWN: Byte = 6
    const val DARK_GREY: Byte = 8
    const val LIGHT_BLUE: Byte = 9
    const val YELLOW: Byte = 14

    /**
     * Valor de [Terrain.color] allí donde no hay terreno. No es un color: el render
     * pinta el degradado del cielo en esos píxeles.
     */
    const val EMPTY: Byte = -1

    /** Degradado del cielo (`art/sky.txt`); el motor interpola entre los dos. */
    val SKY_TOP = EGA[BLUE.toInt()]
    val SKY_BOTTOM = EGA[LIGHT_BLUE.toInt()]

    /**
     * Una variante de fachada, tal y como la codifica `facades.png` en los píxeles
     * (0,0) a (3,0) de cada celda de 16×16.
     *
     * [outline] todavía no lo usa el generador: el §7 pinta base y ventanas. Queda
     * disponible para el borde de los edificios cuando entre el render de M4.
     */
    data class Facade(
        val base: Byte,
        val lit: Byte,
        val unlit: Byte,
        val outline: Byte,
    )

    /**
     * Las cinco fachadas entregadas. **Cinco, no seis**: el número salió del arte, no
     * de una estimación, y cambiarlo altera el consumo del RNG en `ScenarioGen`.
     *
     * Ninguna base es verde (se tragaría la caña de bambú) ni blanca (se tragaría al
     * panda); `tools/verify_assets.py` lo comprueba en cada regeneración.
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

    /** Color ARGB de un índice de [Terrain.color]. */
    fun argb(index: Byte): Int = EGA[index.toInt()]
}
