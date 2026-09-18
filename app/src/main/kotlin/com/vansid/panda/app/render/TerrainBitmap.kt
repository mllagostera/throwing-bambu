package com.vansid.panda.app.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vansid.panda.core.G
import com.vansid.panda.core.Palette
import com.vansid.panda.core.Terrain

/**
 * The terrain as an ARGB bitmap (§13).
 *
 * Everything that is not solid is left **transparent**, not sky-coloured: the skyline
 * band is drawn behind the buildings, so the terrain layer has to let it through.
 *
 * The bitmap is patched when a crater is applied and never rebuilt per frame, which is
 * the difference between 60 fps and a slideshow on a low-end device (§17.5).
 */
class TerrainBitmap(
    private val terrain: Terrain,
) {
    private val width = terrain.width
    private val pixels = IntArray(width * G.H)
    private val bitmap: Bitmap = Bitmap.createBitmap(width, G.H, Bitmap.Config.ARGB_8888)

    /** Bumped on every patch so Compose knows the pixels changed under the same object. */
    var version: Int = 0
        private set

    val image: ImageBitmap get() = bitmap.asImageBitmap()

    init {
        for (i in pixels.indices) {
            pixels[i] = pixelAt(i)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, G.H)
    }

    /**
     * Repaints the rectangle a crater touched. The engine has already cleared the mask,
     * so this only reads it back.
     */
    fun patch(
        cx: Int,
        cy: Int,
        r: Int,
    ) {
        val left = (cx - r).coerceIn(0, width - 1)
        val right = (cx + r).coerceIn(0, width - 1)
        val top = (cy - r).coerceIn(0, G.H - 1)
        val bottom = (cy + r).coerceIn(0, G.H - 1)
        val w = right - left + 1
        val h = bottom - top + 1
        if (w <= 0 || h <= 0) return

        for (y in top..bottom) {
            val row = y * width
            for (x in left..right) {
                pixels[row + x] = pixelAt(row + x)
            }
        }
        bitmap.setPixels(pixels, top * width + left, width, left, top, w, h)
        version++
    }

    private fun pixelAt(index: Int): Int = if (terrain.mask[index]) Palette.argb(terrain.color[index]) else TRANSPARENT

    private companion object {
        const val TRANSPARENT = 0
    }
}

/**
 * The sky as a one-pixel-wide column, stretched across the canvas with nearest-neighbour.
 *
 * One colour per logical row, interpolated between the two colours of `art/sky.txt`.
 * Building it as a bitmap rather than a `Brush` keeps the gradient quantised to whole
 * rows — a smooth blend would be the one soft edge in an otherwise hard-pixel screen.
 */
object SkyGradient {
    val image: ImageBitmap by lazy {
        val pixels = IntArray(G.H) { y -> lerpArgb(Palette.SKY_TOP, Palette.SKY_BOTTOM, y.toFloat() / (G.H - 1)) }
        val bitmap = Bitmap.createBitmap(1, G.H, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, 1, 0, 0, 1, G.H)
        bitmap.asImageBitmap()
    }

    private fun lerpArgb(
        from: Int,
        to: Int,
        t: Float,
    ): Int {
        val r = lerpChannel(from shr 16 and 0xFF, to shr 16 and 0xFF, t)
        val g = lerpChannel(from shr 8 and 0xFF, to shr 8 and 0xFF, t)
        val b = lerpChannel(from and 0xFF, to and 0xFF, t)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(
        from: Int,
        to: Int,
        t: Float,
    ): Int = (from + (to - from) * t).toInt().coerceIn(0, 255)
}
