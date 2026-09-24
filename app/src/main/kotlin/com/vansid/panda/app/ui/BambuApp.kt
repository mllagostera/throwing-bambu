package com.vansid.panda.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vansid.panda.app.R
import com.vansid.panda.app.audio.rememberMusic
import com.vansid.panda.app.render.rememberGameSprites
import com.vansid.panda.app.settings.AppLocale
import com.vansid.panda.app.settings.LocalAudioSettings
import com.vansid.panda.app.settings.LocaleStore
import com.vansid.panda.app.settings.WithAppLocale
import com.vansid.panda.app.settings.WithAudioSettings
import com.vansid.panda.app.ui.bluetooth.bluetoothDestinations
import com.vansid.panda.app.ui.icon.BambuIcons
import com.vansid.panda.app.ui.icon.ButtonIcon
import com.vansid.panda.app.ui.theme.BambuTheme
import com.vansid.panda.core.AiLevel
import kotlin.math.round

/** The level slot carries this when there is no AI: two people at the same device. */
const val NO_LEVEL = "NONE"

internal const val ROUNDS_TO_WIN = 3

/**
 * Navigation (§14): `Menu → [mode] → Setup → Game → Result`, plus settings.
 *
 * The chosen language wraps the whole tree, so switching it repaints every screen at
 * once instead of only the one the change was made on.
 */
@Composable
fun BambuApp(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val store = remember { LocaleStore(context) }
    var locale by remember { mutableStateOf(store.locale) }

    WithAudioSettings {
        // At the root, so the tune runs across the whole app rather than restarting at
        // every navigation. It follows the lifecycle, not the composition (rememberMusic).
        val music = rememberMusic()
        val audio = LocalAudioSettings.current
        // Reading the setting inside the composition is what makes the toggle immediate:
        // flipping it recomposes here, and the assignment starts or stops the player on
        // that frame rather than at the next launch.
        music.enabled = audio.music

        WithAppLocale(locale) {
            BambuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BambuNavHost(
                        navController = navController,
                        locale = locale,
                        onLocalePicked = {
                            locale = it
                            store.locale = it
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BambuNavHost(
    navController: NavHostController,
    locale: AppLocale,
    onLocalePicked: (AppLocale) -> Unit,
) {
    NavHost(navController = navController, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            MenuScreen(
                onOnePlayer = { navController.navigate(Routes.setup(solo = true)) },
                onTwoPlayers = { navController.navigate(Routes.setup(solo = false)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                current = locale,
                onPick = onLocalePicked,
                onBack = { navController.popBackStack() },
            )
        }
        matchDestinations(navController)
        bluetoothDestinations(navController)
    }
}

object Routes {
    const val MENU = "menu"
    const val SETTINGS = "settings"
    const val BLUETOOTH = "bluetooth"
    const val PAIRING = "pairing"
    const val REMOTE_GAME = "remote-game"
    const val ARG_SEED = "seed"
    const val ARG_ROUNDS = "rounds"
    const val ARG_WINNER = "winner"
    const val ARG_LEVEL = "level"
    const val ARG_SOLO = "solo"
    const val ARG_MODE = "mode"
    const val SETUP = "setup/{$ARG_SOLO}"
    const val GAME = "game/{$ARG_SEED}/{$ARG_ROUNDS}/{$ARG_LEVEL}"
    const val RESULT = "result/{$ARG_WINNER}/{$ARG_MODE}"

    fun setup(solo: Boolean) = "setup/$solo"

    fun game(
        seed: Long,
        rounds: Int,
        level: AiLevel?,
    ) = "game/$seed/$rounds/${level?.name ?: NO_LEVEL}"

    fun result(
        winner: Int,
        mode: MatchMode,
    ) = "result/$winner/${mode.name}"

    /** Where a match of this kind is started, for the result screen to return to. */
    fun playAgain(mode: MatchMode) =
        when (mode) {
            MatchMode.SOLO -> setup(solo = true)
            MatchMode.LOCAL -> setup(solo = false)
            MatchMode.BLUETOOTH -> BLUETOOTH
        }
}

@Composable
private fun MenuScreen(
    onOnePlayer: () -> Unit,
    onTwoPlayers: () -> Unit,
    onSettings: () -> Unit,
) {
    // Landscape is the only orientation this game runs in (D-04), so the menu is laid
    // out for it: logo on one side, choices on the other. Stacked vertically, the last
    // button was pushed off the bottom of the screen.
    //
    // The sizes below are a phone's and stay a phone's. On a bigger screen they are not
    // rewritten but redrawn, by the scale `ScreenScaffold` puts on the density.
    ScreenScaffold { wide ->
        val buttons: @Composable ColumnScope.() -> Unit = {
            MenuButton(stringResource(R.string.menu_one_player), onClick = onOnePlayer)
            MenuButton(stringResource(R.string.menu_two_players), onClick = onTwoPlayers)
            // The Bluetooth entry is withdrawn until the mode works on real devices (T-47).
            // Its destinations stay registered, so restoring it is this button and its
            // callback, nothing more.
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                ButtonIcon(BambuIcons.Settings)
                Text(stringResource(R.string.menu_settings))
            }
        }

        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PixelLogo(maxWidth = 320.dp, maxHeight = 160.dp)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = buttons,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PixelLogo(maxWidth = 280.dp, maxHeight = 120.dp)
                buttons()
            }
        }
    }
}

/**
 * Settings. Only the language for now (T-50); sound and speed join it in M7.
 *
 * Every language is listed under its own name, so the way out of the wrong one is
 * always readable.
 */
@Composable
private fun SettingsScreen(
    current: AppLocale,
    onPick: (AppLocale) -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)

            // On its own line, above the five languages, because it is a different kind of
            // answer: follow the phone, or name a language. No Row around it — one child
            // in a `fillMaxWidth` Row is stretched to the screen and then laid out from
            // its start, which is how this chip ended up pinned to the left edge while
            // everything else on the screen was centred.
            FilterChip(
                selected = current == AppLocale.SYSTEM,
                onClick = { onPick(AppLocale.SYSTEM) },
                label = { Text(stringResource(R.string.settings_language_system)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in AppLocale.entries.filter { it != AppLocale.SYSTEM }) {
                    FilterChip(
                        selected = current == option,
                        onClick = { onPick(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            Text(stringResource(R.string.settings_audio), style = MaterialTheme.typography.titleMedium)
            AudioChips()

            Button(onClick = onBack) {
                ButtonIcon(BambuIcons.Back)
                Text(stringResource(R.string.settings_back))
            }
        }
    }
}

/**
 * The two audio switches (T-51).
 *
 * Chips rather than `Switch` rows, to match the language above them: a settings screen
 * that answers two questions in two different shapes makes the reader work out twice
 * whether they have understood the control.
 *
 * Each carries its own state in its icon as well as in the chip's selection, because the
 * selected/unselected difference alone is a tint — and a player deciding whether the
 * music is off wants to read that, not compare two shades of green.
 */
@Composable
private fun AudioChips() {
    val audio = LocalAudioSettings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = audio.music,
            onClick = { audio.music = !audio.music },
            label = { Text(stringResource(R.string.settings_music)) },
            leadingIcon = { ButtonIcon(if (audio.music) BambuIcons.SoundOn else BambuIcons.SoundOff) },
        )
        FilterChip(
            selected = audio.sound,
            onClick = { audio.sound = !audio.sound },
            label = { Text(stringResource(R.string.settings_effects)) },
            leadingIcon = { ButtonIcon(if (audio.sound) BambuIcons.SoundOn else BambuIcons.SoundOff) },
        )
    }
}

/**
 * The logo at a whole-number scale.
 *
 * `ContentScale.FillWidth` would stretch it to whatever fraction the screen happens to
 * need, which is the one thing this game does not do to a sprite (§0.4). Here the
 * largest integer multiple that fits is chosen instead, so every source pixel stays a
 * square block.
 */
@Composable
private fun PixelLogo(
    maxWidth: Dp,
    maxHeight: Dp,
) {
    val logo = rememberGameSprites().logo
    val density = LocalDensity.current
    val scale =
        with(density) {
            minOf(
                (maxWidth.toPx() / logo.width).toInt(),
                (maxHeight.toPx() / logo.height).toInt(),
            ).coerceAtLeast(1)
        }
    Image(
        painter = BitmapPainter(image = logo, filterQuality = FilterQuality.None),
        contentDescription = stringResource(R.string.app_name),
        modifier =
            Modifier.size(
                width = with(density) { (logo.width * scale).toDp() },
                height = with(density) { (logo.height * scale).toDp() },
            ),
    )
}

/** Menu choices share a width so they read as one list rather than three sizes. */
@Composable
private fun MenuButton(
    label: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (icon != null) ButtonIcon(icon)
        Text(label)
    }
}

/**
 * Centres a screen's content, keeps a margin, and scrolls if it still does not fit.
 *
 * The constraints are read *outside* the scroll on purpose: inside one, the available
 * height is infinite, so `maxWidth > maxHeight` would always be false, every screen would
 * lay itself out as if it were in portrait, and the scale below would be measured against
 * a screen that does not exist.
 *
 * ### Why the density is overridden here
 *
 * Written in fixed dp, every one of these screens is a phone screen wherever it runs: on
 * a 10" tablet the menu's logo came out at a third of the width it has on a phone, and
 * its buttons — the one thing that did follow the screen, being full-width halves —
 * stretched into 600 dp letterboxes with one small word in the middle.
 *
 * The fix is one line rather than a `* scale` on every dp in the app: a bigger density
 * means a bigger dp, so **everything inside grows together** — padding, text, icons,
 * touch targets, the lot — and each screen keeps writing the sizes it already had. The
 * proportions that were designed on a phone are the whole point; what changes is how much
 * glass they are drawn on.
 *
 * Two things this deliberately does not touch. The playfield: [com.vansid.panda.app.ui.game.GameScreen]
 * does not come through here, so the logical canvas keeps scaling by its own integer
 * against real pixels (§3). And anything read from `Configuration` — the pairing screen
 * takes the canvas width it negotiates from there — because a protocol value must not
 * move when the interface is made more comfortable.
 */
@Composable
internal fun ScreenScaffold(content: @Composable (wide: Boolean) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * screenScale(maxHeight), density.fontScale),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                content(wide)
            }
        }
    }
}

/**
 * How much bigger than a phone a screen this tall is.
 *
 * A phone held in landscape is around 400 dp tall and a 10" tablet is 800, so the ratio
 * against the phone is the factor, capped at 2: past a tablet the next screen is a
 * television, and an interface that kept growing would be reaching for a remote control
 * it cannot see.
 *
 * Height, not width, and not `sw600dp`. The problem on a big screen is vertical — a
 * handful of buttons sitting in the middle of it — and a resolution bucket would answer
 * with a step where what is wanted is a proportion.
 *
 * Snapped to quarters so that every phone lands on exactly 1, the layout that shipped,
 * rather than on 1.03; and so that two devices a few dp apart cannot render measurably
 * different screens.
 *
 * The height arrives before the override above, in the screen's own dp. It has to: a
 * scale measured through the density it is about to set would chase its own tail.
 */
private fun screenScale(height: Dp): Float {
    val raw = (height / 400.dp).coerceIn(1f, 2f)
    return round(raw * 4f) / 4f
}
