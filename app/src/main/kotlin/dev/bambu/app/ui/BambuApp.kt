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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.bambu.app.render.rememberGameSprites
import dev.bambu.app.ui.game.GameScreen
import dev.bambu.core.AiLevel

/**
 * Navigation (§14): `Menu → [mode] → Setup → Game → Result`.
 *
 * M2 only wires the local two-player route. One player and Bluetooth arrive with M3 and
 * M6; their buttons stay visible but disabled, so the menu already shows the shape of
 * the game instead of growing later.
 */
@Composable
fun BambuApp(navController: NavHostController = rememberNavController()) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = Routes.MENU) {
                composable(Routes.MENU) {
                    MenuScreen(
                        onOnePlayer = { navController.navigate(Routes.setup(solo = true)) },
                        onTwoPlayers = { navController.navigate(Routes.setup(solo = false)) },
                    )
                }
                composable(Routes.SETUP) { entry ->
                    val solo = entry.arguments?.getString(Routes.ARG_SOLO).toBoolean()
                    SetupScreen(
                        solo = solo,
                        onStart = { seed, rounds, level ->
                            navController.navigate(Routes.game(seed, rounds, level))
                        },
                    )
                }
                composable(Routes.GAME) { entry ->
                    val seed = entry.arguments?.getString(Routes.ARG_SEED)?.toLongOrNull() ?: 0L
                    val rounds = entry.arguments?.getString(Routes.ARG_ROUNDS)?.toIntOrNull() ?: 3
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
                            navController.navigate(Routes.result(winner)) {
                                popUpTo(Routes.MENU)
                            }
                        },
                    )
                }
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
    }
}

/** The level slot carries this when there is no AI: two people at the same device. */
const val NO_LEVEL = "NONE"

object Routes {
    const val MENU = "menu"
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
) {
    // Landscape is the only orientation this game runs in (D-04), so the menu is laid
    // out for it: logo on one side, choices on the other. Stacking them vertically left
    // the last button — online play — pushed off the bottom of the screen.
    ScreenScaffold { wide ->
        val buttons: @Composable ColumnScope.() -> Unit = {
            MenuButton("One player", onClick = onOnePlayer)
            MenuButton("Two players, same device", onClick = onTwoPlayers)
            MenuButton("Play over Bluetooth", enabled = false, onClick = {})
            Text(
                text = "Bluetooth pairing arrives in the next milestone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = buttons,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PixelLogo(maxWidth = 280.dp, maxHeight = 120.dp)
                buttons()
            }
        }
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
        contentDescription = "Throwing Bambu",
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
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

/**
 * Centres a screen's content, keeps a margin, and scrolls if it still does not fit.
 *
 * The scroll is the safety net: a 360 dp-tall landscape phone is not much room, and a
 * button that cannot be reached is worse than one that has to be scrolled to.
 */
@Composable
private fun ScreenScaffold(content: @Composable (wide: Boolean) -> Unit) {
    // The constraints are read *outside* the scroll on purpose: inside one, the
    // available height is infinite, so `maxWidth > maxHeight` would always be false and
    // every screen would lay itself out as if it were in portrait.
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
            Text("Match setup", style = MaterialTheme.typography.headlineSmall)
            Text("Best of 5 — first to 3 rounds")

            if (solo) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in AiLevel.entries) {
                        FilterChip(
                            selected = option == level,
                            onClick = { level = option },
                            label = { Text(option.name) },
                        )
                    }
                }
            }

            val chosen = if (solo) level else null
            Button(onClick = { onStart(System.currentTimeMillis(), 3, chosen) }) { Text("Start") }
            // A known seed makes a bug reproducible: the same match, shot for shot.
            Button(onClick = { onStart(FIXED_SEED, 3, chosen) }) { Text("Start with a fixed seed") }
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
            Text("Player ${winner + 1} wins", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onPlayAgain) { Text("Play again") }
            Button(onClick = onMenu) { Text("Menu") }
        }
    }
}

private const val FIXED_SEED = 20260913L
