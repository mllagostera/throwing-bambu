package com.vansid.panda.app.ui.icon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The interface icons (`docs/SPRITE_SPEC.md` §10): 24 dp, 2 dp stroke, round caps and
 * joins, nothing filled.
 *
 * They are **vectors, not pixel art**, and that is the point: the icons live in the dp
 * half of the system, beside the text, and have to stay sharp at whatever density the
 * phone has. Drawing them as sprites would tie them to the playfield's integer scale,
 * which is the one thing the interface does not share with it.
 *
 * Every glyph is built from straight strokes on the same 24-unit grid, so they read as
 * one family and none of them needs a fill rule. `Icon` tints the result, so the black
 * declared in [stroked] is never what ships.
 *
 * A note on the gear that is not here: at 24 dp with a 2 dp stroke a ring with teeth
 * collapses into a black cog — the same failure the sun's rays hit in the pixel art, for
 * the same reason. [BambuIcons.Settings] is drawn as two faders instead, which is also
 * what the screen it opens actually contains.
 */
object BambuIcons {
    /**
     * Back, on the Settings screen.
     *
     * The only icon here that mirrors: the manifest declares `supportsRtl`, and an arrow
     * meaning "back" points the other way in a right-to-left layout. Bluetooth and the
     * speaker do not — they are marks, not directions.
     */
    val Back: ImageVector =
        stroked("Back", autoMirror = true) {
            moveTo(20f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }

    /** Settings: two faders. See the note on the missing gear above. */
    val Settings: ImageVector =
        stroked("Settings") {
            moveTo(3f, 9f)
            lineTo(21f, 9f)
            moveTo(9f, 6f)
            lineTo(9f, 12f)
            moveTo(3f, 15f)
            lineTo(21f, 15f)
            moveTo(15f, 12f)
            lineTo(15f, 18f)
        }

    /** Sound on. Carried by the music and effects chips in settings (T-51). */
    val SoundOn: ImageVector =
        stroked("SoundOn") {
            speaker()
            moveTo(15f, 9f)
            lineTo(17f, 12f)
            lineTo(15f, 15f)
            moveTo(19f, 6f)
            lineTo(22f, 12f)
            lineTo(19f, 18f)
        }

    /** Sound off, the mute state of the same control. */
    val SoundOff: ImageVector =
        stroked("SoundOff") {
            speaker()
            moveTo(16f, 9f)
            lineTo(21f, 15f)
            moveTo(21f, 9f)
            lineTo(16f, 15f)
        }

    /**
     * Bluetooth, on the menu entry that is still disabled.
     *
     * The rune in one polyline: in from the lower left, across to the lower point, up the
     * stem, out to the upper point and back across. Two triangles sharing a stem, which is
     * what the mark is.
     */
    val Bluetooth: ImageVector =
        stroked("Bluetooth") {
            moveTo(7f, 7.5f)
            lineTo(17f, 16.5f)
            lineTo(12f, 21f)
            lineTo(12f, 3f)
            lineTo(17f, 7.5f)
            lineTo(7f, 16.5f)
        }
}

/** The body both sound icons share, so the two states differ only by what follows it. */
private fun PathBuilder.speaker() {
    moveTo(4f, 10f)
    lineTo(7f, 10f)
    lineTo(11f, 6f)
    lineTo(11f, 18f)
    lineTo(7f, 14f)
    lineTo(4f, 14f)
    close()
}

/**
 * One stroked path on the 24-unit grid.
 *
 * The stroke is declared once here rather than per icon: a family whose members carry
 * their own weights stops being a family.
 */
private fun stroked(
    name: String,
    autoMirror: Boolean = false,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        ).build()

/**
 * One of these icons in front of a button's own label.
 *
 * Drawn at the 24 dp they are designed on rather than at Material's 18 dp for this slot:
 * scaling them down would thin the 2 dp stroke the whole family shares, and the buttons
 * that take an icon are full-width in a landscape menu with room to spare.
 *
 * No content description: the label beside it already says what the button does, and one
 * here would have a screen reader announce it twice.
 */
@Composable
internal fun ButtonIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
}
