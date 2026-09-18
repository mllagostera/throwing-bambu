package com.vansid.panda.core

/**
 * Synthetic scenarios for the physics tests.
 *
 * The opponent is created with `alive = false` on purpose: ballistic tests measure the
 * trajectory, and a hitbox sitting in the middle of the flight would cut it short.
 */
object TestScenarios {
    const val FLAT_WIDTH = 460
    const val ROOF_Y = 150
    const val SHOOTER_X = 60

    /** Height of player 0's throwing point. */
    const val LAUNCH_Y = ROOF_Y - G.HAND_DY

    /** A canvas without a single solid pixel: pure ballistics. */
    fun empty(
        opponentX: Int = 440,
        opponentAlive: Boolean = false,
        shooterRoofY: Int = ROOF_Y,
    ): Scenario = withMask(BooleanArray(FLAT_WIDTH * G.H), opponentX, opponentAlive, shooterRoofY)

    /** Empty canvas except for a one-pixel-thick horizontal slab. */
    fun withSlab(
        slabY: Int,
        shooterRoofY: Int = ROOF_Y,
    ): Scenario {
        val mask = BooleanArray(FLAT_WIDTH * G.H)
        for (x in 0 until FLAT_WIDTH) mask[slabY * FLAT_WIDTH + x] = true
        return withMask(mask, opponentX = 440, opponentAlive = false, shooterRoofY = shooterRoofY)
    }

    private fun withMask(
        mask: BooleanArray,
        opponentX: Int,
        opponentAlive: Boolean,
        shooterRoofY: Int,
    ): Scenario {
        val terrain = Terrain(FLAT_WIDTH, mask, ByteArray(FLAT_WIDTH * G.H), emptyList())
        return Scenario(
            width = FLAT_WIDTH,
            terrain = terrain,
            pandas =
                listOf(
                    Panda(0, SHOOTER_X, shooterRoofY),
                    Panda(1, opponentX, ROOF_Y, alive = opponentAlive),
                ),
            sunX = FLAT_WIDTH / 2,
        )
    }

    /**
     * Horizontal range until the cane falls back to the throwing height, read off the
     * trajectory. Returns -1 if it never comes back down that far.
     */
    fun rangeAtLaunchHeight(
        result: ShotResult,
        launchX: Float,
        launchY: Float,
    ): Float {
        var aboveApex = false
        for (i in 0 until result.steps) {
            val y = result.path[i * 2 + 1]
            if (y < launchY) aboveApex = true
            if (aboveApex && y >= launchY) {
                return kotlin.math.abs(result.path[i * 2] - launchX)
            }
        }
        return -1f
    }
}
