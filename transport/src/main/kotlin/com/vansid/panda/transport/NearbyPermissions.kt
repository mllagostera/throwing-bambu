package com.vansid.panda.transport

import android.Manifest

/** API 31 split the Bluetooth permissions out of the location ones. */
private const val SDK_BLUETOOTH_SPLIT = 31

/** API 33 gave Nearby's Wi-Fi use a permission of its own. */
private const val SDK_NEARBY_WIFI = 33

/**
 * One thing the app needs permission to do, and every permission that would grant it.
 *
 * A group rather than a plain name because of location: `FINE` and `COARSE` are two
 * names for one requirement, and the user may hand over either. Treating them as two
 * separate requirements is the bug D-12 exists to prevent — it turns a perfectly usable
 * grant into a refusal and sends the player to a settings screen for nothing.
 */
private class Requirement(
    vararg val anyOf: String,
)

/**
 * What the app must hold before Nearby can advertise or discover (§12, D-12).
 *
 * Pure: it takes the SDK level as an argument rather than reading `Build.VERSION`, so
 * every branch below can be exercised on a plain JVM instead of on four devices. That
 * matters here more than usual — this is the code path that decides whether a player
 * gets into a match at all, and it cannot be checked by looking at it.
 */
object NearbyPermissions {
    private fun requirements(sdkInt: Int): List<Requirement> =
        buildList {
            if (sdkInt >= SDK_BLUETOOTH_SPLIT) {
                add(Requirement(Manifest.permission.BLUETOOTH_ADVERTISE))
                add(Requirement(Manifest.permission.BLUETOOTH_CONNECT))
                add(Requirement(Manifest.permission.BLUETOOTH_SCAN))
            } else {
                // Up to API 30 Nearby's BLE scan is gated on location instead, and the
                // old BLUETOOTH/BLUETOOTH_ADMIN pair is granted at install time.
                add(
                    Requirement(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
            if (sdkInt >= SDK_NEARBY_WIFI) {
                add(Requirement(Manifest.permission.NEARBY_WIFI_DEVICES))
            }
        }

    /**
     * Everything to put in front of the system dialog, in one request.
     *
     * One dialog, not three: Android batches them, and asking three times in a row for
     * what is plainly one capability is how an app trains its users to press Deny.
     */
    fun toRequest(sdkInt: Int): List<String> = requirements(sdkInt).flatMap { it.anyOf.toList() }

    /** True when every requirement has at least one of its permissions granted. */
    fun isSatisfied(
        sdkInt: Int,
        granted: Set<String>,
    ): Boolean = requirements(sdkInt).all { req -> req.anyOf.any { it in granted } }

    /**
     * What is still missing, for the screen that has to explain itself.
     *
     * A requirement satisfied by *either* of its names contributes nothing here, so
     * "COARSE only" comes back as an empty list rather than as "FINE is missing".
     */
    fun stillMissing(
        sdkInt: Int,
        granted: Set<String>,
    ): List<String> =
        requirements(sdkInt)
            .filterNot { req -> req.anyOf.any { it in granted } }
            .flatMap { it.anyOf.toList() }
}
