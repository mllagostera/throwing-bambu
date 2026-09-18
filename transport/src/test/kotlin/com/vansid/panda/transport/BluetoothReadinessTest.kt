package com.vansid.panda.transport

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each obstacle to Bluetooth play names itself, so each can be given its own remedy (§12). */
class BluetoothReadinessTest {
    @Test
    fun everythingInPlaceIsReady() {
        assertEquals(BluetoothReadiness.Ready, readiness(sdkInt = 33, granted = grantFor(33)))
    }

    @Test
    fun aMissingGrantIsReportedBeforeAnythingElse() {
        // Deliberately also off and location-less: the permission still has to come
        // first, because asking the adapter anything without it throws from API 31.
        val state =
            bluetoothReadiness(
                sdkInt = 33,
                granted = emptySet(),
                bluetoothOn = false,
                locationServicesOn = false,
            )

        assertTrue(state is BluetoothReadiness.NeedsPermission)
        assertEquals(NearbyPermissions.toRequest(33), (state as BluetoothReadiness.NeedsPermission).permissions)
    }

    @Test
    fun aGrantedButSwitchedOffRadioSaysSo() {
        assertEquals(
            BluetoothReadiness.BluetoothOff,
            readiness(sdkInt = 33, granted = grantFor(33), bluetoothOn = false),
        )
    }

    /** Up to API 30 the BLE scan finds nothing without location services. */
    @Test
    fun oldAndroidNeedsLocationServicesOn() {
        assertEquals(
            BluetoothReadiness.LocationOff,
            readiness(sdkInt = 30, granted = grantFor(30), locationServicesOn = false),
        )
    }

    /**
     * Above API 30 the location toggle is none of Nearby's business.
     *
     * Blocking there would stop a player over a setting unrelated to the problem — and
     * it is an easy mistake to make, since the permission list below 31 mentions
     * location at all.
     */
    @Test
    fun modernAndroidDoesNotCareAboutTheLocationToggle() {
        assertEquals(
            BluetoothReadiness.Ready,
            readiness(sdkInt = 31, granted = grantFor(31), locationServicesOn = false),
        )
    }

    /** D-12 again, this time end to end: COARSE alone gets the player through the gate. */
    @Test
    fun coarseLocationAloneGetsThrough() {
        assertEquals(
            BluetoothReadiness.Ready,
            readiness(sdkInt = 30, granted = setOf(Manifest.permission.ACCESS_COARSE_LOCATION)),
        )
    }

    private fun grantFor(sdkInt: Int): Set<String> = NearbyPermissions.toRequest(sdkInt).toSet()

    private fun readiness(
        sdkInt: Int,
        granted: Set<String>,
        bluetoothOn: Boolean = true,
        locationServicesOn: Boolean = true,
    ) = bluetoothReadiness(sdkInt, granted, bluetoothOn, locationServicesOn)
}
