package dev.bambu.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.ContentScale
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

object Routes {
    const val MENU = "menu"
    const val SETUP = "setup"
    const val ARG_SEED = "seed"
    const val ARG_ROUNDS = "rounds"
    const val ARG_WINNER = "winner"
    const val GAME = "game/{$ARG_SEED}/{$ARG_ROUNDS}"
    const val RESULT = "result/{$ARG_WINNER}"

    fun game(
        seed: Long,
        rounds: Int,
    ) = "game/$seed/$rounds"

    fun result(winner: Int) = "result/$winner"
}

@Composable
private fun MenuScreen(
    onOnePlayer: () -> Unit,
    onTwoPlayers: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The delivered logo, scaled by a whole number with no filtering, like everything
        // else in this game.
        Image(
            painter =
                androidx.compose.ui.graphics.painter.BitmapPainter(
                    image = rememberGameSprites().logo,
                    filterQuality = FilterQuality.None,
                ),
            contentDescription = "Throwing Bambu",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Button(onClick = onOnePlayer) { Text("One player") }
        Button(onClick = onTwoPlayers) { Text("Two players, same device") }
        Button(onClick = {}, enabled = false) { Text("Bluetooth (M6)") }
    }
}

@Composable
private fun SetupScreen(
    solo: Boolean,
    onStart: (Long, Int, AiLevel?) -> Unit,
) {
    var level by remember { mutableStateOf(AiLevel.MEDIUM) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
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

@Composable
private fun ResultScreen(
    winner: Int,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Player ${winner + 1} wins", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onPlayAgain) { Text("Play again") }
        Button(onClick = onMenu) { Text("Menu") }
    }
}

private const val FIXED_SEED = 20260913L
