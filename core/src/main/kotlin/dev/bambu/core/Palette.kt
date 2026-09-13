package dev.bambu.core

/**
 * Paleta del juego (D-08). Vive en `core` como enteros ARGB: son datos, no recursos
 * de Android, y el generador de escenario necesita el número de fachadas.
 *
 * [Terrain.color] guarda un índice de esta tabla por píxel; el render de `app` la
 * convierte en un búfer ARGB sin volver a interpretarla.
 */
object Palette {
    const val N_FACADES = 6

    // Índices almacenados en Terrain.color
    const val IDX_EMPTY: Byte = 0
    const val IDX_WINDOW_OFF: Byte = 1
    const val IDX_WINDOW_ON: Byte = 2
    const val IDX_FACADE_FIRST: Byte = 3

    const val SKY = 0xFF101038.toInt()
    const val SUN = 0xFFFFD21E.toInt()
    const val GORILLA = 0xFF8A5A2B.toInt()
    const val BANANA = 0xFFFFE24A.toInt()

    /**
     * Tabla ARGB indexada por el valor de [Terrain.color]. El orden es contrato:
     * vacío, ventana apagada, ventana encendida y después las [N_FACADES] fachadas.
     */
    val ARGB =
        intArrayOf(
            SKY, // IDX_EMPTY
            0xFF2A2A3E.toInt(), // IDX_WINDOW_OFF
            0xFFFFE9A0.toInt(), // IDX_WINDOW_ON
            0xFF7A5C4B.toInt(),
            0xFF5E6B7A.toInt(),
            0xFF8A6E5D.toInt(),
            0xFF4F5D52.toInt(),
            0xFF7B5F79.toInt(),
            0xFF6A6A6A.toInt(),
        )

    fun facadeIndex(paletteIdx: Int): Byte = (IDX_FACADE_FIRST + paletteIdx).toByte()
}
