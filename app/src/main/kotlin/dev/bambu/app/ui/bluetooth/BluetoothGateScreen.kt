package dev.bambu.app.ui.bluetooth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.bambu.app.R
import dev.bambu.app.ui.ScreenScaffold
import dev.bambu.transport.BluetoothReadiness
import dev.bambu.transport.currentBluetoothReadiness

/**
 * The gate in front of Bluetooth play (§12).
 *
 * An explanation before the system dialog, never at startup: the player is told what the
 * permission is for and presses a button of ours before Android's appears. Asking cold
 * is how an app earns a reflexive Deny, and on Android 11+ a reflexive Deny is close to
 * permanent.
 *
 * Every refusal gets its own message and its own way out. "It didn't work", with no
 * remedy and no name for what went wrong, is the failure §12 exists to rule out.
 */
@Composable
fun BluetoothGateScreen(
    onReady: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var readiness by remember { mutableStateOf(currentBluetoothReadiness(context)) }
    var asked by remember { mutableStateOf(false) }

    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // The result map is deliberately ignored in favour of re-reading the device.
            // It is the only answer that treats "COARSE granted, FINE refused" as the
            // grant it is (D-12); the map would report FINE denied and send a player who
            // pressed Allow to a settings screen.
            readiness = currentBluetoothReadiness(context)
        }

    // Bluetooth and location are switched on outside this app, so the only way to notice
    // is to look again on the way back in.
    RecheckOnResume { readiness = currentBluetoothReadiness(context) }

    LaunchedEffect(readiness) {
        if (readiness == BluetoothReadiness.Ready) onReady()
    }

    GateLayout(
        body = bodyFor(readiness, asked),
        action = actionFor(readiness),
        onAction = {
            when (val state = readiness) {
                is BluetoothReadiness.NeedsPermission ->
                    // The first press opens Android's dialog; once it has been refused,
                    // that dialog will not appear again, so the only honest next step is
                    // the app's settings page.
                    if (asked) {
                        context.openAppSettings()
                    } else {
                        asked = true
                        request.launch(state.permissions.toTypedArray())
                    }

                BluetoothReadiness.BluetoothOff -> context.open(Settings.ACTION_BLUETOOTH_SETTINGS)
                BluetoothReadiness.LocationOff -> context.open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                BluetoothReadiness.Ready -> onReady()
            }
        },
        onBack = onBack,
    )
}

@Composable
private fun bodyFor(
    readiness: BluetoothReadiness,
    asked: Boolean,
): String =
    stringResource(
        when (readiness) {
            // Before asking, explain. After a refusal, say what it cost and where to undo it.
            is BluetoothReadiness.NeedsPermission ->
                if (asked) R.string.bt_denied_body else R.string.bt_explain_body

            BluetoothReadiness.BluetoothOff -> R.string.bt_off_body
            BluetoothReadiness.LocationOff -> R.string.bt_location_off_body
            BluetoothReadiness.Ready -> R.string.bt_ready_body
        },
    )

@Composable
private fun actionFor(readiness: BluetoothReadiness): String =
    stringResource(
        when (readiness) {
            is BluetoothReadiness.NeedsPermission -> R.string.bt_action_continue
            BluetoothReadiness.BluetoothOff -> R.string.bt_action_bluetooth_settings
            BluetoothReadiness.LocationOff -> R.string.bt_action_location_settings
            BluetoothReadiness.Ready -> R.string.bt_action_continue
        },
    )

@Composable
private fun GateLayout(
    body: String,
    action: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.bt_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bt_action_back))
            }
        }
    }
}

/** Re-reads the device every time this screen comes back to the foreground. */
@Composable
private fun RecheckOnResume(onResume: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) onResume()
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

private fun Context.open(action: String) {
    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
