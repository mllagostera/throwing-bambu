package dev.bambu.core

/** Perfil del skyline (§7, paso 2). El orden de las constantes es contrato. */
private enum class SkylineMode { ASCENDENTE, DESCENDENTE, ALEATORIO }

// Rejilla de ventanas (D-07). Fijar estos valores es obligatorio: cambian el número de
// llamadas al RNG y, con ello, todas las semillas.
private const val WIN_W = 3
private const val WIN_H = 4
private const val WIN_MARGIN = 3
private const val WIN_GAP = 3
private const val WIN_ON_THRESHOLD = 0.5f

/** Ruido de altura en los perfiles ascendente y descendente (§7, paso 4). */
private const val HEIGHT_NOISE = 15

/** Holgura mínima del tejado para que quepa un gorila (§7, paso 8). */
private const val GORILLA_CLEARANCE = 4

/** Con menos edificios no hay sitio para dos gorilas en edificios distintos. */
private const val MIN_BUILDINGS = 4

/**
 * Genera el escenario de una ronda (§7).
 *
 * El orden de las llamadas al RNG **es** el algoritmo: reordenar cualquier paso cambia
 * todos los escenarios ya jugados y rompe la compatibilidad entre versiones. Las fases
 * están separadas tal y como las numera la especificación —primero todas las alturas,
 * después todas las paletas, después todas las ventanas—, no entrelazadas por edificio.
 */
fun generate(
    seed: Long,
    width: Int,
): Scenario {
    require(width in G.W_MIN..G.W_MAX) { "width fuera del rango lógico: $width" }

    val rng = Rng(seed)
    val mode = SkylineMode.entries[rng.nextInt(SkylineMode.entries.size)]
    val widths = buildingWidths(rng, width)
    val n = widths.size
    require(n >= MIN_BUILDINGS) { "escenario con $n edificios: no caben dos gorilas separados" }

    val heights = buildingHeights(rng, mode, n)
    val palettes = IntArray(n) { rng.nextInt(Palette.N_FACADES) }
    val buildings = ArrayList<Building>(n)

    var x = 0
    for (i in 0 until n) {
        val w = widths[i]
        val h = heights[i]
        val cols = gridCount(w)
        val rows = gridCount(h, cellSize = WIN_H)
        val windows = BooleanArray(rows * cols) { rng.nextFloat() < WIN_ON_THRESHOLD }
        buildings += Building(x, w, h, palettes[i], windows, cols)
        x += w
    }

    val terrain = paint(width, buildings)
    val gorillas = placeGorillas(buildings)
    return Scenario(width, terrain, gorillas, width / 2)
}

/**
 * Anchuras acumuladas hasta cubrir el lienzo (§7, paso 3). El último edificio se recorta
 * al espacio restante; si ese resto no llega al mínimo, se suma al anterior, que entonces
 * puede superar `BUILD_W_MAX`. Es la lectura literal de la especificación.
 */
private fun buildingWidths(
    rng: Rng,
    width: Int,
): IntArray {
    val widths = ArrayList<Int>()
    var x = 0
    while (x < width) {
        var w = rng.nextIntRange(G.BUILD_W_MIN, G.BUILD_W_MAX)
        if (x + w > width) w = width - x
        if (w < G.BUILD_W_MIN && widths.isNotEmpty()) {
            widths[widths.size - 1] = widths.last() + w
            break
        }
        widths += w
        x += w
    }
    return widths.toIntArray()
}

/** Alturas según el perfil (§7, paso 4), con clamp final al rango del contrato. */
private fun buildingHeights(
    rng: Rng,
    mode: SkylineMode,
    n: Int,
): IntArray {
    val heights = IntArray(n)
    for (i in 0 until n) {
        val h =
            when (mode) {
                SkylineMode.ALEATORIO -> rng.nextIntRange(G.BUILD_H_MIN, G.BUILD_H_MAX)
                SkylineMode.ASCENDENTE -> ramp(i, n) + rng.nextIntRange(-HEIGHT_NOISE, HEIGHT_NOISE)
                SkylineMode.DESCENDENTE -> ramp(n - 1 - i, n) + rng.nextIntRange(-HEIGHT_NOISE, HEIGHT_NOISE)
            }
        heights[i] = h.coerceIn(G.BUILD_H_MIN, G.BUILD_H_MAX)
    }
    return heights
}

private fun ramp(
    i: Int,
    n: Int,
): Int {
    if (n <= 1) return G.BUILD_H_MIN
    val t = i.toFloat() / (n - 1)
    return (G.BUILD_H_MIN + t * (G.BUILD_H_MAX - G.BUILD_H_MIN)).toInt()
}

/** Número de ventanas que caben en una dimensión, con margen a ambos lados (D-07). */
private fun gridCount(
    span: Int,
    cellSize: Int = WIN_W,
): Int {
    val usable = span - 2 * WIN_MARGIN + WIN_GAP
    return if (usable <= 0) 0 else usable / (cellSize + WIN_GAP)
}

/** Vuelca edificios y ventanas sobre la máscara y la tabla de color (§7, paso 7). */
private fun paint(
    width: Int,
    buildings: List<Building>,
): Terrain {
    val mask = BooleanArray(width * G.H)
    val color = ByteArray(width * G.H)

    for (b in buildings) {
        val facade = Palette.facadeIndex(b.paletteIdx)
        for (y in b.roofY until G.H) {
            val row = y * width
            for (x in b.x until minOf(b.x + b.width, width)) {
                mask[row + x] = true
                color[row + x] = facade
            }
        }
        paintWindows(b, width, color)
    }
    return Terrain(width, mask, color, buildings)
}

private fun paintWindows(b: Building, width: Int, color: ByteArray) {
    if (b.windowCols == 0) return
    val rows = b.windows.size / b.windowCols
    for (r in 0 until rows) {
        val top = b.roofY + WIN_MARGIN + r * (WIN_H + WIN_GAP)
        for (c in 0 until b.windowCols) {
            val left = b.x + WIN_MARGIN + c * (WIN_W + WIN_GAP)
            val on = b.windows[r * b.windowCols + c]
            fillWindow(color, width, left, top, if (on) Palette.IDX_WINDOW_ON else Palette.IDX_WINDOW_OFF)
        }
    }
}

private fun fillWindow(color: ByteArray, width: Int, left: Int, top: Int, idx: Byte) {
    for (y in top until top + WIN_H) {
        if (y < 0 || y >= G.H) continue
        val row = y * width
        for (x in maxOf(0, left) until minOf(left + WIN_W, width)) {
            color[row + x] = idx
        }
    }
}

/**
 * Gorilas en los edificios 1 y n-2 (§7, paso 8), desplazados hacia el centro si el
 * edificio es más estrecho que el propio gorila. Es el fallo clásico de este juego
 * (§17.4), así que el desplazamiento lleva test dedicado.
 */
private fun placeGorillas(buildings: List<Building>): List<Gorilla> {
    val n = buildings.size
    val minWidth = G.GORILLA_W + GORILLA_CLEARANCE

    var left = 1
    while (left < n - 2 && buildings[left].width < minWidth) left++

    var right = n - 2
    while (right > left + 1 && buildings[right].width < minWidth) right--

    check(left != right) { "los dos gorilas caerían en el mismo edificio" }

    return listOf(
        Gorilla(0, buildings[left].x + buildings[left].width / 2, buildings[left].roofY),
        Gorilla(1, buildings[right].x + buildings[right].width / 2, buildings[right].roofY),
    )
}
