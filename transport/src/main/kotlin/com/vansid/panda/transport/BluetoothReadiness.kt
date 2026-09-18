package com.vansid.panda.transport

/** API 31 is where location stopped being Nearby's gate. */
private const val SDK_BLUETOOTH_SPLIT = 31

/**
 * Why Bluetooth play cannot start yet — or that it can (§12).
 *
 * Four separate states rather than one boolean, because each has a different remedy and
 * a player told only "Bluetooth unavailable" has no idea which of three settings screens
 * to go to. The whole point of the M6 criterion is that denying each thing produces a
 * specific message, never a silent `catch`.
 */
sealed interface BluetoothReadiness {
    /** These still have to be granted; the system dialog can still ask. */
    data class NeedsPermission(
        val permissions: List<String>,
    ) : BluetoothReadiness

    /** The permissions are there; the radio is switched off. */
    data object BluetoothOff : BluetoothReadiness

    /** Up to API 30, Nearby's BLE scan finds nothing with location services off. */
    data object LocationOff : BluetoothReadiness

    data object Ready : BluetoothReadiness
}

/**
 * Works out what is standing in the way.
 *
 * The order is not cosmetic. Permissions come first because `BluetoothAdapter.isEnabled`
 * itself needs `BLUETOOTH_CONNECT` from API 31, so asking the radio how it is doing
 * before the grant arrives throws `SecurityException` — the silent `catch` the criterion
 * forbids, arrived at by checking things in the wrong order.
 *
 * @param locationServicesOn whether the device's location toggle is on. Only consulted
 *   up to API 30: above it Nearby no longer uses location, and demanding it there would
 *   block a player over a setting that has nothing to do with the problem.
 */
fun bluetoothReadiness(
    sdkInt: Int,
    granted: Set<String>,
    bluetoothOn: Boolean,
    locationServicesOn: Boolean,
): BluetoothReadiness {
    val missing = NearbyPermissions.stillMissing(sdkInt, granted)
    return when {
        missing.isNotEmpty() -> BluetoothReadiness.NeedsPermission(missing)
        !bluetoothOn -> BluetoothReadiness.BluetoothOff
        sdkInt < SDK_BLUETOOTH_SPLIT && !locationServicesOn -> BluetoothReadiness.LocationOff
        else -> BluetoothReadiness.Ready
    }
}
