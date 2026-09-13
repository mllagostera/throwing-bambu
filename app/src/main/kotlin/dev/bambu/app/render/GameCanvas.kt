package dev.bambu.app.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.bambu.core.G
import dev.bambu.core.Palette
import dev.bambu.core.Scenario
import dev.bambu.core.logicalScale

/** Trail length behind the cane, in trajectory points (§13). */
private const val TRAIL_POINTS = 12

/** The skyline band sits 45 px above the base of the buildings (`art/README.md`). */
private const val SKYLINE_BOTTOM_OFFSET = 45

/**
 * Everything the canvas needs for one frame. Grouped into one object because a draw has
 * to show a single instant: a mix of old and new values reads as a glitch.
 */
data class GameFrame(
    val scenario: Scenario,
    val terrain: TerrainBitmap,
    val terrainVersion: Int,
    val canePoint: Offset?,
    val caneFrame: Int,
    val trail: List<Offset>,
    val boom: BoomState?,
    val sunOuch: Boolean,
    val pandaPoses: Map<Int, Int>,
)

/** An explosion in progress, in logical coordinates. */
data class BoomState(
    val x: Int,
    val y: Int,
    val frame: Int,
)

/**
 * Draws one frame of the match with the delivered sprites (§13).
 *
 * Layer order matters: sky, then the skyline band, then the terrain — which is
 * transparent wherever there is no building, so the band shows through behind it.
 */
@Composable
fun GameCanvas(
    frame: GameFrame,
    sprites: GameSprites,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // The scale comes from core (§3): the renderer never computes its own.
        val scale = logicalScale(size.width.toInt(), size.height.toInt())
        val view =
            Viewport(
                scale = scale,
                originX = ((size.width - frame.scenario.width * scale) / 2f).toInt(),
                originY = ((size.height - G.H * scale) / 2f).toInt(),
            )

        // Side bars beyond the logical canvas take the colour of the top of the sky.
        drawRect(color = Color(Palette.SKY_TOP), topLeft = Offset.Zero, size = size)

        drawScaled(SkyGradient.image, 0, 0, 1, G.H, 0, 0, frame.scenario.width, G.H, view)
        drawSkyline(sprites.skyline, frame.scenario.width, view)

        // Read so a crater forces this draw to run again under the same bitmap object.
        @Suppress("UNUSED_EXPRESSION")
        frame.terrainVersion

        drawScaled(
            frame.terrain.image,
            0,
            0,
            frame.scenario.width,
            G.H,
            0,
            0,
            frame.scenario.width,
            G.H,
            view,
        )

        drawSun(sprites, frame, view)
        drawPandas(sprites, frame, view)
        drawTrail(frame.trail, view)
        drawCane(sprites, frame, view)
        drawBoom(sprites, frame, view)
    }
}

/** Integer scale plus the offset that centres the logical canvas on the screen. */
private data class Viewport(
    val scale: Int,
    val originX: Int,
    val originY: Int,
)

private fun DrawScope.drawSkyline(
    skyline: ImageBitmap,
    canvasWidth: Int,
    view: Viewport,
) {
    // 460 px wide, cropped from the right to the real canvas width (`art/README.md`).
    val w = minOf(skyline.width, canvasWidth)
    val h = skyline.height
    val top = G.H - SKYLINE_BOTTOM_OFFSET - h
    drawScaled(skyline, 0, 0, w, h, 0, top, w, h, view)
}

private fun DrawScope.drawSun(
    sprites: GameSprites,
    frame: GameFrame,
    view: Viewport,
) {
    val sheet = sprites.sun
    val sunFrame = if (frame.sunOuch) 1 else 0
    drawScaled(
        sheet.image,
        sheet.srcX(sunFrame),
        0,
        sheet.cellW,
        sheet.cellH,
        frame.scenario.sunX - sheet.cellW / 2,
        0,
        sheet.cellW,
        sheet.cellH,
        view,
    )
}

/**
 * The panda's pivot is (12,24): the bottom centre of its 24×24 cell, which lands on the
 * roof pixel its feet rest on. The body occupies 16×20 inside that cell, exactly the box
 * physics collides against.
 */
private fun DrawScope.drawPandas(
    sprites: GameSprites,
    frame: GameFrame,
    view: Viewport,
) {
    val sheet = sprites.panda
    for (panda in frame.scenario.pandas) {
        val pose =
            when {
                !panda.alive -> PandaFrame.DEFEATED
                else -> frame.pandaPoses[panda.player] ?: PandaFrame.IDLE
            }
        drawScaled(
            sheet.image,
            sheet.srcX(pose),
            0,
            sheet.cellW,
            sheet.cellH,
            panda.x - sheet.cellW / 2,
            panda.roofY - sheet.cellH,
            sheet.cellW,
            sheet.cellH,
            view,
        )
    }
}

private fun DrawScope.drawCane(
    sprites: GameSprites,
    frame: GameFrame,
    view: Viewport,
) {
    val point = frame.canePoint ?: return
    val sheet = sprites.bamboo
    drawScaled(
        sheet.image,
        sheet.srcX(frame.caneFrame),
        0,
        sheet.cellW,
        sheet.cellH,
        point.x.toInt() - sheet.cellW / 2,
        point.y.toInt() - sheet.cellH / 2,
        sheet.cellW,
        sheet.cellH,
        view,
    )
}

private fun DrawScope.drawBoom(
    sprites: GameSprites,
    frame: GameFrame,
    view: Viewport,
) {
    val boom = frame.boom ?: return
    val sheet = sprites.boom
    drawScaled(
        sheet.image,
        sheet.srcX(boom.frame),
        0,
        sheet.cellW,
        sheet.cellH,
        boom.x - sheet.cellW / 2,
        boom.y - sheet.cellH / 2,
        sheet.cellW,
        sheet.cellH,
        view,
    )
}

private fun DrawScope.drawTrail(
    trail: List<Offset>,
    view: Viewport,
) {
    if (trail.isEmpty()) return
    val base = Color(Palette.argb(Palette.LIGHT_GREEN_INDEX))
    trail.takeLast(TRAIL_POINTS).forEachIndexed { i, p ->
        drawRect(
            color = base.copy(alpha = (i + 1f) / TRAIL_POINTS * 0.5f),
            topLeft =
                Offset(
                    (view.originX + p.x.toInt() * view.scale).toFloat(),
                    (view.originY + p.y.toInt() * view.scale).toFloat(),
                ),
            size =
                androidx.compose.ui.geometry
                    .Size(view.scale.toFloat(), view.scale.toFloat()),
        )
    }
}

/**
 * Blits a region of a sheet onto the logical canvas.
 *
 * `FilterQuality.None` on every call, with no exceptions (§13): any filtering here turns
 * the whole delivery soft, which is precisely what the 1x-only rule exists to prevent.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawScaled(
    image: ImageBitmap,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
    dstX: Int,
    dstY: Int,
    dstW: Int,
    dstH: Int,
    view: Viewport,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(view.originX + dstX * view.scale, view.originY + dstY * view.scale),
        dstSize = IntSize(dstW * view.scale, dstH * view.scale),
        filterQuality = FilterQuality.None,
    )
}
