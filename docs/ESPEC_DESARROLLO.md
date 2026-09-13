# Gorilas Android — Especificación de desarrollo

Documento destinado a un agente de programación. Todo lo que aparece aquí como constante o firma es **contrato**, no sugerencia. Si algo se cambia, se cambia en este documento primero.

---

## 0. Reglas de oro

1. **`:core` no importa nada de Android.** Si aparece un `import android.*` en `:core`, es un error de diseño.
2. **La física es una función pura.** `simulate(...)` recibe estado y devuelve trayectoria completa + resultado. No dibuja, no anima, no muta nada.
3. **Los tres modos de juego comparten el mismo bucle.** Solo cambia de dónde viene el `Shot`.
4. **Píxel entero siempre.** Nada de escalados fraccionarios ni interpolación bilineal.
5. **Determinismo.** Misma semilla + mismos disparos = misma partida, en cualquier dispositivo.

---

## 1. Estructura de módulos

```
gorilas/
├── core/          Kotlin JVM puro. Sin Android. Testeable con JUnit.
├── transport/     Android. Abstracción de conectividad + implementaciones.
└── app/           Android. Compose, render, navegación, audio.
```

- `minSdk = 24`, `targetSdk = 35`, `compileSdk = 35`
- Kotlin 2.x, Compose BOM, `kotlinx.coroutines`
- `transport` depende de `core`. `app` depende de ambos. `core` no depende de nadie.
- Gestión de versiones con `gradle/libs.versions.toml`.

---

## 2. Constantes compartidas (`core/GameConstants.kt`)

```kotlin
object G {
    // Lienzo lógico
    const val H            = 200      // altura lógica fija
    const val W_MIN        = 320
    const val W_MAX        = 460
    const val SKY_BAND     = 60       // banda superior libre de edificios

    // Integración
    const val DT           = 1f / 120f
    const val MAX_FLIGHT_S = 15f
    const val MAX_STEPS    = 1800     // MAX_FLIGHT_S / DT

    // Física
    const val GRAVITY         = 80f   // px/s²
    const val WIND_ACCEL      = 1.5f  // px/s² por unidad de viento (viento ∈ -10..10)
    const val POWER_TO_SPEED  = 2.2f  // potencia 1..100 -> 2.2..220 px/s

    // Cuerpos
    const val BANANA_R   = 3
    const val CRATER_R   = 12
    const val GORILLA_W  = 16
    const val GORILLA_H  = 20
    const val HAND_DX    = 10         // desplazamiento horizontal del punto de lanzamiento
    const val HAND_DY    = 22         // altura del punto de lanzamiento sobre el tejado

    // Escenario
    const val BUILD_W_MIN = 24
    const val BUILD_W_MAX = 40
    const val BUILD_H_MIN = 40
    const val BUILD_H_MAX = 130
    const val SUN_W       = 20
    const val SUN_H       = 20
}
```

**Calibración esperada** (verificar en M1): un disparo a 45° con potencia 68 debe recorrer **~280 px** y durar **~2,65 s**.

Derivación, para que nadie la vuelva a estimar a ojo: `v = 68 × POWER_TO_SPEED = 149,6 px/s`; alcance `v²·sin(2θ)/g = 149,6²/80 = 279,8 px`; tiempo `2·v·sin45°/g = 2,645 s`. El test §15.4 exige ±2 %, así que la cifra de referencia es **279,8 px**, no una redondeada al alza. Si la medición no cuadra con esto, el fallo está en la implementación, no en `GRAVITY`.

---

## 3. Anchura lógica del lienzo

```kotlin
// core/Layout.kt — única fuente de verdad. El render NO recalcula la escala.
fun logicalScale(screenW: Int, screenH: Int): Int {
    var scale = maxOf(1, screenH / G.H)
    while (scale > 1 && screenW / scale < G.W_MIN) scale--
    return scale
}

fun logicalWidth(screenW: Int, screenH: Int): Int =
    (screenW / logicalScale(screenW, screenH)).coerceIn(G.W_MIN, G.W_MAX)
```

La escala no puede derivarse solo de la altura: con una escala tomada de `screenH` y un mínimo forzado de `W_MIN`, el lienzo puede acabar siendo **más ancho que la pantalla** (1080×2400 en vertical daría `scale = 12` y `320 × 12 = 3840 px` sobre 1080 disponibles). El bucle baja la escala hasta que `W_MIN` cabe de verdad.

La altura lógica es **siempre 200**. La anchura varía; eso cambia cuántos edificios caben (entre 8 y 19, según anchuras sorteadas), no la escala de nada. Sobrante horizontal → barras laterales del color del cielo, no estiramiento.

La pantalla de juego se bloquea en **landscape** (`android:screenOrientation="sensorLandscape"`). En vertical el lienzo cabe, pero la escala resultante desperdicia la mitad de la pantalla y los controles no entran bajo el lienzo.

---

## 4. RNG determinista (`core/Rng.kt`)

No usar `java.util.Random` ni `kotlin.random.Random`. Implementar xorshift64\*:

```kotlin
class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    fun nextLong(): Long {
        s = s xor (s ushr 12); s = s xor (s shl 25); s = s xor (s ushr 27)
        return s * -0x7ea3_5b2f_1c0d_49a7L
    }
    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()
    fun nextIntRange(a: Int, b: Int): Int = a + nextInt(b - a + 1)
    fun nextFloat(): Float = ((nextLong() ushr 40) / 16777216f)
    fun nextGaussian(): Float  // Box-Muller con nextFloat()
}
```

### Viento derivado, no transmitido

```kotlin
fun windForTurn(seed: Long, turn: Int): Int =
    Rng(seed * 0x100000001B3L + turn).nextIntRange(-10, 10)
```

Consecuencia de diseño: el viento **no viaja por la red**. Ambos dispositivos lo calculan. Esto elimina una clase entera de bugs de sincronización.

---

## 5. Trigonometría determinista (`core/Trig.kt`)

`Math.sin` no garantiza resultados bit a bit idénticos entre implementaciones de JVM. Como el ángulo es un entero de grados:

```kotlin
internal val SIN = FloatArray(91) { StrictMath.sin(it * StrictMath.PI / 180.0).toFloat() }
internal val COS = FloatArray(91) { StrictMath.cos(it * StrictMath.PI / 180.0).toFloat() }
```

Prohibido llamar a `sin()`/`cos()` en la ruta de simulación.

---

## 6. Modelos de datos (`core/Model.kt`)

```kotlin
// Sin `data`: un data class con un array compara por identidad, no por contenido,
// y los tests §15.2 y §15.11 pasarían o fallarían por el motivo equivocado.
class Building(
    val x: Int, val width: Int, val height: Int,
    val paletteIdx: Int,          // índice en la paleta de fachadas
    val windows: BooleanArray     // encendida/apagada, orden fila-mayor
) {
    override fun equals(other: Any?): Boolean = other is Building &&
        x == other.x && width == other.width && height == other.height &&
        paletteIdx == other.paletteIdx && windows.contentEquals(other.windows)

    override fun hashCode(): Int = /* incluye windows.contentHashCode() */ 0
}

class Terrain(
    val width: Int,
    val mask: BooleanArray,       // width * G.H, true = sólido
    val color: ByteArray,         // width * G.H, índice de paleta por píxel
    val buildings: List<Building>
) {
    fun solid(x: Int, y: Int): Boolean
    fun blast(cx: Int, cy: Int, r: Int)   // pone a false un círculo
    fun fingerprint(): Long               // huella de mask+color; es lo que comparan los tests
}

data class Gorilla(val player: Int, val x: Int, val roofY: Int, var alive: Boolean = true)
// x = centro horizontal; roofY = píxel del tejado donde apoya los pies

data class Scenario(val width: Int, val terrain: Terrain, val gorillas: List<Gorilla>, val sunX: Int)

data class Shot(val turn: Int, val angle: Int, val power: Int)  // angle 0..90, power 1..100

sealed interface Outcome {
    data class HitGorilla(val player: Int) : Outcome
    data class HitTerrain(val x: Int, val y: Int) : Outcome
    data object OffScreen : Outcome
    data object TimeOut : Outcome
}

class ShotResult(
    val path: FloatArray,    // pares x,y intercalados: path[2i], path[2i+1]; tamaño exacto steps*2
    val steps: Int,
    val outcome: Outcome,
    val impactX: Int, val impactY: Int,
    val sunHit: Boolean      // el sol cambió de expresión durante este vuelo
)
```

**No hay `Outcome.HitSun`.** §8 establece que el sol no detiene el proyectil, luego ese resultado sería inalcanzable. El impacto en el sol es un efecto visual y viaja en `sunHit`.

**Propiedad de `path`.** El `ShotResult` que publica `MatchEngine` lleva un `FloatArray` **propio y recortado a `steps * 2`**. Nada de pool en esa ruta: la UI anima ese array durante ~2,6 s mientras la IA lanza cientos de simulaciones, y un buffer compartido se sobrescribiría a mitad de animación. Son ~2,4 KB por turno, una asignación cada varios segundos.

La reutilización sí tiene sentido dentro de la IA, que simula en bucle cerrado y descarta cada trayectoria. Para eso existe la variante del §8 que escribe sobre un buffer prestado.

---

## 7. Generación de escenario (`core/ScenarioGen.kt`)

```kotlin
fun generate(seed: Long, width: Int): Scenario
```

Algoritmo, en este orden exacto (cualquier reordenación rompe el determinismo entre versiones):

1. `rng = Rng(seed)`
2. Elegir `skylineMode = rng.nextInt(3)` → `ASCENDENTE`, `DESCENDENTE`, `ALEATORIO`. Replica la variedad del original.
3. Recorrer `x` desde 0 acumulando edificios de anchura `rng.nextIntRange(BUILD_W_MIN, BUILD_W_MAX)` hasta cubrir `width`. El último se recorta a la anchura restante; si queda < `BUILD_W_MIN`, se suma al anterior.
4. Altura por edificio según modo:
   - `ASCENDENTE`: `h = lerp(BUILD_H_MIN, BUILD_H_MAX, i/(n-1)) + ruido(±15)`
   - `DESCENDENTE`: inverso
   - `ALEATORIO`: `rng.nextIntRange(BUILD_H_MIN, BUILD_H_MAX)`
   - Clamp final a `[BUILD_H_MIN, BUILD_H_MAX]`.
5. `paletteIdx = rng.nextInt(nFachadas)`
6. Ventanas: rejilla de 3×4 px con margen 3 px y separación 3 px. Cada ventana encendida con `p = 0.5`.
7. Pintar `mask` y `color`.
8. Gorilas en los edificios de índice **1** y **n-2**. `x = building.x + building.width/2`, `roofY = G.H - building.height`. Si alguno de esos edificios mide menos de `GORILLA_W + 4`, desplazar al índice adyacente hacia el centro.
9. Sol en `sunX = width / 2`, `y = 0..SUN_H`.

**Invariante a testear:** ningún edificio tiene su tejado por encima de `SKY_BAND`, y los dos gorilas nunca quedan en el mismo edificio.

---

## 8. Física (`core/Physics.kt`)

```kotlin
fun simulate(
    scenario: Scenario,
    shooter: Int,          // 0 = izquierda, 1 = derecha
    shot: Shot,
    wind: Int
): ShotResult

// Variante para la IA: escribe la trayectoria en `scratch` (tamaño MAX_STEPS*2, propiedad
// del llamador) y no asigna nada. El ShotResult devuelto apunta a `scratch`: solo es válido
// hasta la siguiente llamada. NUNCA publicar este resultado en un MatchEvent.
fun simulateInto(
    scenario: Scenario,
    shooter: Int,
    shot: Shot,
    wind: Int,
    scratch: FloatArray
): ShotResult
```

Implementación:

```
dir     = if (shooter == 0) +1 else -1
g       = scenario.gorillas[shooter]
x       = g.x + dir * HAND_DX
y       = g.roofY - HAND_DY
speed   = shot.power * POWER_TO_SPEED
vx      = dir * COS[shot.angle] * speed
vy      = -SIN[shot.angle] * speed          // y crece hacia abajo
windA   = wind * WIND_ACCEL

repeat MAX_STEPS:
    x  += vx * DT
    y  += vy * DT
    vy += GRAVITY * DT
    vx += windA * DT
    registrar (x, y) en path

    si x < -20 o x > width + 20        -> OffScreen
    si y > H                           -> OffScreen
    si y < 0                           -> continuar (el cielo está abierto por arriba)

    si colisionaSol(x, y)              -> sunHit = true, NO termina el vuelo
    si colisionaGorilla(oponente)      -> HitGorilla(oponente)
    si colisionaGorilla(tirador)       -> HitGorilla(tirador)   // autogol, es legal
    si terrain.solid(x.toInt(), y.toInt()) -> HitTerrain(x, y)

si se agotan los pasos -> TimeOut
```

Detalles no negociables:

- **El plátano no colisiona con el sol en sentido físico.** El sol cambia de expresión y el proyectil sigue. Es un detalle del original.
- **Colisión con gorila:** AABB de `GORILLA_W × GORILLA_H` centrado en `(g.x, g.roofY - GORILLA_H/2)`, expandido por `BANANA_R`.
- **Autocolisión inicial:** el punto de partida está fuera del AABB propio por diseño (`HAND_DX = 10 > GORILLA_W/2 + BANANA_R = 11`)… **corrección: 10 < 11**. Subir `HAND_DX` a **12** o ignorar la colisión con el tirador durante los primeros 5 pasos. Elegir lo segundo, que es más robusto frente a ángulos altos.
- **Anti-tunelado por muestreo de segmento.** El cálculo «220 px/s → 1,83 px por paso» solo vale para el primer paso: `vy` crece sin límite mientras dura el vuelo, y a los 5 s ya son 400 px/s (3,3 px/paso), con un techo de ~10 px/paso a los 15 s. Tras varios cráteres el terreno deja istmos de 1–3 px, que es exactamente lo que un muestreo puntual atraviesa. Por tanto, la comprobación contra el terreno recorre el segmento `(x₀,y₀) → (x₁,y₁)` con un DDA entero y evalúa `solid()` en **cada píxel del trayecto**; `impactX/impactY` es el **primer** píxel sólido del segmento, no el extremo del paso. Coste: una comprobación por paso en el caso habitual, ≤10 en el peor.
- Tras `HitTerrain` o `HitGorilla`, el llamador aplica `terrain.blast(impactX, impactY, CRATER_R)`. La física **no** muta el terreno.

---

## 9. IA (`core/Ai.kt`)

```kotlin
enum class AiLevel(val sigmaAngle: Float, val sigmaPower: Float, val refine: Boolean) {
    FACIL(10f, 12f, false),
    MEDIO(4f, 5f, true),
    DIFICIL(1f, 1.5f, true)
}

class AiOpponent(private val level: AiLevel, private val rng: Rng) {
    fun chooseShot(scenario: Scenario, me: Int, wind: Int, turn: Int): Shot
}
```

Algoritmo:

1. **Búsqueda gruesa:** ángulo 10..85 paso 5, potencia 20..100 paso 5 → 272 simulaciones. Puntuación = distancia euclídea del punto de impacto al centro del gorila enemigo. `OffScreen` y `TimeOut` puntúan `Float.MAX_VALUE`. `HitGorilla(me)` también.
2. **Refinado** (si `level.refine`): rejilla ±4 en ángulo y ±4 en potencia, paso 1, alrededor del mejor.
3. **Ruido:** `angle += rng.nextGaussian() * sigmaAngle`, ídem potencia. Clamp a rangos válidos.

Coste: ~272 × ~350 pasos ≈ 95 k iteraciones (353 simulaciones si hay refinado). Milisegundos. Aun así, ejecutar en `Dispatchers.Default` y mostrar un retardo artificial de 600–1200 ms para que el turno se lea bien.

`AiOpponent` posee **su propio** `FloatArray(G.MAX_STEPS * 2)` y llama a `simulateInto` (§8). Es el único punto del sistema donde se reutiliza el buffer de trayectoria, y ninguno de esos `ShotResult` sale de la clase.

**No implementar** una solución analítica de balística. El viento y los edificios interpuestos la invalidan y no aporta nada que la búsqueda no dé.

---

## 10. Fuente de disparos (`core/OpponentSource.kt`)

La pieza que unifica los tres modos:

```kotlin
interface ShotSource {
    suspend fun nextShot(scenario: Scenario, me: Int, wind: Int, turn: Int): Shot
}
```

- `HumanShotSource` — suspende hasta que la UI emite un `Shot` por un `Channel`.
- `AiShotSource` — envuelve `AiOpponent`.
- `RemoteShotSource` — suspende hasta recibir `Msg.Shot` con el `turn` esperado por el transporte.

`MatchEngine` no sabe cuál tiene delante. Si acabas escribiendo tres bucles de partida, el diseño se ha roto.

---

## 11. Motor de partida (`core/MatchEngine.kt`)

```kotlin
class MatchEngine(
    val seed: Long,
    val width: Int,
    val sources: List<ShotSource>,   // índice = jugador
    val roundsToWin: Int = 3
) {
    val events: Flow<MatchEvent>
    suspend fun run()
}

sealed interface MatchEvent {
    data class RoundStart(val scenario: Scenario, val wind: Int) : MatchEvent
    data class TurnStart(val player: Int, val wind: Int, val turn: Int) : MatchEvent
    data class ShotFired(val shot: Shot, val result: ShotResult) : MatchEvent
    data class TerrainChanged(val cx: Int, val cy: Int, val r: Int) : MatchEvent
    class RoundEnd(val winner: Int, val scores: IntArray) : MatchEvent  // sin `data`: lleva array
    data class MatchEnd(val winner: Int) : MatchEvent
}
```

El bucle: `TurnStart` → `sources[current].nextShot(...)` → `simulate` → `ShotFired` → aplicar cráter → evaluar → cambiar turno. La UI se limita a consumir `events` y animar.

`MatchEngine` usa `simulate` (array propio), nunca `simulateInto`: los eventos sobreviven al turno que los produjo. `scores` se copia al construir `RoundEnd`, para que el evento no exponga el array interno del motor.

---

## 12. Transporte (`transport/`)

```kotlin
interface Transport {
    val incoming: Flow<Msg>
    val state: StateFlow<LinkState>   // IDLE, ADVERTISING, DISCOVERING, CONNECTING, CONNECTED, LOST
    suspend fun advertise(nick: String)
    fun discover(): Flow<Peer>
    suspend fun connect(peer: Peer)
    suspend fun send(msg: Msg)
    fun close()
}
```

Implementaciones:

| Clase | Base | Uso |
|---|---|---|
| `NearbyTransport` | `play-services-nearby`, estrategia `P2P_POINT_TO_POINT` | Principal |
| `RfcommTransport` | `BluetoothSocket` + UUID SPP fijo | Respaldo sin Google Play Services |
| `LoopbackTransport` | Dos `Channel` cruzados en memoria | Tests |

### Protocolo binario

Todos los enteros en big-endian. Byte 0 = versión de protocolo (`0x01`), byte 1 = tipo.

| Tipo | Nombre | Cuerpo |
|---|---|---|
| `0x01` | `HELLO` | `u8 nickLen`, `nick UTF-8` |
| `0x02` | `MATCH_START` | `i64 seed`, `u16 width`, `u8 startingPlayer`, `u8 roundsToWin` |
| `0x03` | `SHOT` | `u16 turn`, `u8 angle`, `u8 power` |
| `0x04` | `RESULT` | `u16 turn`, `u8 outcomeCode`, `u16 impactX`, `u16 impactY` |
| `0x05` | `REMATCH` | — |
| `0x06` | `BYE` | `u8 reason` |
| `0x07` | `PING` | `i64 nonce` |

Reglas:

- **El anfitrión es autoridad.** Genera `seed`, decide `width` (el menor de los dos lienzos lógicos, negociado en `HELLO` — añadir `u16 width` al `HELLO`) y quién empieza.
- `SHOT` con `turn` distinto del esperado se **descarta**, no se encola. Protege contra reenvíos.
- `RESULT` es verificación, no fuente de verdad. Si el receptor calcula un impacto que difiere en más de 2 px, registra la divergencia y **adopta el valor del emisor**. Si ocurre más de una vez por partida, es un bug de determinismo: hay que arreglarlo, no taparlo.
- Timeout de turno: 90 s sin `SHOT` → `BYE(TIMEOUT)`.
- `PING` cada 10 s en reposo; 3 fallos → `LinkState.LOST`.

### Permisos

```xml
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" android:minSdkVersion="31"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"  android:minSdkVersion="31"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"     android:minSdkVersion="31"/>
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" android:minSdkVersion="33"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
```

Solicitar en runtime **solo al entrar en el modo Bluetooth**, nunca al arrancar. Pantalla explicativa previa al diálogo del sistema.

---

## 13. Render (`app/`)

### Terreno

- Búfer `IntArray(width * G.H)` en ARGB, generado desde `Terrain.mask` + `Terrain.color` + paleta.
- Convertir a `ImageBitmap` vía `Bitmap.createBitmap(buf, w, h, ARGB_8888).asImageBitmap()`.
- Regenerar **solo** al aplicar un cráter (una vez por turno). No por frame.

### Dibujo

```kotlin
Canvas(Modifier.fillMaxSize()) {
    // La escala viene de core/Layout.kt (§3). El render NO la recalcula: si los dos
    // cálculos divergen, el lienzo se sale de la pantalla o deja bandas muertas.
    val scale = logicalScale(size.width.toInt(), size.height.toInt())
    // cielo, skyline, terreno, sol, gorilas, plátano, estela
    drawImage(
        image = terrainBitmap,
        dstSize = IntSize(w * scale, G.H * scale),
        filterQuality = FilterQuality.None
    )
}
```

`FilterQuality.None` en **todas** las llamadas a `drawImage`. Sin excepciones.

### Animación del disparo

`ShotResult.path` contiene ~300 puntos a 120 Hz. Reproducir a 60 fps consumiendo 2 puntos por frame → duración real del vuelo. Añadir factor `speedMultiplier` (1×, 2×) en ajustes, que solo cambia cuántos puntos se consumen por frame.

Estela: los últimos 12 puntos con opacidad decreciente.

### Explosión

`boom.png`, 8 fotogramas a 60 ms = 480 ms. El cráter se aplica al terreno **en el fotograma 3**, no al inicio ni al final.

---

## 14. Interfaz

La UI **no** vive en el lienzo lógico. Va en dp nativos, superpuesta.

- **HUD superior:** marcador, nombre del jugador en turno, indicador de viento (flecha cuya longitud es proporcional a `|wind|`, con el valor numérico al lado).
- **Controles inferiores:** dos sliders (Ángulo 0–90, Potencia 1–100) con campo numérico editable a la derecha de cada uno. El original se jugaba escribiendo números; conservar esa vía es lo que permite ajustar de 1 en 1.
- Botón **Repetir anterior** que precarga los valores del último disparo del jugador. Es el atajo que más usa la gente en este juego.
- Botón grande **¡Lanzar!**.
- En modo remoto, bloquear controles y mostrar "Esperando a {nick}…" con el estado del enlace.

Navegación: `Menú → [Un jugador | Dos en local | Bluetooth] → Ajustes de partida → Juego → Resultado`.

---

## 15. Tests obligatorios (`core/src/test`)

1. `Rng` reproduce la misma secuencia para la misma semilla (valores fijados a mano en el test).
2. `generate(seed, w)` es idéntico en 100 ejecuciones — comparar hash del `mask`.
3. Ningún tejado por encima de `SKY_BAND`; gorilas siempre en edificios distintos.
4. Sin viento y sin obstáculos, el alcance a 45° coincide con `v²/g` con tolerancia del 2 %.
5. Viento +10 y viento −10 producen alcances simétricos respecto al de viento 0.
6. `blast()` borra exactamente los píxeles dentro del radio y ninguno fuera.
7. Un disparo que impacta en el gorila enemigo devuelve `HitGorilla(oponente)`.
8. Un disparo a 90° con potencia baja vuelve y golpea al propio tirador.
9. `simulate` nunca supera `MAX_STEPS` ni escribe fuera de `path`.
10. Serializar y deserializar cada `Msg` devuelve el objeto original.
11. `MatchEngine` con dos `ShotSource` deterministas produce la misma secuencia de eventos en dos ejecuciones.
12. Partida completa por `LoopbackTransport`: ambos lados terminan con el mismo marcador.

---

## 16. Hitos y criterios de aceptación

| Hito | Contenido | Criterio de aceptación |
|---|---|---|
| **M0** | Esqueleto Gradle, 3 módulos, CI que ejecuta tests | `./gradlew test` verde |
| **M1** | `Rng`, `Trig`, `ScenarioGen`, `Physics` + tests 1–9 | Tests verdes. Calibración del §2 confirmada |
| **M2** | Render con formas geométricas, hot-seat local completo | Partida de 3 rondas jugable con rectángulos de colores |
| **M3** | `AiOpponent` + modo un jugador | La IA en DIFÍCIL acierta en ≤3 turnos en ≥80 % de escenarios generados |
| **M4** | Integración de sprites reales, animaciones, sol, estela | Ningún desajuste visual: gorilas apoyados en tejado, explosión centrada en el cráter |
| **M5** | `Transport` + `LoopbackTransport` + protocolo + tests 10–12 | Partida completa en bucle local sin divergencias |
| **M6** | `NearbyTransport`, flujo de permisos, emparejamiento | Partida completa entre dos dispositivos físicos, incluida la reconexión tras 10 s sin enlace |
| **M7** | `RfcommTransport`, sonido, ajustes, pulido | Funciona en un dispositivo sin Google Play Services |

M4 depende de la entrega de Design. M2 y M3 deben poder completarse sin ella.

---

## 17. Riesgos conocidos

1. **Divergencia de coma flotante entre dispositivos.** Mitigado con `StrictMath` y tablas precalculadas. El `RESULT` del protocolo es la red de seguridad. Si el contador de divergencias sube, es un bug, no ruido.
2. **Nearby Connections requiere Google Play Services.** De ahí `RfcommTransport`. No lo dejes fuera del alcance por ser "el modo secundario": es el que hace que la app funcione en dispositivos sin GMS.
3. **Permisos de Bluetooth en Android 12+.** La denegación es frecuente. Necesitas estado de error legible, no un `catch` silencioso.
4. **Gorila más ancho que su edificio.** Cubierto por el paso 8 de `ScenarioGen`, pero es el fallo clásico de este juego. Test dedicado.
5. **Fugas de memoria por `ImageBitmap`.** Regenerar el bitmap del terreno por frame en lugar de por cráter hunde el rendimiento en gama baja. Verificar con Profiler en M4.

---

## 18. Legal

No portar código de `GORILLA.BAS` ni reutilizar sus recursos: son propiedad de Microsoft. Las mecánicas no son protegibles; el código y los assets sí. Usar un título propio en la publicación.
