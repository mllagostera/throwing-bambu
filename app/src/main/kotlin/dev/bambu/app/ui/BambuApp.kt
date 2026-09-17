package dev.bambu.app.ui

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.bambu.app.R
import dev.bambu.app.render.rememberGameSprites
import dev.bambu.app.settings.AppLocale
import dev.bambu.app.settings.LocaleStore
import dev.bambu.app.settings.WithAppLocale
import dev.bambu.app.ui.bluetooth.bluetoothDestinations
import dev.bambu.app.ui.game.GameScreen
import dev.bambu.app.ui.icon.BambuIcons
import dev.bambu.app.ui.icon.ButtonIcon
import dev.bambu.app.ui.theme.BambuTheme
import dev.bambu.core.AiLevel

/** The level slot carries this when there is no AI: two people at the same device. */
const val NO_LEVEL = "NONE"

internal const val FIXED_SEED = 20260913L
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
                onBluetooth = { navController.navigate(Routes.BLUETOOTH) },
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
        composable(Routes.SETUP) { entry ->
            val solo = entry.arguments?.getString(Routes.ARG_SOLO).toBoolean()
            SetupScreen(
                solo = solo,
                onStart = { seed, rounds, level -> navController.navigate(Routes.game(seed, rounds, level)) },
            )
        }
        composable(Routes.GAME) { entry ->
            val seed = entry.arguments?.getString(Routes.ARG_SEED)?.toLongOrNull() ?: 0L
            val rounds = entry.arguments?.getString(Routes.ARG_ROUNDS)?.toIntOrNull() ?: ROUNDS_TO_WIN
            val level =
                entry.arguments
                    ?.getString(Routes.ARG_LEVEL)
                    ?.takeIf { it != NO_LEVEL }
                    ?.let { runCatching { AiLevel.valueOf(it) }.getOrNull() }
            GameScreen(
                seed = seed,
                roundsToWin = rounds,
                aiLevel = level,
                onFinished = { winner ->
                    navController.navigate(Routes.result(winner)) { popUpTo(Routes.MENU) }
                },
            )
        }
        bluetoothDestinations(navController)
        composable(Routes.RESULT) { entry ->
            val winner = entry.arguments?.getString(Routes.ARG_WINNER)?.toIntOrNull() ?: 0
            ResultScreen(
                winner = winner,
                onPlayAgain = { navController.navigate(Routes.SETUP) { popUpTo(Routes.MENU) } },
                onMenu = { navController.popBackStack(Routes.MENU, inclusive = false) },
            )
        }
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
    const val SETUP = "setup/{$ARG_SOLO}"
    const val GAME = "game/{$ARG_SEED}/{$ARG_ROUNDS}/{$ARG_LEVEL}"
    const val RESULT = "result/{$ARG_WINNER}"

    fun setup(solo: Boolean) = "setup/$solo"

    fun game(
        seed: Long,
        rounds: Int,
        level: AiLevel?,
    ) = "game/$seed/$rounds/${level?.name ?: NO_LEVEL}"

    fun result(winner: Int) = "result/$winner"
}

@Composable
private fun MenuScreen(
    onOnePlayer: () -> Unit,
    onTwoPlayers: () -> Unit,
    onBluetooth: () -> Unit,
    onSettings: () -> Unit,
) {
    // Landscape is the only orientation this game runs in (D-04), so the menu is laid
    // out for it: logo on one side, choices on the other. Stacked vertically, the last
    // button was pushed off the bottom of the screen.
    ScreenScaffold { wide ->
        val buttons: @Composable ColumnScope.() -> Unit = {
            MenuButton(stringResource(R.string.menu_one_player), onClick = onOnePlayer)
            MenuButton(stringResource(R.string.menu_two_players), onClick = onTwoPlayers)
            MenuButton(
                label = stringResource(R.string.menu_bluetooth),
                icon = BambuIcons.Bluetooth,
                onClick = onBluetooth,
            )
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = current == AppLocale.SYSTEM,
                    onClick = { onPick(AppLocale.SYSTEM) },
                    label = { Text(stringResource(R.string.settings_language_system)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in AppLocale.entries.filter { it != AppLocale.SYSTEM }) {
                    FilterChip(
                        selected = current == option,
                        onClick = { onPick(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            Button(onClick = onBack) {
                ButtonIcon(BambuIcons.Back)
                Text(stringResource(R.string.settings_back))
            }
        }
    }
}

@Composable
private fun SetupScreen(
    solo: Boolean,
    onStart: (Long, Int, AiLevel?) -> Unit,
) {
    var level by remember { mutableStateOf(AiLevel.MEDIUM) }

    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.setup_rounds))

            if (solo) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in AiLevel.entries) {
                        FilterChip(
                            selected = option == level,
                            onClick = { level = option },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }
            }

            val chosen = if (solo) level else null
            Button(onClick = { onStart(System.currentTimeMillis(), ROUNDS_TO_WIN, chosen) }) {
                Text(stringResource(R.string.setup_start))
            }
            // A known seed makes a bug reproducible: the same match, shot for shot.
            Button(onClick = { onStart(FIXED_SEED, ROUNDS_TO_WIN, chosen) }) {
                Text(stringResource(R.string.setup_start_fixed))
            }
        }
    }
}

@Composable
private fun ResultScreen(
    winner: Int,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.result_winner, winner + 1),
                style = MaterialTheme.typography.headlineMedium,
            )
            Button(onClick = onPlayAgain) { Text(stringResource(R.string.result_play_again)) }
            Button(onClick = onMenu) { Text(stringResource(R.string.result_menu)) }
        }
    }
}

/** The AI levels are shown translated; the enum names stay as the contract wrote them. */
private fun AiLevel.labelRes(): Int =
    when (this) {
        AiLevel.EASY -> R.string.level_easy
        AiLevel.MEDIUM -> R.string.level_medium
        AiLevel.HARD -> R.string.level_hard
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
 * height is infinite, so `maxWidth > maxHeight` would always be false and every screen
 * would lay itself out as if it were in portrait.
 */
@Composable
internal fun ScreenScaffold(content: @Composable (wide: Boolean) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
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
