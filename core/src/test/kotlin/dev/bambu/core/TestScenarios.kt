package dev.bambu.core

/**
 * Escenarios sintéticos para los tests de física.
 *
 * El oponente se crea con `alive = false` a propósito: en las pruebas balísticas
 * interesa medir la trayectoria sin que un AABB en mitad del vuelo la corte.
 */
object TestScenarios {
    const val FLAT_WIDTH = 460
    const val ROOF_Y = 150
    const val SHOOTER_X = 60

    /** Altura del punto de lanzamiento del tirador 0. */
    const val LAUNCH_Y = ROOF_Y - G.HAND_DY

    /** Lienzo sin un solo píxel sólido: balística pura. */
    fun empty(
        opponentX: Int = 440,
        opponentAlive: Boolean = false,
        shooterRoofY: Int = ROOF_Y,
    ): Scenario = withMask(BooleanArray(FLAT_WIDTH * G.H), opponentX, opponentAlive, shooterRoofY)

    /** Lienzo vacío salvo una losa horizontal de un píxel de grosor. */
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
            gorillas =
                listOf(
                    Gorilla(0, SHOOTER_X, shooterRoofY),
                    Gorilla(1, opponentX, ROOF_Y, alive = opponentAlive),
                ),
            sunX = FLAT_WIDTH / 2,
        )
    }

    /**
     * Alcance horizontal hasta volver a la altura de lanzamiento, leído de la trayectoria.
     * Devuelve -1 si el proyectil nunca vuelve a bajar hasta ahí.
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
