package dev.bambu.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                    MenuScreen(onTwoPlayers = { navController.navigate(Routes.SETUP) })
                }
                composable(Routes.SETUP) {
                    SetupScreen(
                        onStart = { seed, rounds ->
                            navController.navigate(Routes.game(seed, rounds))
                        },
                    )
                }
                composable(Routes.GAME) { entry ->
                    val seed = entry.arguments?.getString(Routes.ARG_SEED)?.toLongOrNull() ?: 0L
                    val rounds = entry.arguments?.getString(Routes.ARG_ROUNDS)?.toIntOrNull() ?: 3
                    GameScreen(
                        seed = seed,
                        roundsToWin = rounds,
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
private fun MenuScreen(onTwoPlayers: () -> Unit) {
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
        Button(onClick = {}, enabled = false) { Text("One player (M3)") }
        Button(onClick = onTwoPlayers) { Text("Two players, same device") }
        Button(onClick = {}, enabled = false) { Text("Bluetooth (M6)") }
    }
}

@Composable
private fun SetupScreen(onStart: (Long, Int) -> Unit) {
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
        Button(onClick = { onStart(System.currentTimeMillis(), 3) }) { Text("Start") }
        Button(onClick = { onStart(FIXED_SEED, 3) }) { Text("Start with a fixed seed") }
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

/** A known seed makes a bug reproducible: the same match, shot for shot. */
private const val FIXED_SEED = 20260913L
