package dev.bambu.transport

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permission gate for Bluetooth play (§12, D-12).
 *
 * A plain JVM test: [NearbyPermissions] takes the SDK level as an argument precisely so
 * these four branches do not need four devices. `Manifest.permission.*` are compile-time
 * String constants, so they inline and never reach the stubbed `android.jar`.
 */
class NearbyPermissionsTest {
    @Test
    fun modernAndroidAsksForTheBluetoothTrio() {
        val asked = NearbyPermissions.toRequest(sdkInt = 31)

        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            ),
            asked,
        )
        assertFalse("location was asked for on API 31", asked.any { it.contains("LOCATION") })
    }

    @Test
    fun androidThirteenAlsoAsksForNearbyWifi() {
        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in NearbyPermissions.toRequest(sdkInt = 33))
        assertFalse(Manifest.permission.NEARBY_WIFI_DEVICES in NearbyPermissions.toRequest(sdkInt = 32))
    }

    @Test
    fun oldAndroidAsksForLocationInstead() {
        val asked = NearbyPermissions.toRequest(sdkInt = 30)

        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            asked,
        )
    }

    /**
     * D-12, the whole point: COARSE alone is a grant, not a refusal.
     *
     * Getting this wrong does not crash or fail a build. It quietly turns a player who
     * pressed Allow into a player staring at a settings screen, which is exactly the
     * kind of bug that only shows up on somebody else's phone.
     */
    @Test
    fun coarseLocationAloneIsEnough() {
        val coarseOnly = setOf(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertTrue(NearbyPermissions.isSatisfied(sdkInt = 30, granted = coarseOnly))
        assertEquals(emptyList<String>(), NearbyPermissions.stillMissing(sdkInt = 30, granted = coarseOnly))
    }

    @Test
    fun fineLocationAloneIsAlsoEnough() {
        val fineOnly = setOf(Manifest.permission.ACCESS_FINE_LOCATION)

        assertTrue(NearbyPermissions.isSatisfied(sdkInt = 30, granted = fineOnly))
        assertEquals(emptyList<String>(), NearbyPermissions.stillMissing(sdkInt = 30, granted = fineOnly))
    }

    @Test
    fun noLocationAtAllIsARefusal() {
        assertFalse(NearbyPermissions.isSatisfied(sdkInt = 30, granted = emptySet()))
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            NearbyPermissions.stillMissing(sdkInt = 30, granted = emptySet()),
        )
    }

    /** Unlike location, the Bluetooth three are each their own requirement. */
    @Test
    fun onePartialBluetoothGrantIsNotEnough() {
        val partial =
            setOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )

        assertFalse(NearbyPermissions.isSatisfied(sdkInt = 31, granted = partial))
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN),
            NearbyPermissions.stillMissing(sdkInt = 31, granted = partial),
        )
    }

    @Test
    fun theFullGrantSatisfiesEveryLevel() {
        for (sdkInt in listOf(24, 30, 31, 33, 35)) {
            val everything = NearbyPermissions.toRequest(sdkInt).toSet()
            assertTrue(
                "API $sdkInt was not satisfied by its own request",
                NearbyPermissions.isSatisfied(sdkInt, everything),
            )
        }
    }
}
