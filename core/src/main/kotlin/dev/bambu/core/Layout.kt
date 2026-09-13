package dev.bambu.core

/**
 * Integer pixel scale for a given screen (§3, D-04).
 *
 * The scale cannot come from the height alone: with `W_MIN` forced, the canvas can end
 * up wider than the screen (1080×2400 in portrait would give scale 12 and 320 × 12 =
 * 3840 px over the 1080 available). The loop lowers it until it actually fits.
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
 * Width of the logical canvas. The height is always [G.H]; what changes is how many
 * buildings fit. Horizontal leftovers are sky-coloured bars, never stretching.
 */
fun logicalWidth(
    screenW: Int,
    screenH: Int,
): Int = (screenW / logicalScale(screenW, screenH)).coerceIn(G.W_MIN, G.W_MAX)
