package dev.bambu.app.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.bambu.core.G
import dev.bambu.core.Palette
import dev.bambu.core.Panda
import dev.bambu.core.Scenario
import dev.bambu.core.logicalScale

/** Length of the trail behind the cane, in trajectory points (§13). */
private const val TRAIL_POINTS = 12

/**
 * Draws one frame of the match (§13).
 *
 * M2 draws geometry: coloured rectangles standing in for the sprites, which arrive in
 * M4. What is already final is the pixel discipline — an integer scale from
 * [logicalScale], `FilterQuality.None` on the image, and every coordinate rounded to a
 * whole logical pixel before being scaled.
 */
@Composable
fun GameCanvas(
    scenario: Scenario,
    terrain: TerrainBitmap,
    canePoint: Offset?,
    trail: List<Offset>,
    sunHit: Boolean,
    terrainVersion: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // The scale comes from core (§3): the renderer never computes its own.
        val scale = logicalScale(size.width.toInt(), size.height.toInt())
        val canvasW = scenario.width * scale
        val canvasH = G.H * scale
        // Leftover space becomes sky-coloured bars, never stretching.
        val originX = ((size.width - canvasW) / 2f).toInt()
        val originY = ((size.height - canvasH) / 2f).toInt()

        drawRect(color = Color(Palette.SKY_TOP), topLeft = Offset.Zero, size = size)

        // `terrainVersion` is read so a crater forces this draw to run again.
        @Suppress("UNUSED_EXPRESSION")
        terrainVersion

        drawImage(
            image = terrain.image,
            dstOffset = IntOffset(originX, originY),
            dstSize = IntSize(canvasW, canvasH),
            filterQuality = FilterQuality.None,
        )

        drawSun(scenario, sunHit, scale, originX, originY)
        for (panda in scenario.pandas) {
            if (panda.alive) drawPanda(panda, scale, originX, originY)
        }
        drawTrail(trail, scale, originX, originY)
        canePoint?.let { drawCane(it, scale, originX, originY) }
    }
}

private fun DrawScope.drawSun(
    scenario: Scenario,
    hit: Boolean,
    scale: Int,
    ox: Int,
    oy: Int,
) {
    val left = scenario.sunX - G.SUN_W / 2
    drawLogicalRect(
        color = Color(Palette.argb(if (hit) Palette.LIGHT_RED_INDEX else Palette.YELLOW)),
        x = left,
        y = 0,
        w = G.SUN_W,
        h = G.SUN_H,
        scale = scale,
        ox = ox,
        oy = oy,
    )
}

/**
 * Placeholder body: exactly the 16×20 box physics collides against, so any mismatch
 * between what is drawn and what is hit shows up immediately instead of in M4.
 */
private fun DrawScope.drawPanda(
    panda: Panda,
    scale: Int,
    ox: Int,
    oy: Int,
) {
    val color = if (panda.player == 0) Color.White else Color(0xFFDDDDDD)
    drawLogicalRect(
        color = color,
        x = panda.x - G.PANDA_W / 2,
        y = panda.roofY - G.PANDA_H,
        w = G.PANDA_W,
        h = G.PANDA_H,
        scale = scale,
        ox = ox,
        oy = oy,
    )
    // A dark band so the two pandas read as pandas and not as blank slabs.
    drawLogicalRect(
        color = Color.Black,
        x = panda.x - G.PANDA_W / 2,
        y = panda.roofY - G.PANDA_H + 6,
        w = G.PANDA_W,
        h = 5,
        scale = scale,
        ox = ox,
        oy = oy,
    )
}

private fun DrawScope.drawCane(
    point: Offset,
    scale: Int,
    ox: Int,
    oy: Int,
) {
    drawLogicalRect(
        color = Color(Palette.argb(Palette.GREEN_INDEX)),
        x = point.x.toInt() - G.CANE_R,
        y = point.y.toInt() - G.CANE_R,
        w = G.CANE_R * 2,
        h = G.CANE_R * 2,
        scale = scale,
        ox = ox,
        oy = oy,
    )
}

private fun DrawScope.drawTrail(
    trail: List<Offset>,
    scale: Int,
    ox: Int,
    oy: Int,
) {
    if (trail.isEmpty()) return
    val base = Color(Palette.argb(Palette.LIGHT_GREEN_INDEX))
    trail.takeLast(TRAIL_POINTS).forEachIndexed { i, p ->
        val alpha = (i + 1f) / TRAIL_POINTS * 0.6f
        drawLogicalRect(
            color = base.copy(alpha = alpha),
            x = p.x.toInt(),
            y = p.y.toInt(),
            w = 1,
            h = 1,
            scale = scale,
            ox = ox,
            oy = oy,
        )
    }
}

/** Draws in logical pixels: everything lands on the integer grid, never between pixels. */
@Suppress("LongParameterList")
private fun DrawScope.drawLogicalRect(
    color: Color,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    scale: Int,
    ox: Int,
    oy: Int,
) {
    drawRect(
        color = color,
        topLeft = Offset((ox + x * scale).toFloat(), (oy + y * scale).toFloat()),
        size = Size((w * scale).toFloat(), (h * scale).toFloat()),
    )
}
