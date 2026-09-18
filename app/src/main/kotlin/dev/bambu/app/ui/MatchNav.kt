package dev.bambu.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.bambu.app.R
import dev.bambu.app.ui.game.GameScreen
import dev.bambu.core.AiLevel
import kotlin.random.Random

/**
 * How the finished match was played, carried into the result screen.
 *
 * "Play again" has to lead somewhere, and where that is depends on the mode: the setup
 * screen for the two local ones, and the Bluetooth gate for a remote match, whose link
 * the game screen hung up when the last round ended.
 */
enum class MatchMode {
    SOLO,
    LOCAL,
    BLUETOOTH,
}

/**
 * `Setup → Game → Result`, the three screens a match passes through.
 *
 * Their own function, like the Bluetooth branch: they share the arguments that describe
 * a match, and reading them is most of what these destinations do.
 */
internal fun NavGraphBuilder.matchDestinations(navController: NavHostController) {
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
        val mode = if (level != null) MatchMode.SOLO else MatchMode.LOCAL
        GameScreen(
            seed = seed,
            roundsToWin = rounds,
            aiLevel = level,
            onFinished = { winner ->
                navController.navigate(Routes.result(winner, mode)) { popUpTo(Routes.MENU) }
            },
        )
    }
    composable(Routes.RESULT) { entry ->
        val winner = entry.arguments?.getString(Routes.ARG_WINNER)?.toIntOrNull() ?: 0
        val mode =
            entry.arguments
                ?.getString(Routes.ARG_MODE)
                ?.let { runCatching { MatchMode.valueOf(it) }.getOrNull() }
                ?: MatchMode.LOCAL
        ResultScreen(
            winner = winner,
            // `Routes.SETUP` is the pattern, `{solo}` and all: navigating to it landed on
            // a route no destination answers to. Another match starts where this one was
            // set up, which is a different place per mode.
            onPlayAgain = {
                navController.navigate(Routes.playAgain(mode)) { popUpTo(Routes.MENU) }
            },
            onMenu = { navController.popBackStack(Routes.MENU, inclusive = false) },
        )
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
            // The seed is drawn afresh on every press: each match is a new one.
            Button(onClick = { onStart(Random.nextLong(), ROUNDS_TO_WIN, chosen) }) {
                Text(stringResource(R.string.setup_start))
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
