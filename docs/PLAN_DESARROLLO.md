# Throwing Bambu — Plan de desarrollo

Derivado de [`ESPEC_DESARROLLO.md`](ESPEC_DESARROLLO.md). La especificación es el **contrato**; este documento es el **plan de ejecución**: qué se construye, en qué orden, con qué criterio se da por terminado y qué hay que decidir antes de empezar.

- **Versión del plan:** 1.2
- **Base:** especificación §0–§18, con D-01…D-06 ya incorporadas
- **Estimación total:** ~23 jornadas de desarrollo (1 persona, sin contar la entrega de Design ni pruebas de campo en M6)
- **Hitos:** M0 → M7, secuenciales salvo lo indicado en §7 (paralelización)

---

## 1. Cómo leer este plan

| Elemento | Significado |
|---|---|
| `T-xx` | Tarea atómica. Unidad de commit y de revisión. |
| `D-xx` | Decisión previa. Debe resolverse **antes** del hito que la consume. |
| DoD | *Definition of Done*: condición verificable, no opinión. |
| Est. | Estimación en jornadas (j) de 6 h efectivas. |

Regla operativa: **ninguna tarea se cierra sin su test o su verificación manual descrita**. Si una tarea no es verificable, está mal definida y se parte.

---

## 2. Decisiones previas — huecos y contradicciones de la especificación

La especificación es sólida, pero contenía **once puntos que no se podían implementar tal cual**. Cada uno lleva propuesta concreta.

**Estado:** ✅ **D-01 a D-06 y D-12 ya están aplicadas** a `ESPEC_DESARROLLO.md`; el texto se conserva aquí como registro de la decisión y su motivo. 🟡 D-07 a D-11 siguen pendientes y se consumen en M1 (D-07, D-08, D-09) y M5 (D-10, D-11).

### ✅ D-01 — El pool de `path` es incompatible con `MatchEvent` — *aplicada en la especificación (§6, §8, §9, §11)*

§6 dice que `ShotResult.path` se reutiliza vía pool; §11 lo publica en un `Flow` y §13 lo anima durante ~2,6 s. En modo IA, `chooseShot` ejecuta 272–353 simulaciones **mientras la UI aún está leyendo el path del turno anterior**: el buffer se machaca a mitad de animación.

**Propuesta:** dos caminos separados.
- Ruta de partida: `simulate` devuelve un `FloatArray(steps * 2)` **propio y recortado**. Coste: ~2,4 KB por turno, una asignación cada varios segundos. Irrelevante.
- Ruta de IA: `simulateInto(scratch: FloatArray, ...)` sobre un buffer reutilizable, confinado al `Dispatchers.Default` de la IA.

La optimización que la especificación pide es real, pero pertenece a la IA, no al motor.

### ✅ D-02 — `Outcome.HitSun` es inalcanzable — *aplicada (§6, §8)*

§6 declara `HitSun` como resultado; §8 dice explícitamente que el sol **no** detiene el vuelo. Ambas cosas no pueden ser ciertas.

**Propuesta:** eliminar `HitSun` de `Outcome` y añadir `val sunHit: Boolean` a `ShotResult`. El render usa el flag para la expresión del sol. El contrato del §6 se corrige.

### ✅ D-03 — Anti-tunelado: el cálculo del §8 solo cubre la velocidad inicial — *aplicada (§8)*

«220 px/s → 1,83 px por paso» solo describe el instante del lanzamiento: `vy` crece durante la caída y el viento sigue acelerando `vx`.

**Corrección hecha en M1.** La primera redacción de esta decisión estimaba un techo de ~10 px/paso extrapolando 15 s de caída libre. Es falso: el proyectil sale del lienzo mucho antes de acumular esa velocidad. El barrido completo de ángulos, potencias, vientos y alturas de tejado da **2,50 px** de avance máximo por paso (2,05 horizontal, 2,32 vertical).

La conclusión no cambia, pero el motivo sí: no se tunela una ventana de 3 px — se tunela un istmo de 1–2 px de los que deja el terreno tras varios cráteres, o se cruza una esquina en diagonal. El techo queda fijado por el test `stepAdvanceStaysBelowTheMeasuredCeiling`, que falla si alguien lo sube sin revisar esta decisión.

**Propuesta:** recorrer el segmento `(x₀,y₀) → (x₁,y₁)` con DDA entero y comprobar `solid()` en cada píxel del trayecto. Coste: ≤10 comprobaciones por paso en el peor caso, cero en el habitual. El `impactX/impactY` pasa a ser el primer píxel sólido del segmento, no el extremo.

### ✅ D-04 — `logicalWidth` puede devolver un lienzo más ancho que la pantalla — *aplicada (§3, §13)*

`logicalWidth` fuerza el mínimo a `W_MIN = 320` tras dividir por una escala derivada **solo de la altura**. En un 1080×2400 en vertical: `scale = 2400/200 = 12`, `1080/12 = 90` → clamp a 320 → se dibujan `320 × 12 = 3840 px` sobre una pantalla de 1080. Recorte del 72 % del campo de juego.

**Propuesta:** (a) bloquear la actividad de juego en **landscape** (`android:screenOrientation="sensorLandscape"`), y (b) hacer la escala dependiente de ambas dimensiones:

```kotlin
fun logicalWidth(screenW: Int, screenH: Int): Int {
    var scale = maxOf(1, screenH / G.H)
    while (scale > 1 && screenW / scale < G.W_MIN) scale--
    return (screenW / scale).coerceIn(G.W_MIN, G.W_MAX)
}
```

La escala calculada por el render debe ser **la misma función**, no un cálculo paralelo dentro del `Canvas`. Extraer a `core` y usarla en los dos sitios.

### ✅ D-05 — La calibración del §2 no cuadra con su propia física — *aplicada (§2)*

Con `POWER_TO_SPEED = 2.2`, potencia 68 → `v = 149,6 px/s`. Alcance a 45°: `v²/g = 149,6² / 80 = 279,8 px`. Tiempo: `2·v·sin45°/g = 2,65 s`.

El tiempo cuadra con el documento (~2,6 s); **el alcance no**: 280 px, no ~300 px (−6,7 %). El test 15.4 exige tolerancia del 2 %, así que con el texto actual el hito M1 nace fallando.

**Propuesta:** mantener `GRAVITY = 80` (el tiempo de vuelo es lo que se percibe) y corregir el §2 a **«~280 px y ~2,65 s»**. La alternativa, `GRAVITY = 74,6`, acelera todo el juego un 7 % para cuadrar una cifra redonda escrita a ojo.

### ✅ D-06 — `data class` con arrays rompe `equals`/`hashCode` — *aplicada (§6, §11)*

`Building(windows: BooleanArray)`, `ShotResult(path)` y `RoundEnd(scores: IntArray)` comparan arrays por identidad. Los tests 15.2 y 15.11 comparan escenarios y secuencias de eventos: pasarían o fallarían por motivos equivocados.

**Propuesta:** quitar `data` donde hay arrays y exponer `contentEquals`/`contentHashCode` explícitos, o un `fun fingerprint(): Long` en `Terrain` y `Scenario`. Los tests comparan huellas, no objetos.

### 🟡 D-07 — Falta la geometría exacta de las ventanas

§7.6 dice «rejilla de 3×4 px con margen 3 px y separación 3 px», pero no fija el número de filas y columnas. Cualquier interpretación distinta entre dos versiones rompe el determinismo porque **cambia el número de llamadas al RNG**.

**Propuesta (contrato):**

```
cols = max(0, (width  - 2*MARGIN + GAP) / (WIN_W + GAP))   // WIN_W=3, GAP=3, MARGIN=3
rows = max(0, (height - 2*MARGIN + GAP) / (WIN_H + GAP))   // WIN_H=4
windows = BooleanArray(rows * cols) { rng.nextFloat() < 0.5f }   // fila-mayor, de arriba abajo
```

Se consumen exactamente `rows*cols` valores del RNG, siempre, aunque la ventana quede fuera por cualquier motivo.

### 🟡 D-08 — `nFachadas` y la paleta no existen en `G`

§7.5 usa `rng.nextInt(nFachadas)` sin definirlo, y `Terrain.color` guarda índices de una paleta que nadie declara.

**Propuesta:** `core/Palette.kt` con `FACADES: IntArray` (6 fachadas ARGB), `WINDOW_ON`, `WINDOW_OFF`, `SKY`, `SUN`, `GORILLA` y `const val N_FACADES = 6`. En `core` son enteros ARGB, sin dependencia de Android.

### 🟡 D-09 — `nextGaussian` puede devolver infinito

Box-Muller con `ln(u1)` revienta si `nextFloat()` devuelve exactamente 0,0 — y con 24 bits de mantisa ocurre 1 de cada 16,7 M. Con la IA llamando dos veces por turno, es un crash cada ~8 M turnos: raro, pero no imposible y muy difícil de diagnosticar.

**Propuesta:** rechazar `u1 == 0f` y volver a muestrear (bucle `do/while`), nunca sumar un epsilon.

### 🟡 D-10 — Protocolo: `HELLO` no declara `width`, y no hay reanudación

§12 exige negociar `width` en `HELLO` («añadir `u16 width`»), pero la tabla no lo refleja. Y el criterio de M6 pide «reconexión tras 10 s sin enlace» sin que exista ningún mensaje capaz de recuperar el estado.

**Propuesta:** fijar la tabla y añadir dos tipos. El determinismo hace la reanudación trivial: basta con el historial de disparos.

| Tipo | Nombre | Cuerpo |
|---|---|---|
| `0x01` | `HELLO` | `u8 nickLen`, `nick UTF-8`, `u16 width`, `u8 caps` |
| `0x08` | `RESUME` | `i64 seed`, `u16 nextTurn` |
| `0x09` | `HISTORY` | `u16 n`, n × (`u16 turn`, `u8 angle`, `u8 power`) |

### 🟡 D-11 — `RESULT`: quién lo envía y qué se adopta

§12 dice que el receptor «adopta el valor del emisor» si difiere en más de 2 px, pero no fija emisor ni si se adopta también el `outcome`.

**Propuesta:** lo envía **siempre el tirador**, inmediatamente después de su `SHOT`. El receptor adopta `outcome`, `impactX` e `impactY` **antes** de aplicar el cráter — el `outcome` decide la puntuación, así que adoptar solo las coordenadas deja marcadores divergentes. Contador `divergences` expuesto en la pantalla de depuración; >1 por partida es un bug de determinismo abierto, no un aviso.

### ✅ D-12 — La lista de permisos del §12 está incompleta — *aplicada (§12)*

Declarar `ACCESS_FINE_LOCATION` sin `ACCESS_COARSE_LOCATION` es un **error** de lint (`CoarseFineLocation`) y aborta el build de la app. Lo detectó el primer CI de M0.

No es solo un formalismo: desde Android 12 el usuario puede conceder únicamente COARSE, así que el flujo de permisos de M6 (T-42) debe tratar «solo COARSE» como estado válido y comprobar si Nearby funciona con esa concesión, en lugar de darlo por denegado.

### Otros puntos menores asumidos sin decisión

- §3 afirma que caben «6–9» edificios. Con `BUILD_W` ∈ [24, 40] y `width` ∈ [320, 460], el rango real es **8–19** (mínimo `320/40`, máximo `460/24`). No afecta al código, pero el número aparece en el documento de contrato y conviene corregirlo.
- §7.3: al absorber el sobrante, el último edificio **puede** superar `BUILD_W_MAX`. Se acepta y el test de invariantes lo contempla.
- §7.8: con `W_MIN = 320` y `BUILD_W_MAX = 40` siempre hay ≥8 edificios, luego los índices 1 y n−2 nunca coinciden. Aun así, `generate` exige `n >= 4` con `require`, para que el fallo sea explícito si alguien toca las constantes.
- `Shot.turn` es un **contador global monótono de la partida** (0-based), no por ronda: lo consumen `windForTurn` y el campo `u16 turn` del protocolo. Se documenta en el KDoc de `Shot`.
- §15.8: «potencia baja» a 90° debe ser ≥ 10 para que el proyectil salga de los 5 pasos de gracia antes de volver. El test fija potencia 20.

---

## 3. Arquitectura y estructura del repositorio

```
throwing-bambu/
├── gradle/libs.versions.toml        catálogo de versiones (única fuente)
├── settings.gradle.kts
├── build-logic/                     convention plugins (kotlin-jvm, android-lib, android-app)
├── core/                            Kotlin JVM puro — sin Android
│   ├── src/main/kotlin/…/core/      GameConstants, Rng, Trig, Palette, Model, ScenarioGen,
│   │                                Physics, Ai, ShotSource, MatchEngine, Layout
│   └── src/test/kotlin/             tests 1–9 y 11
├── transport/                       Android library
│   ├── Transport, Msg, Codec, LinkState, Peer
│   ├── LoopbackTransport (main, no test: lo usa la app en modo depuración)
│   ├── NearbyTransport, RfcommTransport
│   └── src/test/                    tests 10 y 12 (JVM puro, sin instrumentación)
└── app/                             Android app — Compose
    ├── ui/menu, ui/setup, ui/game, ui/result
    ├── render/  (TerrainBitmap, GameCanvas, sprites)
    ├── audio/, settings/ (DataStore)
    └── di/ (manual, sin Hilt)
```

**Reglas estructurales verificadas en CI**, no por buena voluntad:

1. `core` es `kotlin("jvm")`. Un `import android.` no compila: no hay SDK en el classpath.
2. Test de arquitectura en `core`: ninguna clase de `core` referencia `android.*` ni `kotlinx.coroutines.android`.
3. `transport` no conoce Compose. `app` no implementa protocolo.
4. Sin inyección de dependencias por librería: el grafo es pequeño y una `AppContainer` manual basta. Hilt aquí es coste sin retorno.

---

## 4. Convenciones de trabajo

- **Rama de desarrollo:** `claude/plan-desarrollo-pw6mrm`. Commits atómicos por tarea, mensaje `M<hito>/T-xx: descripción`.
- **CI (GitHub Actions):** `./gradlew build test` en cada push. M0 lo deja verde y **no se vuelve a romper**: una rama con CI roja no recibe tareas nuevas.
- **Calidad:** `ktlint` + `detekt` en el pipeline desde M0. Añadirlos en M7 significa 400 avisos de golpe.
- **Cobertura:** no se fija un porcentaje. Se fija la lista de 12 tests obligatorios del §15 más los que añade este plan; esa lista es el contrato de calidad.
- **Cualquier cambio de constante en `G`** exige actualizar `ESPEC_DESARROLLO.md` en el **mismo commit**. Es la regla del §0 y se revisa.

---

## 5. Desglose por hitos

### M0 — Esqueleto ✅ *completado*

**Objetivo:** `./gradlew test` verde y CI ejecutándose.

| ID | Tarea | Est. | Estado |
|---|---|---|---|
| T-01 | `settings.gradle.kts`, `libs.versions.toml`, wrapper Gradle 8.14.3 | 0,2 j | ✅ |
| T-02 | Módulos `core` (JVM), `transport` (android-lib), `app` (android-app) con `minSdk 24 / target 35 / compile 35`; grafo de dependencias del §1 | 0,15 j | ✅ |
| T-03 | Workflow `ci.yml`: JDK 17, cache de Gradle, `build test`; ktlint + detekt; APK de depuración publicado como artefacto | 0,15 j | ✅ |
| T-04 | `README.md`, `LICENSE` propia y nota legal del §18 (nombre propio, sin assets de Microsoft) | 0,05 j | ✅ |

**Dos desviaciones respecto al plan original, ambas deliberadas:**

- **Sin `build-logic`.** Con tres módulos, un included build de convention plugins cuesta más configuración y tiempo de compilación del que ahorra: la configuración compartida entre `transport` y `app` son ~15 líneas. Se reconsidera si el proyecto pasa de cinco módulos.
- **Añadido `-PcoreOnly`.** Excluye los módulos Android del build. No es un apaño: hace efectiva la promesa del §0.1 —`:core` es testeable sin Android— y permite trabajar el núcleo en una máquina o un contenedor sin SDK.

**Añadidos no previstos:**

- Cada ejecución publica el **APK de depuración** como artefacto (`apk-debug`, 14 días), generado después de los tests: si el build está en rojo, no hay APK. El de release espera a M7, donde entran keystore y R8 (T-52).
- El workflow incluye un segundo job que ejecuta `:core:test` en **macOS**. El §17.1 avisa de divergencias de coma flotante entre implementaciones de JVM; desde M1 esto las detecta en el commit que las introduce, no en M6 como bug de red.

**DoD:** CI verde en la rama. `app` instala y arranca en una pantalla vacía.

**Verificación realizada** (el contenedor de desarrollo no alcanza `dl.google.com` ni `maven.google.com`, bloqueados por la política de red, así que no puede resolver AGP ni AndroidX):

| Elemento | Cómo se verificó |
|---|---|
| Tests de `:core` | ✅ Ejecutados (build equivalente Kotlin JVM + JUnit): 2/2 en verde |
| ktlint sobre `.kt` y `.kts` | ✅ Limpio (ktlint-cli 1.5.0 sobre todo el repositorio) |
| detekt | ✅ Limpio, con la configuración de `config/detekt/detekt.yml` |
| Build de `:transport` y `:app` | ✅ Verificado en CI, tras corregir el error de lint que destapó (D-12) |
| `app` arranca en pantalla vacía | ⏳ Pendiente de dispositivo o emulador |

**Decisiones consumidas:** D-04 (orientación landscape en el manifiesto) y D-12 (permiso COARSE), esta última descubierta por el propio CI.

---

### M1 — Núcleo determinista ✅ *completado* — *el hito que decide el proyecto*

**Objetivo:** física y generación de escenario correctas, deterministas y con los tests 1–9 en verde.

| ID | Tarea | Est. | Depende |
|---|---|---|---|
| ✅ T-05 | `GameConstants.kt` (§2) + `Palette.kt` (D-08) | 0,15 j | — |
| ✅ T-06 | `Rng.kt` xorshift64\* + `nextGaussian` robusto (D-09) + `windForTurn` | 0,3 j | T-05 |
| ✅ T-07 | `Trig.kt`: tablas `SIN`/`COS` con `StrictMath`; prohibición de `sin`/`cos` verificada por test de arquitectura | 0,2 j | — |
| ✅ T-08 | `Model.kt`: `Building`, `Terrain` (`solid`, `blast`, `fingerprint`), `Gorilla`, `Scenario`, `Shot`, `Outcome` (sin `HitSun`, D-02), `ShotResult` (con `sunHit`) | 0,4 j | T-05 |
| ✅ T-09 | `Layout.kt`: `logicalWidth` corregido (D-04), compartido por `core` y render | 0,1 j | T-05 |
| ✅ T-10 | `ScenarioGen.generate` (§7, pasos 1–9 en orden exacto) con la rejilla de ventanas de D-07 | 0,8 j | T-06, T-08 |
| ✅ T-11 | `Physics.simulate` con DDA anti-tunelado (D-03), gracia de 5 pasos para el tirador, sol no bloqueante | 0,8 j | T-07, T-08 |
| ✅ T-12 | Tests 15.1–15.9 + test de arquitectura (sin `android.*`, sin `Math.sin` en `core`) | 0,8 j | T-10, T-11 |
| ✅ T-13 | Calibración: medir alcance y tiempo a 45°/68 y **corregir el §2** según D-05 | 0,15 j | T-12 |

**DoD:** ✅ los nueve tests del §15 en verde (34 tests en total con los añadidos), huella de `mask` estable en 100 ejecuciones y en dos JVM, calibración documentada con la cifra medida.

**Cifras medidas en M1** (sustituyen a toda estimación previa):

| Magnitud | Analítico | Medido | Desvío |
|---|---|---|---|
| Alcance 45° / potencia 68 | 279,8 px | **281,2 px** | +0,52 % |
| Tiempo de vuelo | 2,645 s | **2,658 s** (386 pasos) | +0,49 % |
| Desplazamiento por viento ±10 | — | **±52,8 px**, simétrico a ±0,01 px | — |
| Avance máximo por paso | 1,83 px *(estimación del §8)* | **2,50 px** (H 2,05 / V 2,32) | +37 % |

El desvío del alcance y el tiempo es el error del integrador de Euler explícito, dentro del 2 % que exige el test §15.4.

**La última fila obligó a corregir D-03**: la estimación de ~10 px/paso que yo mismo había escrito era falsa — el proyectil sale del lienzo antes de acumular esa velocidad. El DDA sigue siendo necesario, pero por los istmos de 1–2 px que deja el terreno tras los cráteres, no por velocidades altas. El techo está fijado por un test.

**Decisiones consumidas:** D-01, D-02, D-03 (corregida), D-05, D-06, D-07, D-08, D-09.

---

### M2 — Render geométrico y hot-seat (3 j)

**Objetivo:** partida local de 3 rondas jugable con rectángulos de colores. **Sin dependencia de Design.**

| ID | Tarea | Est. | Depende |
|---|---|---|---|
| T-14 | `ShotSource` + `HumanShotSource` (Channel) + `AiShotSource` (stub) | 0,2 j | M1 |
| T-15 | `MatchEngine`: bucle, `MatchEvent`, cráter, marcador, rondas, `roundsToWin` | 0,7 j | T-14 |
| T-16 | `TerrainBitmap`: `IntArray` ARGB desde `mask`+`color`+paleta → `ImageBitmap`; regeneración **solo** por cráter, parcheando el rect afectado | 0,5 j | M1 |
| T-17 | `GameCanvas`: cielo, terreno, sol, gorilas, plátano, estela de 12 puntos; `FilterQuality.None` en todo `drawImage`; barras laterales del color del cielo | 0,6 j | T-16 |
| T-18 | Animación del disparo: 2 puntos de `path` por frame a 60 fps, `speedMultiplier` 1×/2× | 0,3 j | T-17 |
| T-19 | HUD (marcador, turno, flecha de viento proporcional + valor) y controles (2 sliders + campo numérico editable, **Repetir anterior**, botón ¡Lanzar!) | 0,6 j | T-15 |
| T-20 | Navegación `Menú → Modo → Ajustes → Juego → Resultado` (Navigation Compose) + bloqueo landscape (D-04) | 0,4 j | T-19 |
| T-21 | `MatchEngine` sobrevive a rotación y a background: el motor vive en un `ViewModel` con `viewModelScope`, la UI solo consume `events` | 0,3 j | T-15 |

**DoD:** partida completa de 3 rondas con dos humanos en el mismo dispositivo, viento visible cambiando cada turno, cráteres persistentes dentro de la ronda, marcador correcto y pantalla de resultado. Test 15.11 (`MatchEngine` determinista con dos fuentes deterministas) en verde.

**Nota de riesgo del §17.5:** T-16 es el punto exacto donde se hunde el rendimiento si alguien regenera el bitmap por frame. Se verifica en M4 con el Profiler, pero la decisión se toma aquí.

---

### M3 — IA y modo un jugador (2 j)

| ID | Tarea | Est. | Depende |
|---|---|---|---|
| T-22 | `AiOpponent`: búsqueda gruesa 16×17 = 272, puntuación por distancia euclídea al centro del gorila, penalización máxima para `OffScreen`/`TimeOut`/autogol | 0,5 j | M1 |
| T-23 | Refinado ±4/±4 paso 1 y ruido gaussiano por nivel (`FACIL`/`MEDIO`/`DIFICIL`) | 0,3 j | T-22 |
| T-24 | `simulateInto` sobre arena reutilizable para la IA (D-01) + ejecución en `Dispatchers.Default` + retardo artificial 600–1200 ms | 0,3 j | T-22 |
| T-25 | Integración del modo un jugador: selección de nivel en Ajustes de partida | 0,2 j | M2, T-23 |
| T-26 | **Arnés de evaluación de la IA**: 200 escenarios generados, mide turnos hasta acierto por nivel; se ejecuta como test largo con etiqueta `@Tag("slow")`, fuera del CI por defecto | 0,5 j | T-23 |
| T-27 | Ajuste de sigmas si T-26 no alcanza el criterio | 0,2 j | T-26 |

**DoD:** el arnés T-26 reporta **DIFÍCIL acertando en ≤3 turnos en ≥80 % de los escenarios**. El número se publica en el commit; sin arnés no hay criterio de aceptación, solo impresión.

**Decisión de alcance confirmada:** no se implementa solución analítica de balística (§9). La búsqueda cuesta milisegundos y tolera viento y edificios interpuestos.

---

### M4 — Sprites y acabado visual (3 j) — *depende de la entrega de Design*

| ID | Tarea | Est. |
|---|---|---|
| T-28 | Integración de sprites: gorilas (reposo, lanzamiento, victoria), plátano en rotación, sol con dos expresiones | 0,8 j |
| T-29 | Explosión `boom.png`: 8 fotogramas × 60 ms; cráter aplicado en el **fotograma 3** | 0,4 j |
| T-30 | Fachadas y ventanas definitivas según paleta de Design; ajuste de `N_FACADES` si Design entrega otro número | 0,5 j |
| T-31 | Reacción del sol al `sunHit` (D-02) y animación de derrota del gorila | 0,4 j |
| T-32 | Verificación con Android Studio Profiler en dispositivo de gama baja (§17.5): sin crecimiento de memoria por turno, 60 fps estables | 0,5 j |
| T-33 | Repaso de alineación: pies del gorila exactamente en `roofY`, explosión centrada en el cráter, ningún escalado fraccionario | 0,4 j |

**DoD:** el criterio del §16 — ningún desajuste visual — verificado con capturas comparadas a escala 1× y a la escala máxima del dispositivo. Profiler limpio.

**Gestión de dependencia:** si Design no ha entregado al terminar M3, se salta a M5 y M4 se recupera en paralelo a M6. M4 nunca bloquea al resto.

---

### M5 — Transporte, protocolo y loopback (3 j)

| ID | Tarea | Est. | Depende |
|---|---|---|---|
| T-34 | `Msg` (sellado) + `Codec` big-endian, versión `0x01`, tipos `0x01`–`0x09` con las correcciones de D-10 | 0,6 j | — |
| T-35 | `Transport`, `LinkState`, `Peer`; `LoopbackTransport` con dos `Channel` cruzados | 0,4 j | T-34 |
| T-36 | `RemoteShotSource`: suspende hasta `Msg.Shot` con el `turn` esperado; **descarta** turnos distintos (§12) | 0,4 j | T-35 |
| T-37 | Sesión de partida remota: anfitrión como autoridad (seed, `width` = mín. de los dos lienzos, jugador inicial), `MATCH_START`, `REMATCH`, `BYE` | 0,6 j | T-36 |
| T-38 | `RESULT` como verificación con adopción y contador de divergencias (D-11); pantalla de depuración que lo muestra | 0,4 j | T-37 |
| T-39 | Keep-alive: `PING` cada 10 s en reposo, 3 fallos → `LOST`; timeout de turno 90 s → `BYE(TIMEOUT)` con cuenta atrás visible desde 75 s | 0,4 j | T-37 |
| T-40 | Tests 15.10 (round-trip de cada `Msg`) y 15.12 (partida completa por loopback, mismo marcador en ambos lados) | 0,5 j | T-38 |

**DoD:** partida de 3 rondas completa por `LoopbackTransport` con `divergences == 0` y marcadores idénticos en los dos motores. Round-trip de los 9 tipos de mensaje, incluidos los límites (`nick` de 0 y 255 bytes, `turn` = 65535, `angle` = 0 y 90).

---

### M6 — Nearby Connections y emparejamiento real (4 j)

| ID | Tarea | Est. |
|---|---|---|
| T-41 | `NearbyTransport` con `P2P_POINT_TO_POINT`: advertise, discover, connect, payloads `BYTES`, mapeo de callbacks a `Flow`/`StateFlow` | 1,2 j |
| T-42 | Flujo de permisos: pantalla explicativa **previa** al diálogo del sistema, solicitud solo al entrar en modo Bluetooth, estados de error legibles por permiso denegado / Bluetooth apagado / ubicación desactivada en ≤ API 30 (§17.3) | 0,8 j |
| T-43 | UI de emparejamiento: anfitrión/invitado, lista de peers con nick, estado del enlace, cancelación | 0,7 j |
| T-44 | Reconexión: `RESUME` + `HISTORY` (D-10), reconstrucción determinista del estado y reanudación en el turno correcto | 0,8 j |
| T-45 | Pruebas en dos dispositivos físicos, incluido el corte de enlace de 10 s del criterio de aceptación | 0,5 j |

**DoD:** partida completa entre dos dispositivos físicos, con una desconexión de 10 s en medio y reanudación correcta, `divergences == 0`. Denegar cada permiso produce un mensaje concreto, nunca un `catch` silencioso.

---

### M7 — RFCOMM, audio, ajustes y pulido (4 j)

| ID | Tarea | Est. |
|---|---|---|
| T-46 | `RfcommTransport`: `BluetoothSocket` + UUID SPP fijo, servidor/cliente, framing por longitud sobre el flujo de bytes | 1,2 j |
| T-47 | Selección de transporte: Nearby si hay GMS, RFCOMM si no; conmutación manual en ajustes (§17.2 — no es un modo secundario) | 0,4 j |
| T-48 | Audio: lanzamiento, explosión, victoria, sonido de derrota; `SoundPool`, respeto del modo silencio | 0,6 j |
| T-49 | Ajustes persistentes (DataStore): `speedMultiplier`, sonido, nivel de IA por defecto, rondas para ganar | 0,4 j |
| T-50 | Localización es/en; ninguna cadena embebida en el código | 0,4 j |
| T-51 | Pulido: transiciones, estados vacíos, accesibilidad de los controles numéricos, `contentDescription` | 0,5 j |
| T-52 | Build de release: R8, `proguard-rules`, verificación de que `core` no se ofusca de forma que rompa los tests de determinismo | 0,3 j |
| T-53 | Prueba en dispositivo **sin Google Play Services** (emulador AOSP) | 0,2 j |

**DoD:** partida completa por RFCOMM en un dispositivo sin GMS. APK de release instalable y jugable en los tres modos.

---

## 6. Trazabilidad de tests

| # (§15) | Descripción | Hito | Tarea |
|---|---|---|---|
| 1 | `Rng` reproduce la secuencia (valores fijados a mano) | M1 | ✅ T-12 |
| 2 | `generate` idéntico en 100 ejecuciones (huella de `mask`) | M1 | ✅ T-12 |
| 3 | Ningún tejado sobre `SKY_BAND`; gorilas en edificios distintos | M1 | ✅ T-12 |
| ✅ 4 | Alcance a 45° ≈ `v²/g` ±2 % | M1 | T-12, T-13 |
| 5 | Viento ±10 produce alcances simétricos | M1 | ✅ T-12 |
| 6 | `blast()` borra exactamente el círculo | M1 | ✅ T-12 |
| 7 | Impacto en el enemigo → `HitGorilla(oponente)` | M1 | ✅ T-12 |
| 8 | 90° con potencia baja → autogol | M1 | ✅ T-12 |
| 9 | `simulate` nunca excede `MAX_STEPS` ni escribe fuera de `path` | M1 | ✅ T-12 |
| 10 | Round-trip de cada `Msg` | M5 | T-40 |
| 11 | `MatchEngine` determinista con fuentes deterministas | M2 | T-15 |
| 12 | Partida completa por loopback, mismo marcador | M5 | T-40 |

**Tests añadidos por este plan** (no están en el §15 y cubren riesgos reales):

| # | Descripción | Hito | Motivo |
|---|---|---|---|
| ✅ A1 | `core` no referencia `android.*` ni `Math.sin`/`cos` | M1 | Reglas de oro §0.1 y §5 |
| ✅ A2 | El gorila nunca es más ancho que su edificio (§17.4) | M1 | Fallo clásico, riesgo declarado |
| ✅ A3 | Anti-tunelado: proyectil a 900 px/s contra un muro de 2 px → impacta | M1 | D-03 |
| ✅ A4 | `logicalWidth` × escala ≤ ancho de pantalla en 12 resoluciones reales | M2 | D-04 |
| A5 | El `path` publicado por `MatchEvent` no se altera tras el siguiente turno | M2 | D-01 |
| A6 | `Codec` rechaza versión ≠ `0x01` y cuerpos truncados sin lanzar excepción no controlada | M5 | Robustez de red |
| A7 | `SHOT` con `turn` inesperado se descarta y no bloquea el motor | M5 | §12 |
| A8 | Reanudación: aplicar `HISTORY` reconstruye un estado idéntico al continuo | M6 | D-10 |

---

## 7. Ruta crítica y paralelización

```
M0 ──► M1 ──┬──► M2 ──┬──► M3 ──► (modo 1 jugador listo)
            │         │
            │         └──► M5 ──► M6 ──► M7
            │
            └──► M4 (en cuanto Design entregue; se integra sobre M2)
```

- **Ruta crítica:** M0 → M1 → M2 → M5 → M6 → M7 ≈ 17,5 j.
- **M3 es paralelizable** con M5 si hay dos personas: la IA solo toca `core`, el transporte solo toca `transport`.
- **M4 nunca bloquea**: se integra sobre el render de M2 cuando llega la entrega de Design.
- **Punto de no retorno:** el final de M1. Cualquier cambio en `G`, en el orden del RNG o en la geometría de ventanas después de M5 invalida partidas y exige subir la versión de protocolo.

---

## 8. Riesgos — disparadores y respuesta

| # | Riesgo (§17) | Disparador observable | Respuesta |
|---|---|---|---|
| R1 | Divergencia de coma flotante | `divergences > 0` en cualquier partida | Parar. Reproducir con la misma seed en las dos JVM, comparar `path` paso a paso. No subir el umbral de 2 px. |
| R2 | Nearby exige GMS | Dispositivo sin GMS en pruebas | `RfcommTransport` (M7) es alcance comprometido, no opcional. |
| R3 | Permisos Bluetooth 12+ | Denegación en pruebas de campo | T-42: mensaje concreto por permiso y acceso directo a los ajustes del sistema. |
| R4 | Gorila más ancho que su edificio | Test A2 en rojo | Desplazamiento al edificio adyacente (§7.8) verificado por test, no por inspección. |
| R5 | Fugas por `ImageBitmap` | Memoria creciente en Profiler (T-32) | Parcheo del rect del cráter (T-16); nunca regenerar por frame. |
| R6 | **[nuevo]** Tunelado tras varios cráteres | Proyectil que atraviesa terreno en partida larga | DDA de D-03; test A3. |
| R7 | **[nuevo]** Entrega de Design tardía | M3 terminado sin assets | M4 se desplaza y se ejecuta en paralelo a M6; M2/M3 son jugables con geometría. |
| R8 | **[nuevo]** Deriva entre `ESPEC` y código | Constante cambiada sin actualizar el documento | Revisión de PR: cambio en `G` sin cambio en `ESPEC_DESARROLLO.md` = rechazo. |

---

## 9. Fuera de alcance (backlog explícito)

No entran en M0–M7 y no se empiezan «de paso»:

- Partida por internet (solo enlace local: Nearby / RFCOMM).
- Marcador persistente, perfiles o estadísticas.
- Más de dos jugadores.
- Editor de escenarios.
- Tablet/plegables con layout específico (funcionan, pero sin optimización dedicada).
- Guardado y reanudación de partida entre ejecuciones de la app (la reanudación de M6 es solo intrasesión).

---

## 10. Próximo paso inmediato

1. ~~Resolver D-01 a D-06~~ — hecho: aplicadas a la especificación.
2. ~~Ejecutar **M0** y dejar CI verde~~ — en curso.
3. ~~Confirmar D-07, D-08 y D-09~~ — aplicadas tal y como se propusieron. El orden de llamadas al RNG queda congelado: cambiarlo a partir de aquí invalida todas las semillas.
4. ~~**M1**~~ — completado: 34 tests en verde y las cifras de calibración medidas.
5. Siguiente: **M2** (render geométrico y hot-seat), que ya no depende de ninguna decisión abierta. Quedan pendientes D-10 y D-11, que se consumen en M5.
