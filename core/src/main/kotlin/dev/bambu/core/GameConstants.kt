package dev.bambu.core

/**
 * Constantes del contrato (§2 de la especificación).
 *
 * Cualquier cambio aquí altera el comportamiento de partidas ya jugadas y la
 * compatibilidad entre dispositivos: se cambia primero en `ESPEC_DESARROLLO.md`.
 */
object G {
    // Lienzo lógico
    const val H = 200 // altura lógica fija
    const val W_MIN = 320
    const val W_MAX = 460
    const val SKY_BAND = 60 // banda superior libre de edificios

    // Integración
    const val DT = 1f / 120f
    const val MAX_FLIGHT_S = 15f
    const val MAX_STEPS = 1800 // MAX_FLIGHT_S / DT

    // Física
    const val GRAVITY = 80f // px/s²
    const val WIND_ACCEL = 1.5f // px/s² por unidad de viento (viento ∈ -10..10)
    const val POWER_TO_SPEED = 2.2f // potencia 1..100 -> 2.2..220 px/s

    // Cuerpos
    const val BANANA_R = 3
    const val CRATER_R = 12
    const val GORILLA_W = 16
    const val GORILLA_H = 20
    const val HAND_DX = 10 // desplazamiento horizontal del punto de lanzamiento
    const val HAND_DY = 22 // altura del punto de lanzamiento sobre el tejado

    // Escenario
    const val BUILD_W_MIN = 24
    const val BUILD_W_MAX = 40
    const val BUILD_H_MIN = 40
    const val BUILD_H_MAX = 130
    const val SUN_W = 20
    const val SUN_H = 20

    // Rangos de disparo
    const val ANGLE_MIN = 0
    const val ANGLE_MAX = 90
    const val POWER_MIN = 1
    const val POWER_MAX = 100

    /**
     * Pasos iniciales en los que se ignora la colisión con el propio tirador.
     * El punto de lanzamiento (HAND_DX = 10) cae dentro del AABB propio expandido
     * por el radio del plátano (GORILLA_W/2 + BANANA_R = 11), así que sin esta
     * gracia todo disparo sería un autogol inmediato.
     */
    const val SELF_HIT_GRACE_STEPS = 5
}
