package dev.bambu.app.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.bambu.core.G
import dev.bambu.core.Palette
import dev.bambu.core.Terrain

/**
 * The terrain as an ARGB bitmap, sky included (§13).
 *
 * The sky lives in this buffer rather than in a `Brush` behind the canvas so the
 * gradient is quantised to whole logical rows: one colour per row, no smooth blend that
 * would break the pixel look. It also leaves the canvas with a single `drawImage`.
 *
 * The bitmap is rebuilt **only** when a crater is applied, and even then only the
 * affected rectangle is rewritten. Regenerating it per frame is what sinks performance
 * on low-end devices (§17.5).
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
        for (y in 0 until G.H) {
            val sky = skyColor(y)
            val row = y * width
            for (x in 0 until width) {
                pixels[row + x] = pixelAt(row + x, sky)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, G.H)
    }

    /**
     * Repaints the rectangle a crater touched. The engine has already cleared the mask,
     * so this only has to read it back.
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
            val sky = skyColor(y)
            val row = y * width
            for (x in left..right) {
                pixels[row + x] = pixelAt(row + x, sky)
            }
        }
        bitmap.setPixels(pixels, top * width + left, width, left, top, w, h)
        version++
    }

    private fun pixelAt(
        index: Int,
        sky: Int,
    ): Int = if (terrain.mask[index]) Palette.argb(terrain.color[index]) else sky

    /** One colour per logical row, interpolated between the two colours of `sky.txt`. */
    private fun skyColor(y: Int): Int {
        val t = y.toFloat() / (G.H - 1)
        return lerpArgb(Palette.SKY_TOP, Palette.SKY_BOTTOM, t)
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
