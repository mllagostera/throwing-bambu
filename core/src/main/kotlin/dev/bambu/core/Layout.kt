package dev.bambu.core

/**
 * Escala de píxel entero para una pantalla dada (§3, D-04).
 *
 * La escala no puede salir solo de la altura: con `W_MIN` forzado, el lienzo puede
 * acabar siendo más ancho que la pantalla (1080×2400 en vertical daría escala 12 y
 * 320 × 12 = 3840 px sobre 1080 disponibles). El bucle la baja hasta que quepa.
 */
fun logicalScale(
    screenW: Int,
    screenH: Int,
): Int {
    var scale = maxOf(1, screenH / G.H)
    while (scale > 1 && screenW / scale < G.W_MIN) {
        scale--
    }
    return scale
}

/**
 * Anchura del lienzo lógico. La altura es siempre [G.H]; lo que cambia es cuántos
 * edificios caben. El sobrante horizontal son barras del color del cielo, nunca
 * estiramiento.
 */
fun logicalWidth(
    screenW: Int,
    screenH: Int,
): Int = (screenW / logicalScale(screenW, screenH)).coerceIn(G.W_MIN, G.W_MAX)
