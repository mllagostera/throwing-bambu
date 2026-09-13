package dev.bambu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainTest {
    /** §15.6: `blast` borra exactamente el círculo, ni un píxel más. */
    @Test
    fun blastClearsExactlyTheCircle() {
        val w = G.W_MIN
        val mask = BooleanArray(w * G.H) { true }
        val terrain = Terrain(w, mask, ByteArray(w * G.H) { Palette.IDX_FACADE_FIRST }, emptyList())

        val cx = 100
        val cy = 100
        val r = G.CRATER_R
        terrain.blast(cx, cy, r)

        for (y in 0 until G.H) {
            for (x in 0 until w) {
                val dx = x - cx
                val dy = y - cy
                val inside = dx * dx + dy * dy <= r * r
                assertEquals(
                    "píxel ($x,$y) ${if (inside) "debería estar borrado" else "no debería tocarse"}",
                    !inside,
                    terrain.solid(x, y),
                )
                if (inside) assertEquals(Palette.IDX_EMPTY, terrain.color[y * w + x])
            }
        }
    }

    @Test
    fun blastClipsAtTheEdgesWithoutCrashing() {
        val w = G.W_MIN
        val terrain = Terrain(w, BooleanArray(w * G.H) { true }, ByteArray(w * G.H), emptyList())
        terrain.blast(0, 0, G.CRATER_R)
        terrain.blast(w - 1, G.H - 1, G.CRATER_R)
        assertFalse(terrain.solid(0, 0))
        assertFalse(terrain.solid(w - 1, G.H - 1))
        assertTrue(terrain.solid(w / 2, G.H / 2))
    }

    @Test
    fun solidIsFalseOutsideTheCanvas() {
        val w = G.W_MIN
        val terrain = Terrain(w, BooleanArray(w * G.H) { true }, ByteArray(w * G.H), emptyList())
        assertFalse(terrain.solid(-1, 10))
        assertFalse(terrain.solid(w, 10))
        assertFalse(terrain.solid(10, -1))
        assertFalse(terrain.solid(10, G.H))
    }

    @Test
    fun fingerprintReactsToASinglePixel() {
        val w = G.W_MIN
        val a = Terrain(w, BooleanArray(w * G.H) { true }, ByteArray(w * G.H), emptyList())
        val before = a.fingerprint()
        a.mask[1234] = false
        assertTrue("la huella ignora cambios en la máscara", before != a.fingerprint())
    }
}
