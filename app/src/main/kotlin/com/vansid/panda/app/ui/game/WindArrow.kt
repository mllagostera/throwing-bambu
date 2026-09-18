package com.vansid.panda.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vansid.panda.app.R
import kotlin.math.abs

/**
 * The wind indicator (`docs/SPRITE_SPEC.md` §10, `docs/DEVELOPMENT_SPEC.md` §14).
 *
 * An arrow whose shaft grows with the magnitude and whose head points the way the wind
 * pushes. It replaces the row of repeated `→` characters that stood in for it: those were
 * honest about being a placeholder, but they grew in steps of three units, so wind 4 and
 * wind 6 drew the same thing.
 *
 * Vector, drawn at the same 2 dp weight as [com.vansid.panda.app.ui.icon.BambuIcons], because it
 * belongs to the same half of the system as they do. The numeric value stays beside it in
 * the HUD: the arrow is read at a glance, the number is what a player aiming into a
 * headwind actually counts on.
 *
 * The box is a fixed [arrowWidth] whatever the wind, so the HUD does not reflow when the
 * value changes between rounds. The shaft leaves from the centre of that box, which means
 * the arrow also says which half of the screen it is pushing towards.
 */
@Composable
fun WindArrow(
    wind: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val label = stringResource(R.string.game_wind, wind)
    Canvas(
        modifier =
            modifier
                .size(width = arrowWidth, height = arrowHeight)
                .semantics { contentDescription = label },
    ) {
        val stroke = strokeWeight.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)

        if (wind == 0) {
            val half = calmHalfWidth.toPx()
            drawLine(
                color = color,
                start = centre.copy(x = centre.x - half),
                end = centre.copy(x = centre.x + half),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val direction = if (wind > 0) 1f else -1f
        val tip = centre.copy(x = centre.x + direction * (shaftBase + shaftPerUnit * abs(wind)).toPx())
        val barbX = tip.x - direction * headLength.toPx()
        val barbY = headSpread.toPx()

        for (end in listOf(centre, Offset(barbX, centre.y - barbY), Offset(barbX, centre.y + barbY))) {
            drawLine(
                color = color,
                start = tip,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Wide enough for the longest arrow the game can produce and never narrower, so the HUD
 * keeps its columns when the wind changes.
 */
private val arrowWidth = 56.dp

/** Tall enough for the head's spread plus the stroke, and no taller: the HUD is a thin bar. */
private val arrowHeight = 16.dp

/** The weight the whole dp half of the system is drawn at. */
private val strokeWeight = 2.dp

/** Shaft at wind ±1. Short, but long enough to carry a head and read as an arrow. */
private val shaftBase = 6.dp

/**
 * Growth per unit of wind. At the maximum of ±10 the shaft reaches 26 dp, which with the
 * stroke's round cap lands one dp inside [arrowWidth]'s half.
 */
private val shaftPerUnit = 2.dp

/** Barb length, measured back along the shaft. */
private val headLength = 4.dp

/** Half the barb's opening. With [headLength] this puts the head at about 35°. */
private val headSpread = 3.dp

/** Half the dash drawn in place of an arrow when there is no wind at all. */
private val calmHalfWidth = 5.dp
