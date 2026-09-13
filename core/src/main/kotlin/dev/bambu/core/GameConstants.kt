package dev.bambu.core

/**
 * Contract constants (§2 of the specification).
 *
 * Changing anything here changes the outcome of matches already played and breaks
 * compatibility between devices: change `ESPEC_DESARROLLO.md` first.
 */
object G {
    // Logical canvas
    const val H = 200 // fixed logical height
    const val W_MIN = 320
    const val W_MAX = 460
    const val SKY_BAND = 60 // top band kept clear of buildings

    // Integration
    const val DT = 1f / 120f
    const val MAX_FLIGHT_S = 15f
    const val MAX_STEPS = 1800 // MAX_FLIGHT_S / DT

    // Physics
    const val GRAVITY = 80f // px/s²
    const val WIND_ACCEL = 1.5f // px/s² per unit of wind (wind ∈ -10..10)
    const val POWER_TO_SPEED = 2.2f // power 1..100 -> 2.2..220 px/s

    // Bodies
    const val CANE_R = 3
    const val CRATER_R = 12
    const val PANDA_W = 16
    const val PANDA_H = 20
    const val HAND_DX = 10 // horizontal offset of the throwing point
    const val HAND_DY = 22 // height of the throwing point above the roof

    // Scenario
    const val BUILD_W_MIN = 24
    const val BUILD_W_MAX = 40
    const val BUILD_H_MIN = 40
    const val BUILD_H_MAX = 130
    const val SUN_W = 20
    const val SUN_H = 20

    /** The game is always two-player; the protocol and the match loop assume it. */
    const val PLAYERS = 2

    // Shot ranges
    const val ANGLE_MIN = 0
    const val ANGLE_MAX = 90
    const val POWER_MIN = 1
    const val POWER_MAX = 100

    /**
     * Initial steps during which the thrower's own hitbox is ignored.
     *
     * The throwing point (HAND_DX = 10) falls inside the thrower's own box once it is
     * expanded by the cane radius (PANDA_W/2 + CANE_R = 11), so without this grace
     * period every shot would be an immediate own goal.
     */
    const val SELF_HIT_GRACE_STEPS = 5
}
