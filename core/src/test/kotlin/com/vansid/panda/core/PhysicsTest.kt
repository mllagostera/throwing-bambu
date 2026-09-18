package com.vansid.panda.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class PhysicsTest {
    private val launchX = (TestScenarios.SHOOTER_X + G.HAND_DX).toFloat()
    private val launchY = TestScenarios.LAUNCH_Y.toFloat()

    /**
     * §15.4 and the calibration in §2: with no wind and no obstacles, the range at 45°
     * matches `v²/g` within 2 %. At power 68 that is 279.8 px, not the ~300 px the first
     * version of the specification claimed (D-05).
     */
    @Test
    fun rangeAt45DegreesMatchesBallistics() {
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, shooter = 0, shot = Shot(0, 45, 68), wind = 0)

        val speed = 68 * G.POWER_TO_SPEED
        val expected = speed * speed / G.GRAVITY
        val measured = TestScenarios.rangeAtLaunchHeight(result, launchX, launchY)

        assertTrue("the cane never returns to the throwing height", measured > 0)
        assertEquals("range outside tolerance", expected.toDouble(), measured.toDouble(), expected * 0.02)
        assertEquals("the §2 calibration has changed", 279.8, expected.toDouble(), 0.5)
    }

    @Test
    fun flightTimeAt45DegreesMatchesCalibration() {
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, shooter = 0, shot = Shot(0, 45, 68), wind = 0)

        var steps = 0
        var aboveApex = false
        for (i in 0 until result.steps) {
            val y = result.path[i * 2 + 1]
            if (y < launchY) aboveApex = true
            if (aboveApex && y >= launchY) {
                steps = i + 1
                break
            }
        }
        val seconds = steps * G.DT
        assertEquals("flight time outside the calibration", 2.645, seconds.toDouble(), 0.05)
    }

    /** §15.5: wind shifts the impact symmetrically. */
    @Test
    fun windShiftsRangeSymmetrically() {
        val scenario = TestScenarios.empty()

        fun range(wind: Int): Float =
            TestScenarios.rangeAtLaunchHeight(
                simulate(scenario, 0, Shot(0, 45, 68), wind),
                launchX,
                launchY,
            )

        val calm = range(0)
        val tail = range(10)
        val head = range(-10)

        assertTrue("a tailwind does not lengthen the shot", tail > calm)
        assertTrue("a headwind does not shorten the shot", head < calm)
        assertEquals("wind is not symmetric", (tail - calm).toDouble(), (calm - head).toDouble(), 1.0)
    }

    /** §15.7: hitting the opposing panda. */
    @Test
    fun hittingTheOpponentIsReported() {
        // First measure where the shot lands, then put the opponent there.
        val probe = simulate(TestScenarios.empty(), 0, Shot(0, 45, 68), wind = 0)
        val impact = TestScenarios.rangeAtLaunchHeight(probe, launchX, launchY)
        val target = (launchX + impact).toInt()

        val scenario = TestScenarios.empty(opponentX = target, opponentAlive = true)
        val result = simulate(scenario, 0, Shot(0, 45, 68), wind = 0)

        assertEquals(Outcome.HitPanda(1), result.outcome)
        assertTrue("impact too far from the target", abs(result.impactX - target) <= G.PANDA_W)
    }

    /** §15.8: at 90° with low power the cane comes back down onto the thrower. */
    @Test
    fun straightUpShotComesBackAndHitsTheShooter() {
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, 0, Shot(0, 90, 20), wind = 0)

        assertEquals(Outcome.HitPanda(0), result.outcome)
        // Landing within the grace steps would be a false own goal, not a return.
        assertTrue("the cane never flew: ${result.steps} steps", result.steps > 20)
    }

    @Test
    fun theShotLeavesItsOwnHitboxBeforeGraceEnds() {
        // The 5-step grace only works if by then the cane is already out of the AABB.
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, 0, Shot(0, 90, 20), wind = 0)
        val yAtGrace = result.path[(G.SELF_HIT_GRACE_STEPS - 1) * 2 + 1]
        val topOfHitbox = TestScenarios.ROOF_Y - G.PANDA_H / 2 - (G.PANDA_H / 2 + G.CANE_R)
        assertTrue(
            "when grace ends the cane is still inside its own AABB ($yAtGrace >= $topOfHitbox)",
            yAtGrace < topOfHitbox,
        )
    }

    /** §15.9: `simulate` never exceeds MAX_STEPS nor writes outside `path`. */
    @Test
    fun neverExceedsMaxStepsOrPathBounds() {
        val scenario = TestScenarios.empty()
        for (angle in 0..90 step 5) {
            for (power in intArrayOf(1, 25, 50, 75, 100)) {
                for (wind in intArrayOf(-10, 0, 10)) {
                    val r = simulate(scenario, 0, Shot(0, angle, power), wind)
                    assertTrue("steps out of range: ${r.steps}", r.steps in 1..G.MAX_STEPS)
                    assertEquals("path wrongly sized", r.steps * 2, r.path.size)
                    for (v in r.path) assertTrue("coordinate is not finite", v.isFinite())
                }
            }
        }
    }

    /** D-01: the published `path` is owned; the one from `simulateInto` is borrowed. */
    @Test
    fun simulateCopiesThePathButSimulateIntoDoesNot() {
        val scenario = TestScenarios.empty()
        val owned = simulate(scenario, 0, Shot(0, 45, 68), 0)

        val scratch = FloatArray(G.MAX_STEPS * 2)
        simulateInto(scenario, 0, Shot(0, 30, 50), 0, scratch)
        val borrowed = simulateInto(scenario, 0, Shot(0, 60, 90), 0, scratch)

        assertTrue("simulateInto should return the borrowed buffer", borrowed.path === scratch)
        // A second simulation must not alter an already published result.
        val first = owned.path.copyOf()
        simulateInto(scenario, 0, Shot(0, 10, 100), 0, scratch)
        assertTrue("the published path was overwritten", first.contentEquals(owned.path))
    }

    /**
     * A3 (D-03): without segment sampling the cane flies through a thin slab on the way
     * down. The test also checks that the impacting step jumps more than one pixel: if
     * it did not, it would be proving nothing.
     */
    @Test
    fun fastFallDoesNotTunnelThroughAThinSlab() {
        val slabY = 195
        val scenario = TestScenarios.withSlab(slabY, shooterRoofY = 40)
        val result = simulate(scenario, 0, Shot(0, 80, 100), wind = 0)

        assertEquals(Outcome.HitTerrain(result.impactX, slabY), result.outcome)
        assertEquals(slabY, result.impactY)

        val last = result.steps - 1
        val jump = abs(result.path[last * 2 + 1] - result.path[(last - 1) * 2 + 1])
        assertTrue("the final step does not jump past 1 px ($jump): the test proves nothing", jump > 1.5f)
    }

    /**
     * Ceiling on the advance per step, measured rather than estimated: a full sweep of
     * angles, powers, winds and roof heights peaks at 2.50 px (2.05 horizontal, 2.32
     * vertical). Above 1 px, point sampling can already skip a terrain isthmus, which is
     * what justifies the DDA in [Terrain.firstSolidOnSegment] (D-03).
     *
     * If this ceiling rises, the anti-tunnelling margin narrows and D-03 needs revisiting.
     */
    @Test
    fun stepAdvanceStaysBelowTheMeasuredCeiling() {
        val scenario = TestScenarios.withSlab(199, shooterRoofY = 40)
        var maxStep = 0f
        for (angle in 0..90 step 5) {
            for (power in 10..100 step 10) {
                for (wind in intArrayOf(-10, 0, 10)) {
                    val r = simulate(scenario, 0, Shot(0, angle, power), wind)
                    for (i in 1 until r.steps) {
                        val dx = r.path[i * 2] - r.path[(i - 1) * 2]
                        val dy = r.path[i * 2 + 1] - r.path[(i - 1) * 2 + 1]
                        val d = sqrt(dx * dx + dy * dy)
                        if (d > maxStep) maxStep = d
                    }
                }
            }
        }
        assertTrue("advance per step rose to $maxStep px (measured: 2.50)", maxStep < 2.6f)
        assertTrue("advance per step exceeds 1 px: the DDA is needed", maxStep > 1f)
    }

    @Test
    fun theSunDoesNotStopTheCane() {
        // §8: the sun changes expression, it does not stop the cane. Hence no HitSun.
        val scenario = TestScenarios.empty()
        var sawSun = false
        for (angle in 40..85) {
            val r = simulate(scenario, 0, Shot(0, angle, 100), wind = 0)
            if (r.sunHit) {
                sawSun = true
                assertNotEquals("the sun ended the flight", 0, r.steps)
                assertTrue(
                    "the flight ended at the sun",
                    r.outcome is Outcome.OffScreen || r.outcome is Outcome.HitTerrain,
                )
            }
        }
        assertTrue("no shot reached the sun: the test checks nothing", sawSun)
    }

    @Test
    fun offScreenShotsAreReported() {
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, 0, Shot(0, 5, 100), wind = 10)
        assertEquals(Outcome.OffScreen, result.outcome)
    }

    @Test
    fun physicsNeverMutatesTheTerrain() {
        val scenario = generate(4242L, G.W_MIN)
        val before = scenario.terrain.fingerprint()
        for (angle in 10..80 step 10) {
            simulate(scenario, 0, Shot(0, angle, 70), wind = 3)
        }
        assertEquals("physics has touched the terrain", before, scenario.terrain.fingerprint())
    }
}
