# Throwing Bambu — Development specification

A document for a coding agent. Everything here that appears as a constant or a signature is **contract**, not suggestion. If something changes, it changes in this document first.

> **Theme.** The game is *Throwing Bambu*: pandas throwing bamboo canes. The original brief in `docs/SPRITE_SPEC.md` still says gorillas and bananas because it is the record of what was asked for; this document describes what is built.

---

## 0. Golden rules

1. **`:core` imports nothing from Android.** An `import android.*` inside `:core` is a design error.
2. **Physics is a pure function.** `simulate(...)` takes state and returns the full trajectory plus the outcome. It does not draw, does not animate, mutates nothing.
3. **The three game modes share one loop.** Only the source of the `Shot` changes.
4. **Whole pixels, always.** No fractional scaling, no bilinear interpolation.
5. **Determinism.** Same seed + same shots = same match, on any device.

---

## 1. Module layout

```
throwing-bambu/
├── core/          Plain Kotlin JVM. No Android. Testable with JUnit.
├── transport/     Android. Connectivity abstraction + implementations.
└── app/           Android. Compose, rendering, navigation, audio.
```

- `minSdk = 24`, `targetSdk = 35`, `compileSdk = 35`
- Kotlin 2.x, Compose BOM, `kotlinx.coroutines`
- `transport` depends on `core`. `app` depends on both. `core` depends on nothing.
- Version management through `gradle/libs.versions.toml`.

---

## 2. Shared constants (`core/GameConstants.kt`)

```kotlin
object G {
    // Logical canvas
    const val H            = 200      // fixed logical height
    const val W_MIN        = 320
    const val W_MAX        = 460
    const val SKY_BAND     = 60       // top band kept clear of buildings

    // Integration
    const val DT           = 1f / 120f
    const val MAX_FLIGHT_S = 15f
    const val MAX_STEPS    = 1800     // MAX_FLIGHT_S / DT

    // Physics
    const val GRAVITY         = 80f   // px/s²
    const val WIND_ACCEL      = 1.5f  // px/s² per unit of wind (wind ∈ -10..10)
    const val POWER_TO_SPEED  = 2.2f  // power 1..100 -> 2.2..220 px/s

    // Bodies
    const val CANE_R     = 3
    const val CRATER_R   = 12
    const val PANDA_W    = 16
    const val PANDA_H    = 20
    const val HAND_DX    = 10         // horizontal offset of the throwing point
    const val HAND_DY    = 22         // height of the throwing point above the roof

    // Scenario
    const val BUILD_W_MIN = 24
    const val BUILD_W_MAX = 40
    const val BUILD_H_MIN = 40
    const val BUILD_H_MAX = 130
    const val SUN_W       = 20
    const val SUN_H       = 20
}
```

`PANDA_W × PANDA_H` matches the delivered art: the panda's body occupies exactly 16×20 inside its 24×24 cell (`art/README.md`).

**Expected calibration** (verify in M1): a shot at 45° with power 68 should travel **~280 px** and last **~2.65 s**.

The derivation, so nobody estimates it by eye again: `v = 68 × POWER_TO_SPEED = 149.6 px/s`; range `v²·sin(2θ)/g = 149.6²/80 = 279.8 px`; time `2·v·sin45°/g = 2.645 s`.

**Measured in M1** with the integrator of §8: range **281.2 px** (+0.52 % over the analytical value, inside the 2 % the §15.4 test demands) and time **2.658 s** (386 steps). The difference is the error of the Euler integrator, not a bug. If a future measurement drifts away from these figures, the fault is in the implementation, not in `GRAVITY`.

---

## 3. Logical canvas width

```kotlin
// core/Layout.kt — single source of truth. The renderer does NOT recompute the scale.
fun logicalScale(screenW: Int, screenH: Int): Int {
    var scale = maxOf(1, screenH / G.H)
    while (scale > 1 && screenW / scale < G.W_MIN) scale--
    return scale
}

fun logicalWidth(screenW: Int, screenH: Int): Int =
    (screenW / logicalScale(screenW, screenH)).coerceIn(G.W_MIN, G.W_MAX)
```

The scale cannot be derived from the height alone: with a scale taken from `screenH` and a forced minimum of `W_MIN`, the canvas can end up **wider than the screen** (1080×2400 in portrait would give `scale = 12` and `320 × 12 = 3840 px` over the 1080 available). The loop lowers the scale until `W_MIN` really fits.

The logical height is **always 200**. The width varies; that changes how many buildings fit (between 8 and 19, depending on the widths drawn), not the scale of anything. Horizontal leftovers → sky-coloured side bars, never stretching.

The game screen is locked to **landscape** (`android:screenOrientation="sensorLandscape"`). In portrait the canvas does fit, but the resulting scale wastes half the screen and the controls do not fit below it.

---

## 4. Deterministic RNG (`core/Rng.kt`)

Do not use `java.util.Random` or `kotlin.random.Random`. Implement xorshift64\*:

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
    fun nextGaussian(): Float  // Box-Muller over nextFloat()
}
```

`nextGaussian` resamples `u1` while it is zero: `ln(0)` is infinite and the AI's shot would come out with a NaN angle (D-09).

### Wind is derived, not transmitted

```kotlin
fun windForTurn(seed: Long, turn: Int): Int =
    Rng(seed * 0x100000001B3L + turn).nextIntRange(-10, 10)
```

A design consequence: wind **never travels over the network**. Both devices compute it. This removes an entire class of synchronisation bugs.

`turn` is the match-wide counter, 0-based, the same one the protocol carries in its `u16 turn` field.

---

## 5. Deterministic trigonometry (`core/Trig.kt`)

`Math.sin` does not guarantee bit-identical results across JVM implementations. Since the angle is an integer number of degrees:

```kotlin
internal val SIN = FloatArray(91) { StrictMath.sin(it * StrictMath.PI / 180.0).toFloat() }
internal val COS = FloatArray(91) { StrictMath.cos(it * StrictMath.PI / 180.0).toFloat() }
```

Calling `sin()`/`cos()` on the simulation path is forbidden.

---

## 6. Data model (`core/Model.kt`)

```kotlin
// Not a `data class`: a data class holding an array compares by identity, not by
// content, and tests §15.2 and §15.11 would pass or fail for the wrong reason.
class Building(
    val x: Int, val width: Int, val height: Int,
    val paletteIdx: Int,          // index into the facade palette
    val windows: BooleanArray,    // lit/unlit, row-major
    val windowCols: Int,
) {
    override fun equals(other: Any?): Boolean = /* … contentEquals over windows */ false
    override fun hashCode(): Int = /* … includes windows.contentHashCode() */ 0
}

class Terrain(
    val width: Int,
    val mask: BooleanArray,       // width * G.H, true = solid
    val color: ByteArray,         // width * G.H, EGA index per pixel
    val buildings: List<Building>
) {
    fun solid(x: Int, y: Int): Boolean
    fun blast(cx: Int, cy: Int, r: Int)   // clears a circle
    fun firstSolidOnSegment(x0: Float, y0: Float, x1: Float, y1: Float, out: IntArray): Boolean
    fun fingerprint(): Long               // hash of mask+color; this is what tests compare
}

data class Panda(val player: Int, val x: Int, val roofY: Int, var alive: Boolean = true)
// x = horizontal centre; roofY = the roof pixel the feet rest on

data class Scenario(val width: Int, val terrain: Terrain, val pandas: List<Panda>, val sunX: Int)

data class Shot(val turn: Int, val angle: Int, val power: Int)  // angle 0..90, power 1..100

sealed interface Outcome {
    data class HitPanda(val player: Int) : Outcome
    data class HitTerrain(val x: Int, val y: Int) : Outcome
    data object OffScreen : Outcome
    data object TimeOut : Outcome
}

class ShotResult(
    val path: FloatArray,    // interleaved x,y pairs: path[2i], path[2i+1]; size exactly steps*2
    val steps: Int,
    val outcome: Outcome,
    val impactX: Int, val impactY: Int,
    val sunHit: Boolean      // the sun changed expression during this flight
)
```

**There is no `Outcome.HitSun`.** §8 establishes that the sun does not stop the cane, so that outcome would be unreachable. Hitting the sun is a visual effect and travels in `sunHit`.

**Ownership of `path`.** The `ShotResult` published by `MatchEngine` carries its **own** `FloatArray`, trimmed to `steps * 2`. No pooling on that path: the UI animates that array for ~2.6 s while the AI runs hundreds of simulations, and a shared buffer would be overwritten mid-animation. It is ~2.4 KB per turn, one allocation every few seconds.

Reuse does make sense inside the AI, which simulates in a tight loop and discards every trajectory. That is what the §8 variant writing into a borrowed buffer is for.

---

## 7. Scenario generation (`core/ScenarioGen.kt`)

```kotlin
fun generate(seed: Long, width: Int): Scenario
```

The algorithm, in this exact order (any reordering breaks determinism between versions):

1. `rng = Rng(seed)`
2. Pick `skylineMode = rng.nextInt(3)` → `ASCENDING`, `DESCENDING`, `RANDOM`. Reproduces the variety of the original.
3. Walk `x` from 0 accumulating buildings of width `rng.nextIntRange(BUILD_W_MIN, BUILD_W_MAX)` until `width` is covered. The last one is trimmed to the remaining space; if that remainder is below `BUILD_W_MIN`, it is added to the previous building, which may then exceed `BUILD_W_MAX`.
4. Height per building, by mode:
   - `ASCENDING`: `h = lerp(BUILD_H_MIN, BUILD_H_MAX, i/(n-1)) + noise(±15)`
   - `DESCENDING`: the inverse
   - `RANDOM`: `rng.nextIntRange(BUILD_H_MIN, BUILD_H_MAX)`
   - Final clamp to `[BUILD_H_MIN, BUILD_H_MAX]`.
5. `paletteIdx = rng.nextInt(Palette.N_FACADES)`
6. Windows: a 3×4 px grid with a 3 px margin and 3 px spacing. Each window lit with `p = 0.5`. The exact counts are contract, because they fix how many RNG calls are consumed:

```
cols = max(0, (width  - 2*MARGIN + GAP) / (WIN_W + GAP))   // WIN_W=3, GAP=3, MARGIN=3
rows = max(0, (height - 2*MARGIN + GAP) / (WIN_H + GAP))   // WIN_H=4
windows = BooleanArray(rows * cols) { rng.nextFloat() < 0.5f }   // row-major, top down
```

7. Paint `mask` and `color`.
8. Pandas on the buildings at index **1** and **n-2**. `x = building.x + building.width/2`, `roofY = G.H - building.height`. If either of those buildings is narrower than `PANDA_W + 4`, move to the adjacent index towards the centre.
9. Sun at `sunX = width / 2`, `y = 0..SUN_H`.

The phases stay separate exactly as numbered — all the heights first, then all the palettes, then all the windows — not interleaved per building.

**Invariants to test:** no building has its roof above `SKY_BAND`, and the two pandas never share a building.

### Palette (`core/Palette.kt`)

The palette is **closed EGA-16** and comes from the delivered art, not from invention: `art/palette/ega16.gpl` (the index order is normative), the four key pixels of each 16×16 cell of `art/sprites/facades.png` (base, lit window, unlit window, outline) and the two sky colours in `art/sky.txt`.

There are **five** facades, not six. The number comes from the art and changes how much the RNG consumes in `generate`.

---

## 8. Physics (`core/Physics.kt`)

```kotlin
fun simulate(
    scenario: Scenario,
    shooter: Int,          // 0 = left, 1 = right
    shot: Shot,
    wind: Int
): ShotResult

// AI variant: writes the trajectory into `scratch` (size MAX_STEPS*2, owned by the
// caller) and allocates nothing. The returned ShotResult points at `scratch`: it is only
// valid until the next call. NEVER publish this result in a MatchEvent.
fun simulateInto(
    scenario: Scenario,
    shooter: Int,
    shot: Shot,
    wind: Int,
    scratch: FloatArray
): ShotResult
```

Implementation:

```
dir     = if (shooter == 0) +1 else -1
p       = scenario.pandas[shooter]
x       = p.x + dir * HAND_DX
y       = p.roofY - HAND_DY
speed   = shot.power * POWER_TO_SPEED
vx      = dir * COS[shot.angle] * speed
vy      = -SIN[shot.angle] * speed          // y grows downwards
windA   = wind * WIND_ACCEL

repeat MAX_STEPS:
    x  += vx * DT
    y  += vy * DT
    vy += GRAVITY * DT
    vx += windA * DT
    record (x, y) in path

    if x < -20 or x > width + 20        -> OffScreen
    if y > H                            -> OffScreen
    if y < 0                            -> continue (the sky is open above)

    if hitsSun(x, y)                    -> sunHit = true, does NOT end the flight
    if hitsPanda(opponent)              -> HitPanda(opponent)
    if hitsPanda(shooter)               -> HitPanda(shooter)   // own goals are legal
    if terrain.firstSolidOnSegment(...) -> HitTerrain(x, y)

if the steps run out -> TimeOut
```

Non-negotiable details:

- **The cane does not physically collide with the sun.** The sun changes expression and the projectile carries on. It is a detail of the original.
- **Panda collision:** an AABB of `PANDA_W × PANDA_H` centred on `(p.x, p.roofY - PANDA_H/2)`, expanded by `CANE_R`.
- **Self-collision at launch:** the starting point lies inside the thrower's own AABB (`HAND_DX = 10 < PANDA_W/2 + CANE_R = 11`), so the collision with the shooter is ignored for the first 5 steps. That is more robust than raising `HAND_DX`, which would misplace the throwing point at high angles.
- **Anti-tunnelling by segment sampling.** The figure «220 px/s → 1.83 px per step» only describes the instant of the throw: `vy` grows during the fall and the wind keeps accelerating `vx`. Sweeping every angle, power, wind and roof height, the real advance per step reaches **2.50 px** (2.05 horizontal, 2.32 vertical) — measured, not estimated, and pinned by a test.

  That is not enough to punch through a 3 px window, but it is enough to skip a 1–2 px isthmus, which is exactly what the terrain leaves after a few craters, or to cut a corner diagonally. So the terrain check walks the segment `(x₀,y₀) → (x₁,y₁)` with an integer DDA and evaluates `solid()` at **every pixel along the way**; `impactX/impactY` is the **first** solid pixel of the segment, not the end of the step. Cost: one or two checks per step.
- After `HitTerrain` or `HitPanda`, the caller applies `terrain.blast(impactX, impactY, CRATER_R)`. Physics does **not** mutate the terrain.

---

## 9. AI (`core/Ai.kt`)

```kotlin
enum class AiLevel(val sigmaAngle: Float, val sigmaPower: Float, val refine: Boolean) {
    EASY(10f, 12f, false),
    MEDIUM(4f, 5f, true),
    HARD(1f, 1.5f, true)
}

class AiOpponent(private val level: AiLevel, private val rng: Rng) {
    fun chooseShot(scenario: Scenario, me: Int, wind: Int, turn: Int): Shot
}
```

Algorithm:

1. **Coarse search:** angle 10..85 step 5, power 20..100 step 5 → 272 simulations. Score = Euclidean distance from the impact point to the centre of the enemy panda. `OffScreen` and `TimeOut` score `Float.MAX_VALUE`. So does `HitPanda(me)`.
2. **Refinement** (if `level.refine`): a ±4 grid on angle and ±4 on power, step 1, around the best result.
3. **Noise:** `angle += rng.nextGaussian() * sigmaAngle`, same for power. Clamp to valid ranges.

Cost: ~272 × ~350 steps ≈ 95 k iterations (353 simulations when refining). Milliseconds. Even so, run it on `Dispatchers.Default` and show an artificial delay of 600–1200 ms so the turn reads properly.

`AiOpponent` owns **its own** `FloatArray(G.MAX_STEPS * 2)` and calls `simulateInto` (§8). It is the only place in the system where the trajectory buffer is reused, and none of those `ShotResult`s leaves the class.

**Do not implement** an analytical ballistics solution. Wind and intervening buildings invalidate it and it adds nothing the search does not already give.

---

## 10. Shot source (`core/OpponentSource.kt`)

The piece that unifies the three modes:

```kotlin
interface ShotSource {
    suspend fun nextShot(scenario: Scenario, me: Int, wind: Int, turn: Int): Shot
}
```

- `HumanShotSource` — suspends until the UI emits a `Shot` through a `Channel`.
- `AiShotSource` — wraps `AiOpponent`.
- `RemoteShotSource` — suspends until a `Msg.Shot` arrives with the `turn` the transport expects.

`MatchEngine` does not know which one it is facing. If you end up writing three match loops, the design has broken.

---

## 11. Match engine (`core/MatchEngine.kt`)

```kotlin
class MatchEngine(
    val seed: Long,
    val width: Int,
    val sources: List<ShotSource>,   // index = player
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
    class RoundEnd(val winner: Int, val scores: IntArray) : MatchEvent  // no `data`: holds an array
    data class MatchEnd(val winner: Int) : MatchEvent
}
```

The loop: `TurnStart` → `sources[current].nextShot(...)` → `simulate` → `ShotFired` → apply the crater → evaluate → switch turns. The UI only consumes `events` and animates.

`MatchEngine` uses `simulate` (owned array), never `simulateInto`: events outlive the turn that produced them. `scores` is copied when building `RoundEnd`, so the event does not expose the engine's internal array.

---

## 12. Transport (`transport/`)

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

Implementations:

| Class | Based on | Use |
|---|---|---|
| `NearbyTransport` | `play-services-nearby`, `P2P_POINT_TO_POINT` strategy | Primary |
| `RfcommTransport` | `BluetoothSocket` + fixed SPP UUID | Fallback without Google Play Services |
| `LoopbackTransport` | Two crossed `Channel`s in memory | Tests |

### Binary protocol

All integers big-endian. Byte 0 = protocol version (`0x01`), byte 1 = type.

| Type | Name | Body |
|---|---|---|
| `0x01` | `HELLO` | `u8 nickLen`, `nick UTF-8`, `u16 width`, `u8 caps` |
| `0x02` | `MATCH_START` | `i64 seed`, `u16 width`, `u8 startingPlayer`, `u8 roundsToWin` |
| `0x03` | `SHOT` | `u16 turn`, `u8 angle`, `u8 power` |
| `0x04` | `RESULT` | `u16 turn`, `u8 outcomeCode`, `u16 impactX`, `u16 impactY` |
| `0x05` | `REMATCH` | — |
| `0x06` | `BYE` | `u8 reason` |
| `0x07` | `PING` | `i64 nonce` |
| `0x08` | `RESUME` | `i64 seed`, `u16 nextTurn` |
| `0x09` | `HISTORY` | `u16 n`, n × (`u16 turn`, `u8 angle`, `u8 power`) |

Rules:

- **The host is the authority.** It generates the `seed`, decides the `width` (the smaller of the two logical canvases, negotiated in `HELLO`) and who starts.
- A `SHOT` whose `turn` is not the expected one is **discarded**, not queued. This protects against resends.
- `RESULT` is verification, not the source of truth. It is always sent by the shooter, right after its `SHOT`. If the receiver computes an impact differing by more than 2 px, it logs the divergence and **adopts the sender's values** — `outcome`, `impactX` and `impactY`, before applying the crater, because the outcome decides the score. If it happens more than once per match, it is a determinism bug: fix it, do not paper over it.
- Turn timeout: 90 s without a `SHOT` → `BYE(TIMEOUT)`, with a visible countdown from 75 s.
- `PING` every 10 s while idle; 3 failures → `LinkState.LOST`.
- Reconnection: determinism makes it cheap. `RESUME` states where the match stands and `HISTORY` replays the shots, from which both sides rebuild the identical state.

### Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" android:minSdkVersion="31"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"  android:minSdkVersion="31"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"     android:minSdkVersion="31"/>
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" android:minSdkVersion="33"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
```

Request them at runtime **only when entering Bluetooth mode**, never at startup. An explanatory screen comes before the system dialog.

`ACCESS_COARSE_LOCATION` is not optional: declaring FINE without COARSE is a lint error (`CoarseFineLocation`) and breaks the build. From Android 12 the user may grant COARSE only, so the M6 permission flow must treat "COARSE only" as a valid state, not as a denial.

---

## 13. Rendering (`app/`)

### Terrain

- An `IntArray(width * G.H)` buffer in ARGB, built from `Terrain.mask` + `Terrain.color` + the palette.
- Converted to an `ImageBitmap` via `Bitmap.createBitmap(buf, w, h, ARGB_8888).asImageBitmap()`.
- Regenerate **only** when a crater is applied (once per turn), patching the affected rectangle. Never per frame.

### Drawing

```kotlin
Canvas(Modifier.fillMaxSize()) {
    // The scale comes from core/Layout.kt (§3). The renderer does NOT recompute it: if
    // the two calculations diverge, the canvas overflows the screen or leaves dead bands.
    val scale = logicalScale(size.width.toInt(), size.height.toInt())
    // sky, skyline, terrain, sun, pandas, cane, trail
    drawImage(
        image = terrainBitmap,
        dstSize = IntSize(w * scale, G.H * scale),
        filterQuality = FilterQuality.None
    )
}
```

`FilterQuality.None` on **every** `drawImage`. No exceptions.

Sprites live in `app/src/main/assets/sprites/`, **not** in `res/drawable*/`: the resource system applies density scaling and would destroy the nearest-neighbour look. Load them with `BitmapFactory.Options(inScaled = false)` and paint them with `isFilterBitmap = false` and `isAntiAlias = false`.

### Shot animation

`ShotResult.path` holds ~300 points at 120 Hz. Play it back at 60 fps consuming 2 points per frame → the real flight duration. Add a `speedMultiplier` setting (1×, 2×) that only changes how many points are consumed per frame.

Trail: the last 12 points with decreasing opacity.

### Explosion

`boom.png`, 8 frames at 60 ms = 480 ms. The crater is applied to the terrain on **frame 3**, not at the start and not at the end.

---

## 14. User interface

The UI does **not** live in the logical canvas. It sits on top, in native dp.

- **Top HUD:** score, name of the player whose turn it is, wind indicator (an arrow whose length is proportional to `|wind|`, with the numeric value beside it).
- **Bottom controls:** two sliders (Angle 0–90, Power 1–100) each with an editable numeric field to its right. The original was played by typing numbers; keeping that route is what allows adjusting one unit at a time.
- A **Repeat previous** button that preloads the player's last shot. It is the shortcut people use most in this game.
- A large **Throw!** button.
- In remote mode, lock the controls and show "Waiting for {nick}…" along with the link state.

Navigation: `Menu → [One player | Two local | Bluetooth] → Match setup → Game → Result`.

---

## 15. Mandatory tests (`core/src/test`)

1. `Rng` reproduces the same sequence for the same seed (values pinned by hand in the test).
2. `generate(seed, w)` is identical across 100 runs — compare the `mask` hash.
3. No roof above `SKY_BAND`; pandas always on different buildings.
4. With no wind and no obstacles, the range at 45° matches `v²/g` within 2 %.
5. Wind +10 and wind −10 produce ranges symmetric about the one with no wind.
6. `blast()` clears exactly the pixels inside the radius and none outside.
7. A shot that hits the enemy panda returns `HitPanda(opponent)`.
8. A 90° shot at low power comes back and hits the thrower.
9. `simulate` never exceeds `MAX_STEPS` nor writes outside `path`.
10. Serialising and deserialising each `Msg` returns the original object.
11. `MatchEngine` with two deterministic `ShotSource`s produces the same sequence of events across two runs.
12. A full match over `LoopbackTransport`: both sides end with the same score.

---

## 16. Milestones and acceptance criteria

| Milestone | Content | Acceptance criterion |
|---|---|---|
| **M0** | Gradle skeleton, 3 modules, CI running the tests | `./gradlew test` green |
| **M1** | `Rng`, `Trig`, `ScenarioGen`, `Physics` + tests 1–9 | Tests green. §2 calibration confirmed |
| **M2** | Rendering with geometric shapes, full local hot-seat | A playable 3-round match with coloured rectangles |
| **M3** | `AiOpponent` + one-player mode | The AI on HARD hits within ≤3 turns in ≥80 % of generated scenarios |
| **M4** | Real sprites, animations, sun, trail | No visual mismatch: pandas resting on the roof, explosion centred on the crater |
| **M5** | `Transport` + `LoopbackTransport` + protocol + tests 10–12 | A full match over the local loop with no divergences |
| **M6** | `NearbyTransport`, permission flow, pairing | A full match between two physical devices, including reconnection after 10 s without a link |
| **M7** | `RfcommTransport`, sound, settings, polish | Works on a device without Google Play Services |

M4 depends on the art delivery, which is already in the repository (`art/`). M2 and M3 must be completable without it.

---

## 17. Known risks

1. **Floating-point divergence between devices.** Mitigated with `StrictMath` and precomputed tables. The protocol's `RESULT` is the safety net. If the divergence counter goes up, that is a bug, not noise.
2. **Nearby Connections requires Google Play Services.** Hence `RfcommTransport`. Do not drop it as "the secondary mode": it is what makes the app work on devices without GMS.
3. **Bluetooth permissions on Android 12+.** Denial is common. You need a readable error state, not a silent `catch`.
4. **Panda wider than its building.** Covered by step 8 of `ScenarioGen`, but it is the classic failure of this game. Dedicated test.
5. **Memory leaks through `ImageBitmap`.** Regenerating the terrain bitmap per frame instead of per crater sinks performance on low-end devices. Verify with the Profiler in M4.
6. **Tunnelling after several craters.** Covered by the DDA of §8 and pinned by a test.

---

## 18. Legal

Do not port code from `GORILLA.BAS` and do not reuse its assets: they belong to Microsoft. Game mechanics are not protectable; code and assets are. The published title is our own, and the theme — pandas and bamboo canes — is not the original's.
