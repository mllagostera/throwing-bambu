package com.vansid.panda.core

import kotlin.math.abs

/** Margin outside the canvas past which the cane is considered lost (§8). */
private const val OFFSCREEN_MARGIN = 20

/**
 * Simulates a complete shot (§8). Pure function: it does not draw, does not animate and
 * **does not mutate the terrain** — the caller applies the crater.
 *
 * Returns an owned `path`, trimmed to `steps * 2`.
 */
fun simulate(
    scenario: Scenario,
    shooter: Int,
    shot: Shot,
    wind: Int,
): ShotResult {
    val scratch = FloatArray(G.MAX_STEPS * 2)
    val r = simulateInto(scenario, shooter, shot, wind, scratch)
    return ShotResult(
        path = scratch.copyOf(r.steps * 2),
        steps = r.steps,
        outcome = r.outcome,
        impactX = r.impactX,
        impactY = r.impactY,
        sunHit = r.sunHit,
    )
}

/**
 * Same as [simulate] but writing into a borrowed buffer, allocating nothing.
 *
 * The result's `path` points at `scratch`: it is only valid until the next call. It
 * exists for the AI search, which discards every trajectory. It must **never** be
 * published in a `MatchEvent` (D-01).
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount")
fun simulateInto(
    scenario: Scenario,
    shooter: Int,
    shot: Shot,
    wind: Int,
    scratch: FloatArray,
): ShotResult {
    require(shot.angle in G.ANGLE_MIN..G.ANGLE_MAX) { "angle out of range: ${shot.angle}" }
    require(shot.power in G.POWER_MIN..G.POWER_MAX) { "power out of range: ${shot.power}" }
    require(scratch.size >= G.MAX_STEPS * 2) { "scratch too small: ${scratch.size}" }

    val dir = if (shooter == 0) 1 else -1
    val me = scenario.pandas[shooter]
    val opponent = 1 - shooter
    val terrain = scenario.terrain
    val hit = IntArray(2)

    var x = (me.x + dir * G.HAND_DX).toFloat()
    var y = (me.roofY - G.HAND_DY).toFloat()
    val speed = shot.power * G.POWER_TO_SPEED
    var vx = dir * COS[shot.angle] * speed
    var vy = -SIN[shot.angle] * speed // y grows downwards
    val windA = wind * G.WIND_ACCEL
    var sunHit = false

    for (step in 0 until G.MAX_STEPS) {
        val prevX = x
        val prevY = y

        x += vx * G.DT
        y += vy * G.DT
        vy += G.GRAVITY * G.DT
        vx += windA * G.DT

        scratch[step * 2] = x
        scratch[step * 2 + 1] = y
        val steps = step + 1

        if (x < -OFFSCREEN_MARGIN || x > scenario.width + OFFSCREEN_MARGIN || y > G.H) {
            return ShotResult(scratch, steps, Outcome.OffScreen, x.toInt(), y.toInt(), sunHit)
        }
        // The sky is open above: the cane may leave and come back.
        if (y < 0f) continue

        // The sun does not slow the cane down: it only changes expression. It is a
        // detail of the original.
        if (hitsSun(scenario, x, y)) sunHit = true

        if (hitsPanda(scenario.pandas[opponent], x, y)) {
            return ShotResult(scratch, steps, Outcome.HitPanda(opponent), x.toInt(), y.toInt(), sunHit)
        }
        // Own goals are legal, but the throwing point sits inside the thrower's own
        // box: without these grace steps every shot would end on step 1.
        if (step >= G.SELF_HIT_GRACE_STEPS && hitsPanda(me, x, y)) {
            return ShotResult(scratch, steps, Outcome.HitPanda(shooter), x.toInt(), y.toInt(), sunHit)
        }

        if (terrain.firstSolidOnSegment(prevX, prevY, x, y, hit)) {
            return ShotResult(scratch, steps, Outcome.HitTerrain(hit[0], hit[1]), hit[0], hit[1], sunHit)
        }
    }

    return ShotResult(scratch, G.MAX_STEPS, Outcome.TimeOut, x.toInt(), y.toInt(), sunHit)
}

/** Panda AABB centred on `(x, roofY - PANDA_H/2)`, expanded by the cane radius. */
private fun hitsPanda(
    p: Panda,
    x: Float,
    y: Float,
): Boolean {
    if (!p.alive) return false
    val halfW = G.PANDA_W / 2 + G.CANE_R
    val halfH = G.PANDA_H / 2 + G.CANE_R
    val cy = p.roofY - G.PANDA_H / 2
    return abs(x - p.x) <= halfW && abs(y - cy) <= halfH
}

private fun hitsSun(
    scenario: Scenario,
    x: Float,
    y: Float,
): Boolean {
    val halfW = G.SUN_W / 2 + G.CANE_R
    // A band, not a half-plane. While the sun sat at y = 0 an upper bound was enough,
    // because the loop skips everything above the canvas; away from the top edge the
    // sky is open on both sides of it.
    return abs(x - scenario.sunX) <= halfW &&
        y >= G.SUN_Y - G.CANE_R &&
        y <= G.SUN_Y + G.SUN_H + G.CANE_R
}
