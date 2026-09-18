package com.vansid.panda.app.render

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A horizontal strip of equally sized frames.
 *
 * Every sheet in `art/` is 1x and laid out left to right, one row (`art/README.md`).
 */
class SpriteSheet(
    val image: ImageBitmap,
    val cellW: Int,
    val cellH: Int,
) {
    val frames: Int = image.width / cellW

    /** Left edge of a frame inside the sheet, clamped so a bad index cannot crash a draw. */
    fun srcX(frame: Int): Int = (frame.coerceIn(0, frames - 1)) * cellW
}

/**
 * The delivered art, decoded once.
 *
 * `inScaled = false` is not optional: without it the resource/density machinery would
 * resample the bitmaps and destroy the nearest-neighbour look the whole pipeline exists
 * to protect (AGENTS.md, §13).
 */
class GameSprites(
    val panda: SpriteSheet,
    val bamboo: SpriteSheet,
    val boom: SpriteSheet,
    val sun: SpriteSheet,
    val skyline: ImageBitmap,
    val logo: ImageBitmap,
) {
    companion object {
        // Cell sizes are contract, taken from the delivery inventory in art/README.md.
        private const val PANDA_CELL = 24
        private const val BAMBOO_CELL = 8
        private const val BOOM_CELL = 32
        private const val SUN_CELL = 20

        fun load(assets: AssetManager): GameSprites =
            GameSprites(
                panda = sheet(assets, "panda.png", PANDA_CELL, PANDA_CELL),
                bamboo = sheet(assets, "bamboo.png", BAMBOO_CELL, BAMBOO_CELL),
                boom = sheet(assets, "boom.png", BOOM_CELL, BOOM_CELL),
                sun = sheet(assets, "sun.png", SUN_CELL, SUN_CELL),
                skyline = image(assets, "skyline.png"),
                logo = image(assets, "logo.png"),
            )

        private fun sheet(
            assets: AssetManager,
            name: String,
            w: Int,
            h: Int,
        ) = SpriteSheet(image(assets, name), w, h)

        private fun image(
            assets: AssetManager,
            name: String,
        ): ImageBitmap {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap =
                assets.open("sprites/$name").use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            requireNotNull(bitmap) { "could not decode sprites/$name" }
            return bitmap.asImageBitmap()
        }
    }
}

/** Panda poses, in the order `panda.png` delivers them (`art/README.md`). */
object PandaFrame {
    const val IDLE = 0
    const val ARM_LEFT = 1
    const val ARM_RIGHT = 2
    const val CHEST_1 = 3
    const val CHEST_2 = 4
    const val DEFEATED = 5

    /**
     * The throwing pose for a player. Player 0 stands on the left and throws to the
     * right, so it raises its right arm; player 1 mirrors that.
     */
    fun throwing(player: Int): Int = if (player == 0) ARM_RIGHT else ARM_LEFT
}

/**
 * Decodes the sheets once per composition tree.
 *
 * They are a few kilobytes in total and immutable, so there is no reason to reload them
 * on every recomposition — or to build a cache for something the composition already
 * keeps alive.
 */
@androidx.compose.runtime.Composable
fun rememberGameSprites(): GameSprites {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) { GameSprites.load(context.assets) }
}
