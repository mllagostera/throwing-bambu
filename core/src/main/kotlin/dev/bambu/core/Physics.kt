package dev.bambu.core

import kotlin.math.abs

/** Margen fuera del lienzo tras el cual el proyectil se da por perdido (§8). */
private const val OFFSCREEN_MARGIN = 20

/**
 * Simula un disparo completo (§8). Función pura: no dibuja, no anima y **no muta el
 * terreno** — el cráter lo aplica el llamador.
 *
 * Devuelve un `path` propio, recortado a `steps * 2`.
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
 * Igual que [simulate] pero escribiendo en un búfer prestado, sin asignar nada.
 *
 * El `path` del resultado apunta a `scratch`: solo es válido hasta la siguiente llamada.
 * Está pensado para la búsqueda de la IA, que descarta cada trayectoria. **Nunca** debe
 * publicarse en un `MatchEvent` (D-01).
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount")
fun simulateInto(
    scenario: Scenario,
    shooter: Int,
    shot: Shot,
    wind: Int,
    scratch: FloatArray,
): ShotResult {
    require(shot.angle in G.ANGLE_MIN..G.ANGLE_MAX) { "ángulo fuera de rango: ${shot.angle}" }
    require(shot.power in G.POWER_MIN..G.POWER_MAX) { "potencia fuera de rango: ${shot.power}" }
    require(scratch.size >= G.MAX_STEPS * 2) { "scratch demasiado pequeño: ${scratch.size}" }

    val dir = if (shooter == 0) 1 else -1
    val me = scenario.gorillas[shooter]
    val opponent = 1 - shooter
    val terrain = scenario.terrain
    val hit = IntArray(2)

    var x = (me.x + dir * G.HAND_DX).toFloat()
    var y = (me.roofY - G.HAND_DY).toFloat()
    val speed = shot.power * G.POWER_TO_SPEED
    var vx = dir * COS[shot.angle] * speed
    var vy = -SIN[shot.angle] * speed // y crece hacia abajo
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
        // El cielo está abierto por arriba: el proyectil puede salir y volver.
        if (y < 0f) continue

        // El sol no frena el plátano: solo cambia de expresión. Es un detalle del original.
        if (hitsSun(scenario, x, y)) sunHit = true

        if (hitsGorilla(scenario.gorillas[opponent], x, y)) {
            return ShotResult(scratch, steps, Outcome.HitGorilla(opponent), x.toInt(), y.toInt(), sunHit)
        }
        // El autogol es legal, pero el punto de lanzamiento cae dentro del AABB propio:
        // sin estos pasos de gracia todo disparo terminaría en el paso 1.
        if (step >= G.SELF_HIT_GRACE_STEPS && hitsGorilla(me, x, y)) {
            return ShotResult(scratch, steps, Outcome.HitGorilla(shooter), x.toInt(), y.toInt(), sunHit)
        }

        if (terrain.firstSolidOnSegment(prevX, prevY, x, y, hit)) {
            return ShotResult(scratch, steps, Outcome.HitTerrain(hit[0], hit[1]), hit[0], hit[1], sunHit)
        }
    }

    return ShotResult(scratch, G.MAX_STEPS, Outcome.TimeOut, x.toInt(), y.toInt(), sunHit)
}

/** AABB del gorila centrado en `(x, roofY - GORILLA_H/2)`, expandido por el radio del plátano. */
private fun hitsGorilla(
    g: Gorilla,
    x: Float,
    y: Float,
): Boolean {
    if (!g.alive) return false
    val halfW = G.GORILLA_W / 2 + G.BANANA_R
    val halfH = G.GORILLA_H / 2 + G.BANANA_R
    val cy = g.roofY - G.GORILLA_H / 2
    return abs(x - g.x) <= halfW && abs(y - cy) <= halfH
}

private fun hitsSun(
    scenario: Scenario,
    x: Float,
    y: Float,
): Boolean {
    val halfW = G.SUN_W / 2 + G.BANANA_R
    return abs(x - scenario.sunX) <= halfW && y <= G.SUN_H + G.BANANA_R
}
