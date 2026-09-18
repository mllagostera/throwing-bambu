package com.vansid.panda.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The interface palette (§14).
 *
 * Until this file existed the game ran on `MaterialTheme {}` with no argument, so every
 * button, chip, slider and surface painted Material's baseline violet — a colour nobody
 * chose and which shares nothing with the sixteen the playfield is drawn from.
 *
 * The rule here is one line long and every value below follows it: **keep the EGA hue,
 * cut the saturation to about a third, and pick the lightness by role.** The chrome then
 * belongs to the same world as the playfield without competing with it, which is exactly
 * what `docs/SPRITE_SPEC.md` §10 asked for.
 *
 * Which hue plays which part is not arbitrary either:
 *
 * | Role | EGA source | Why |
 * |---|---|---|
 * | surfaces | blue (1), the sky | the menus sit on the same ground the match does |
 * | primary | light green (10), the cane | the accent is the thing the player is aiming |
 * | secondary | yellow (14), the lit windows | the playfield's other signal colour |
 * | tertiary | cyan (3), a facade | needed a third hue; no other role claims it |
 * | error | light red (12) | kept a little more saturated than the rule, on purpose |
 *
 * The scheme is dark and there is no light variant. The game is played over a deep blue
 * sky in landscape, and a light interface would mean the menus flashing white between
 * two dark screens.
 *
 * Contrast was measured, not eyeballed. Every on-pair clears 6.8:1 and the worst pair on
 * `surface` is `outline` at 5.1:1; for what the bars do over a live playfield see
 * [playfieldScrim].
 */
@Composable
fun BambuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = bambuColorScheme, content = content)
}

/**
 * Alpha of the HUD and controls bars over the playfield.
 *
 * It used to be 0.45, which was measured against the sky and never against the rest of
 * the game: the sun is yellow and sits at y=0, directly behind the HUD, and a panda on a
 * tall building reaches into it too. White text over a 45 % black scrim on yellow is
 * 3.1:1 — below the floor. At 0.75 the worst ground in the game leaves 7.4:1 for
 * [ColorScheme.onSurface] and 4.9:1 for [ColorScheme.onSurfaceVariant].
 *
 * The bars are darker for it. That is the trade: the playfield is the point of the
 * screen, but a score nobody can read is worse than a slightly dimmer sun.
 */
const val SCRIM_ALPHA = 0.75f

/**
 * Ground of the HUD and controls bars.
 *
 * The darkest surface rather than pure black, so the bars stay in the scheme's blue
 * family instead of punching a neutral hole in it.
 */
val ColorScheme.playfieldScrim: Color
    get() = surfaceContainerLowest.copy(alpha = SCRIM_ALPHA)

/**
 * The lightness ladder of the surfaces, all at the sky's hue.
 *
 * Every literal is the result of the rule in [BambuTheme]'s documentation; they are
 * written out rather than computed so that what ships is what was measured.
 */
private val bambuColorScheme: ColorScheme =
    darkColorScheme(
        // Light green (10) at a third of its saturation: the bamboo cane.
        primary = Color(0xFF92C992),
        onPrimary = Color(0xFF112711),
        primaryContainer = Color(0xFF2F502F),
        onPrimaryContainer = Color(0xFFD1EBD1),
        inversePrimary = Color(0xFF2E6B2E),
        // Yellow (14): the lit windows.
        secondary = Color(0xFFCECEA1),
        onSecondary = Color(0xFF2B2B12),
        secondaryContainer = Color(0xFF525232),
        onSecondaryContainer = Color(0xFFEEEED8),
        // Cyan (3): the first facade.
        tertiary = Color(0xFF95C6C6),
        onTertiary = Color(0xFF112727),
        tertiaryContainer = Color(0xFF304F4F),
        onTertiaryContainer = Color(0xFFD5ECEC),
        // Light red (12), held a little above the rule's saturation so a wrong value reads
        // as wrong at a glance rather than as decoration.
        error = Color(0xFFD89797),
        onError = Color(0xFF2B1212),
        errorContainer = Color(0xFF562E2E),
        onErrorContainer = Color(0xFFF1DADA),
        // Blue (1): the sky, and with it every surface in the game.
        background = Color(0xFF131326),
        onBackground = Color(0xFFE8E8F2),
        surface = Color(0xFF131326),
        onSurface = Color(0xFFE8E8F2),
        surfaceVariant = Color(0xFF343455),
        onSurfaceVariant = Color(0xFFBEBED0),
        surfaceTint = Color(0xFF92C992),
        surfaceDim = Color(0xFF0D0D1B),
        surfaceBright = Color(0xFF2D2D4D),
        surfaceContainerLowest = Color(0xFF0B0B18),
        surfaceContainerLow = Color(0xFF18182F),
        surfaceContainer = Color(0xFF1E1E39),
        surfaceContainerHigh = Color(0xFF282848),
        surfaceContainerHighest = Color(0xFF323257),
        inverseSurface = Color(0xFFE0E0EB),
        inverseOnSurface = Color(0xFF1B1B32),
        outline = Color(0xFF8585A3),
        outlineVariant = Color(0xFF41415D),
        scrim = Color(0xFF0B0B18),
    )
