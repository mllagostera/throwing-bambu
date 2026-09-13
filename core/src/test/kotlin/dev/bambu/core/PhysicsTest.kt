package dev.bambu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhysicsTest {
    private val launchX = (TestScenarios.SHOOTER_X + G.HAND_DX).toFloat()
    private val launchY = TestScenarios.LAUNCH_Y.toFloat()

    /**
     * §15.4 y calibración del §2: sin viento ni obstáculos, el alcance a 45° coincide con
     * `v²/g` dentro del 2 %. Con potencia 68 eso son 279,8 px, no los ~300 px que decía
     * la versión inicial de la especificación (D-05).
     */
    @Test
    fun rangeAt45DegreesMatchesBallistics() {
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, shooter = 0, shot = Shot(0, 45, 68), wind = 0)

        val speed = 68 * G.POWER_TO_SPEED
        val expected = speed * speed / G.GRAVITY
        val measured = TestScenarios.rangeAtLaunchHeight(result, launchX, launchY)

        assertTrue("el proyectil nunca vuelve a la altura de lanzamiento", measured > 0)
        assertEquals("alcance fuera de tolerancia", expected.toDouble(), measured.toDouble(), expected * 0.02)
        assertEquals("la calibración del §2 ha cambiado", 279.8, expected.toDouble(), 0.5)
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
        assertEquals("tiempo de vuelo fuera de la calibración", 2.645, seconds.toDouble(), 0.05)
    }

    /** §15.5: el viento desplaza el impacto de forma simétrica. */
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

        assertTrue("el viento a favor no alarga el disparo", tail > calm)
        assertTrue("el viento en contra no acorta el disparo", head < calm)
        assertEquals("el viento no es simétrico", (tail - calm).toDouble(), (calm - head).toDouble(), 1.0)
    }

    /** §15.7: impacto en el gorila enemigo. */
    @Test
    fun hittingTheOpponentIsReported() {
        // Primero se mide dónde cae el disparo, y después se coloca al enemigo ahí.
        val probe = simulate(TestScenarios.empty(), 0, Shot(0, 45, 68), wind = 0)
        val impact = TestScenarios.rangeAtLaunchHeight(probe, launchX, launchY)
        val target = (launchX + impact).toInt()

        val scenario = TestScenarios.empty(opponentX = target, opponentAlive = true)
        val result = simulate(scenario, 0, Shot(0, 45, 68), wind = 0)

        assertEquals(Outcome.HitGorilla(1), result.outcome)
        assertTrue("impacto demasiado lejos del objetivo", abs(result.impactX - target) <= G.GORILLA_W)
    }

    /** §15.8: a 90° con potencia baja el plátano vuelve y golpea a quien lo lanzó. */
    @Test
    fun straightUpShotComesBackAndHitsTheShooter() {
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, 0, Shot(0, 90, 20), wind = 0)

        assertEquals(Outcome.HitGorilla(0), result.outcome)
        // Si impactara dentro de los pasos de gracia sería un autogol falso, no un regreso.
        assertTrue("el proyectil no llegó a volar: ${result.steps} pasos", result.steps > 20)
    }

    @Test
    fun theShotLeavesItsOwnHitboxBeforeGraceEnds() {
        // La gracia de 5 pasos solo es válida si para entonces el plátano ya salió del AABB.
        val scenario = TestScenarios.empty()
        val result = simulate(scenario, 0, Shot(0, 90, 20), wind = 0)
        val yAtGrace = result.path[(G.SELF_HIT_GRACE_STEPS - 1) * 2 + 1]
        val topOfHitbox = TestScenarios.ROOF_Y - G.GORILLA_H / 2 - (G.GORILLA_H / 2 + G.BANANA_R)
        assertTrue(
            "al acabar la gracia el plátano sigue dentro del AABB propio ($yAtGrace >= $topOfHitbox)",
            yAtGrace < topOfHitbox,
        )
    }

    /** §15.9: `simulate` nunca excede MAX_STEPS ni escribe fuera de `path`. */
    @Test
    fun neverExceedsMaxStepsOrPathBounds() {
        val scenario = TestScenarios.empty()
        for (angle in 0..90 step 5) {
            for (power in intArrayOf(1, 25, 50, 75, 100)) {
                for (wind in intArrayOf(-10, 0, 10)) {
                    val r = simulate(scenario, 0, Shot(0, angle, power), wind)
                    assertTrue("steps fuera de rango: ${r.steps}", r.steps in 1..G.MAX_STEPS)
                    assertEquals("path mal dimensionado", r.steps * 2, r.path.size)
                    for (v in r.path) assertTrue("coordenada no finita", v.isFinite())
                }
            }
        }
    }

    /** D-01: el `path` publicado es propio; el de `simulateInto` es prestado. */
    @Test
    fun simulateCopiesThePathButSimulateIntoDoesNot() {
        val scenario = TestScenarios.empty()
        val owned = simulate(scenario, 0, Shot(0, 45, 68), 0)

        val scratch = FloatArray(G.MAX_STEPS * 2)
        simulateInto(scenario, 0, Shot(0, 30, 50), 0, scratch)
        val borrowed = simulateInto(scenario, 0, Shot(0, 60, 90), 0, scratch)

        assertTrue("simulateInto debería devolver el búfer prestado", borrowed.path === scratch)
        // Una segunda simulación no puede alterar un resultado ya publicado.
        val first = owned.path.copyOf()
        simulateInto(scenario, 0, Shot(0, 10, 100), 0, scratch)
        assertTrue("el path publicado fue sobrescrito", first.contentEquals(owned.path))
    }

    /**
     * A3 (D-03): sin muestreo de segmento, el plátano atraviesa una losa fina en la caída.
     * El test comprueba además que el paso que impacta salta más de un píxel: si no lo
     * hiciera, no estaría probando nada.
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
        assertTrue("el paso final no salta más de 1 px ($jump): el test no prueba tunelado", jump > 1.5f)
    }

    /**
     * Cota del avance por paso, medida y no estimada: un barrido completo de ángulos,
     * potencias, vientos y alturas de tejado da un máximo de 2,50 px (2,05 horizontal,
     * 2,32 vertical). Por encima de 1 px, el muestreo puntual ya puede saltarse un istmo
     * de terreno, que es lo que justifica el DDA de [firstSolid] (D-03).
     *
     * Si este techo sube, el margen anti-tunelado se estrecha y hay que revisar D-03.
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
                        val d = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (d > maxStep) maxStep = d
                    }
                }
            }
        }
        assertTrue("el avance por paso ha subido a $maxStep px (medido: 2,50)", maxStep < 2.6f)
        assertTrue("el avance por paso supera 1 px: el DDA es necesario", maxStep > 1f)
    }

    @Test
    fun theSunDoesNotStopTheBanana() {
        // §8: el sol cambia de expresión, no frena el proyectil. Por eso no hay Outcome.HitSun.
        val scenario = TestScenarios.empty()
        var sawSun = false
        for (angle in 40..85) {
            val r = simulate(scenario, 0, Shot(0, angle, 100), wind = 0)
            if (r.sunHit) {
                sawSun = true
                assertNotEquals("el sol terminó el vuelo", 0, r.steps)
                assertTrue(
                    "el vuelo acabó en el sol",
                    r.outcome is Outcome.OffScreen || r.outcome is Outcome.HitTerrain,
                )
            }
        }
        assertTrue("ningún disparo alcanzó el sol: el test no comprueba nada", sawSun)
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
        assertEquals("la física ha tocado el terreno", before, scenario.terrain.fingerprint())
    }
}
