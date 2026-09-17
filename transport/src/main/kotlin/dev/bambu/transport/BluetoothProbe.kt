package dev.bambu.transport

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

/** API 31 is where `BluetoothAdapter.isEnabled` started needing a permission of its own. */
private const val SDK_BLUETOOTH_SPLIT = 31

/** Which of the permissions this device needs are actually held right now. */
fun grantedNearbyPermissions(context: Context): Set<String> =
    NearbyPermissions
        .toRequest(Build.VERSION.SDK_INT)
        .filterTo(mutableSetOf()) {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

/**
 * Asks the device what is standing in the way of Bluetooth play (§12).
 *
 * The thin half of the gate: it gathers three facts and hands them to
 * [bluetoothReadiness], which holds the actual rules and is tested on a plain JVM.
 * Splitting it this way is why "COARSE only counts" and "the location toggle stops
 * mattering at API 31" are covered by tests rather than by four phones.
 */
fun currentBluetoothReadiness(context: Context): BluetoothReadiness =
    bluetoothReadiness(
        sdkInt = Build.VERSION.SDK_INT,
        granted = grantedNearbyPermissions(context),
        bluetoothOn = isBluetoothOn(context),
        locationServicesOn = isLocationOn(context),
    )

/**
 * Whether the radio is switched on.
 *
 * The permission guard is not decoration. From API 31, reading `isEnabled` without
 * `BLUETOOTH_CONNECT` throws `SecurityException`, and catching that would turn a
 * missing grant into "Bluetooth is off" — sending the player to the wrong settings
 * screen, which is precisely the silent failure §12 rules out.
 */
private fun isBluetoothOn(context: Context): Boolean {
    val needsGrant = Build.VERSION.SDK_INT >= SDK_BLUETOOTH_SPLIT
    if (needsGrant &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}

/**
 * Whether location services are on, asked the way every API level understands.
 *
 * `isLocationEnabled` would be the modern call, but it arrived in API 28 and this only
 * matters up to API 30 — so the older provider query covers the whole range that cares,
 * with no version branch to get wrong.
 */
private fun isLocationOn(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
