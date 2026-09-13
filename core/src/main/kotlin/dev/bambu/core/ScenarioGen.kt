package dev.bambu.core

/** Skyline profile (§7, step 2). The order of the constants is contract. */
private enum class SkylineMode { ASCENDING, DESCENDING, RANDOM }

// Window grid (D-07). Pinning these values is mandatory: they change how many calls the
// RNG takes and therefore every seed.
private const val WIN_W = 3
private const val WIN_H = 4
private const val WIN_MARGIN = 3
private const val WIN_GAP = 3
private const val WIN_ON_THRESHOLD = 0.5f

/** Height noise in the ascending and descending profiles (§7, step 4). */
private const val HEIGHT_NOISE = 15

/** Minimum roof clearance for a panda to fit (§7, step 8). */
private const val PANDA_CLEARANCE = 4

/** With fewer buildings there is no room for two pandas on different ones. */
private const val MIN_BUILDINGS = 4

/**
 * Generates the scenario for one round (§7).
 *
 * The order of the RNG calls **is** the algorithm: reordering any step changes every
 * scenario already played and breaks compatibility between versions. The phases are
 * kept separate exactly as the specification numbers them — first all the heights, then
 * all the palettes, then all the windows — not interleaved per building.
 */
fun generate(
    seed: Long,
    width: Int,
): Scenario {
    require(width in G.W_MIN..G.W_MAX) { "width outside the logical range: $width" }

    val rng = Rng(seed)
    val mode = SkylineMode.entries[rng.nextInt(SkylineMode.entries.size)]
    val widths = buildingWidths(rng, width)
    val n = widths.size
    require(n >= MIN_BUILDINGS) { "scenario with $n buildings: two pandas cannot be kept apart" }

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
    val pandas = placePandas(buildings)
    return Scenario(width, terrain, pandas, width / 2)
}

/**
 * Widths accumulated until the canvas is covered (§7, step 3). The last building is
 * trimmed to the remaining space; if that remainder is below the minimum it is added to
 * the previous one, which may then exceed `BUILD_W_MAX`. That is the literal reading of
 * the specification.
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

/** Heights following the profile (§7, step 4), clamped to the contract range. */
private fun buildingHeights(
    rng: Rng,
    mode: SkylineMode,
    n: Int,
): IntArray {
    val heights = IntArray(n)
    for (i in 0 until n) {
        val h =
            when (mode) {
                SkylineMode.RANDOM -> rng.nextIntRange(G.BUILD_H_MIN, G.BUILD_H_MAX)
                SkylineMode.ASCENDING -> ramp(i, n) + rng.nextIntRange(-HEIGHT_NOISE, HEIGHT_NOISE)
                SkylineMode.DESCENDING -> ramp(n - 1 - i, n) + rng.nextIntRange(-HEIGHT_NOISE, HEIGHT_NOISE)
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

/** How many windows fit along one dimension, with a margin on both sides (D-07). */
private fun gridCount(
    span: Int,
    cellSize: Int = WIN_W,
): Int {
    val usable = span - 2 * WIN_MARGIN + WIN_GAP
    return if (usable <= 0) 0 else usable / (cellSize + WIN_GAP)
}

/** Pours buildings and windows onto the mask and the colour table (§7, step 7). */
private fun paint(
    width: Int,
    buildings: List<Building>,
): Terrain {
    val mask = BooleanArray(width * G.H)
    val color = ByteArray(width * G.H)

    for (b in buildings) {
        val facade = Palette.FACADES[b.paletteIdx]
        for (y in b.roofY until G.H) {
            val row = y * width
            for (x in b.x until minOf(b.x + b.width, width)) {
                mask[row + x] = true
                color[row + x] = facade.base
            }
        }
        paintWindows(b, facade, width, color)
    }
    return Terrain(width, mask, color, buildings)
}

private fun paintWindows(
    b: Building,
    facade: Palette.Facade,
    width: Int,
    color: ByteArray,
) {
    if (b.windowCols == 0) return
    val rows = b.windows.size / b.windowCols
    for (r in 0 until rows) {
        val top = b.roofY + WIN_MARGIN + r * (WIN_H + WIN_GAP)
        for (c in 0 until b.windowCols) {
            val left = b.x + WIN_MARGIN + c * (WIN_W + WIN_GAP)
            val on = b.windows[r * b.windowCols + c]
            fillWindow(color, width, left, top, if (on) facade.lit else facade.unlit)
        }
    }
}

private fun fillWindow(
    color: ByteArray,
    width: Int,
    left: Int,
    top: Int,
    idx: Byte,
) {
    for (y in top until top + WIN_H) {
        if (y < 0 || y >= G.H) continue
        val row = y * width
        for (x in maxOf(0, left) until minOf(left + WIN_W, width)) {
            color[row + x] = idx
        }
    }
}

/**
 * Pandas on buildings 1 and n-2 (§7, step 8), shifted towards the centre if the
 * building is narrower than the panda itself. That is the classic bug of this game
 * (§17.4), so the shift has a dedicated test.
 */
private fun placePandas(buildings: List<Building>): List<Panda> {
    val n = buildings.size
    val minWidth = G.PANDA_W + PANDA_CLEARANCE

    var left = 1
    while (left < n - 2 && buildings[left].width < minWidth) left++

    var right = n - 2
    while (right > left + 1 && buildings[right].width < minWidth) right--

    check(left != right) { "both pandas would land on the same building" }

    return listOf(
        Panda(0, buildings[left].x + buildings[left].width / 2, buildings[left].roofY),
        Panda(1, buildings[right].x + buildings[right].width / 2, buildings[right].roofY),
    )
}
