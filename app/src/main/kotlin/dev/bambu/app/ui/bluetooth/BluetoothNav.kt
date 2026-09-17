package dev.bambu.app.ui.bluetooth

import android.os.Build
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.bambu.app.ui.FIXED_SEED
import dev.bambu.app.ui.ROUNDS_TO_WIN
import dev.bambu.app.ui.Routes
import dev.bambu.app.ui.game.GameScreen

/**
 * `Menu → gate → pairing → game`, the Bluetooth branch (§12, §14).
 *
 * Its own function rather than three more entries in the main graph: it is one journey
 * with one exit, and every step of it can fail in a way the others cannot.
 */
internal fun NavGraphBuilder.bluetoothDestinations(navController: NavHostController) {
    composable(Routes.BLUETOOTH) {
        BluetoothGateScreen(
            onReady = { navController.navigate(Routes.PAIRING) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.PAIRING) {
        PairingScreen(
            // The device model, so two people in a room can tell which panda is theirs.
            nick = Build.MODEL,
            // Back to the menu rather than onto the stack: once a match is agreed there
            // is nothing to return to, and stepping back into a pairing screen whose
            // link now belongs to the game would start a second search on one radio.
            onReady = { navController.navigate(Routes.REMOTE_GAME) { popUpTo(Routes.MENU) } },
            onBack = { navController.popBackStack(Routes.MENU, inclusive = false) },
        )
    }
    composable(Routes.REMOTE_GAME) {
        // The seed and the width are the host's, and arrive through the handshake rather
        // than through the route; these two are the placeholders GameScreen ignores.
        GameScreen(
            seed = FIXED_SEED,
            roundsToWin = ROUNDS_TO_WIN,
            aiLevel = null,
            remote = true,
            onFinished = { winner ->
                navController.navigate(Routes.result(winner)) { popUpTo(Routes.MENU) }
            },
        )
    }
}
