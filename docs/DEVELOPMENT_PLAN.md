# Throwing Bambu — Development plan

Derived from [`DEVELOPMENT_SPEC.md`](DEVELOPMENT_SPEC.md). The specification is the **contract**; this document is the **execution plan**: what gets built, in what order, what counts as done, and what has to be decided before starting.

- **Plan version:** 2.0 (English, retheme applied)
- **Base:** specification §0–§18, with D-01…D-09 and D-12 already incorporated
- **Estimate:** ~23 developer-days for the whole project (1 person; excludes physical-device testing in M6)
- **Status:** M0, M1, M3, M5 complete; M2 and M4 code complete. Next: M6.

---

## 1. How to read this plan

| Item | Meaning |
|---|---|
| `T-xx` | An atomic task. The unit of commit and of review. |
| `D-xx` | A prior decision. Must be settled **before** the milestone that consumes it. |
| DoD | *Definition of Done*: a verifiable condition, not an opinion. |
| Est. | Estimate in days (d) of 6 effective hours. |

Operating rule: **no task closes without its test or its described manual check**. A task that cannot be verified is badly defined and gets split.

---

## 2. Prior decisions — gaps and contradictions in the specification

The specification was solid, but it held **twelve points that could not be implemented as written**. Each carries a concrete proposal.

**Status:** ✅ **D-01 to D-09 and D-12 are applied** to `DEVELOPMENT_SPEC.md`; the text is kept here as the record of the decision and its reason. 🟡 D-11 is still open; D-10 was consumed by T-46.

### ✅ D-01 — Pooling `path` is incompatible with `MatchEvent` — *applied (§6, §8, §9, §11)*

§6 said `ShotResult.path` was reused through a pool; §11 publishes it in a `Flow` and §13 animates it for ~2.6 s. In AI mode, `chooseShot` runs 272–353 simulations **while the UI is still reading the previous turn's path**: the buffer gets overwritten mid-animation.

Applied: two separate routes. The match route returns its **own, trimmed** `FloatArray(steps * 2)` — ~2.4 KB per turn, one allocation every few seconds, irrelevant. The AI route uses `simulateInto(scratch, …)` over a reusable buffer, confined to the AI's `Dispatchers.Default`.

The optimisation the specification asked for is real, but it belongs to the AI, not to the engine.

### ✅ D-02 — `Outcome.HitSun` is unreachable — *applied (§6, §8)*

§6 declared `HitSun` as an outcome; §8 says explicitly that the sun does **not** stop the flight. Both cannot be true. Applied: `HitSun` removed, `ShotResult.sunHit` added. The renderer uses the flag for the sun's expression.

### ✅ D-03 — Anti-tunnelling: the §8 figure only covers the launch speed — *applied (§8)*

«220 px/s → 1.83 px per step» only describes the instant of the throw: `vy` grows during the fall and the wind keeps accelerating `vx`.

**Correction made during M1.** The first version of this decision estimated a ceiling of ~10 px/step by extrapolating 15 s of free fall. That is false: the cane leaves the canvas long before reaching such a speed. A full sweep of angles, powers, winds and roof heights gives **2.50 px** of maximum advance per step (2.05 horizontal, 2.32 vertical).

The conclusion stands but the reason changed: nothing tunnels through a 3 px window — what tunnels is a 1–2 px isthmus left by the terrain after several craters, or a diagonal corner cut. The ceiling is pinned by `stepAdvanceStaysBelowTheMeasuredCeiling`, which fails if anyone raises it without revisiting this decision.

### ✅ D-04 — `logicalWidth` could return a canvas wider than the screen — *applied (§3, §13)*

`logicalWidth` forced a minimum of `W_MIN = 320` after dividing by a scale derived **only from the height**. On a 1080×2400 in portrait: `scale = 2400/200 = 12`, `1080/12 = 90` → clamped to 320 → drawing `320 × 12 = 3840 px` onto a 1080 px screen. A 72 % crop of the playfield.

Applied: `logicalScale`/`logicalWidth` in `core` as the single source of truth, with the scale lowered until `W_MIN` genuinely fits, and the game screen locked to landscape. The renderer calls the same function instead of computing its own.

### ✅ D-05 — The §2 calibration did not match its own physics — *applied (§2)*

With `POWER_TO_SPEED = 2.2`, power 68 → `v = 149.6 px/s`. Range at 45°: `v²/g = 279.8 px`, time 2.645 s. The time matched the document (~2.6 s); **the range did not**: 280 px, not ~300 px (−6.7 %). Test 15.4 demands 2 % tolerance, so as written M1 was born failing.

Applied: keep `GRAVITY = 80` (flight time is what players feel) and correct §2. The alternative, `GRAVITY = 74.6`, would speed the whole game up by 7 % to match a round number written by eye.

### ✅ D-06 — `data class` with arrays breaks `equals`/`hashCode` — *applied (§6, §11)*

`Building(windows)`, `ShotResult(path)` and `RoundEnd(scores)` compared arrays by identity. Tests 15.2 and 15.11 compare scenarios and event sequences: they would pass or fail for the wrong reason. Applied: `data` dropped where arrays are involved, explicit `contentEquals`, and `Terrain.fingerprint()` for the determinism tests.

### ✅ D-07 — The exact window geometry was missing — *applied (§7)*

§7.6 said "a 3×4 px grid with a 3 px margin and 3 px spacing" without fixing the number of rows and columns. Any difference in interpretation breaks determinism, because it **changes how many RNG calls are consumed**. The formula is now contract, and exactly `rows*cols` values are drawn, always.

### ✅ D-08 — `N_FACADES` and the palette did not exist — *applied (§7)*

§7.5 used `rng.nextInt(nFacades)` without defining it, and `Terrain.color` stored indices into a palette nobody declared.

Applied twice. First as a placeholder with six invented facades, because there was no art yet. Then, when the art landed in `main`, replaced by the real thing: closed EGA-16 in its normative order, **five** facades read from the four key pixels of each cell in `facades.png`, and the sky gradient from `sky.txt`. `PaletteMatchesArtTest` fails if the engine and the delivered art ever disagree.

The count matters: five instead of six changes how much the RNG consumes and therefore every generated scenario.

### ✅ D-09 — `nextGaussian` could return infinity — *applied (§4)*

Box-Muller with `ln(u1)` blows up if `nextFloat()` returns exactly 0.0 — with 24 bits of mantissa, once every 16.7 M draws. With the AI calling it twice per turn that is a crash every ~8 M turns: rare, not impossible, and very hard to diagnose. Applied: resample, never add an epsilon.

### ✅ D-10 — The protocol: `HELLO` without `width`, and no way to resume

§12 requires negotiating `width` in `HELLO` ("add `u16 width`"), but the table did not show it. And the M6 criterion asks for "reconnection after 10 s without a link" with no message capable of recovering the state.

Proposal, already reflected in the specification's table: `HELLO` carries `u16 width` and `u8 caps`; `0x08 RESUME { i64 seed, u16 nextTurn }` and `0x09 HISTORY { u16 n, n×(u16 turn, u8 angle, u8 power) }` are added. Determinism makes resuming trivial: the shot history is enough to rebuild the state.
**Consumed in T-46.** `resyncHistory()` asks and merges, `RemoteMatch(resumeFrom = …)` replays;
`nextTurn` is the first *gap* in what the asker holds, not its highest turn, so a hole in the
middle is filled rather than left to stall the replay. A `RESUME` carrying another seed is
answered with `BYE(PROTOCOL_ERROR)`: handing over this match's shots would look like a
determinism bug rather than like the mismatch it is.

### 🟡 D-11 — `RESULT`: who sends it and what is adopted

§12 said the receiver "adopts the sender's value" when the difference exceeds 2 px, without fixing the sender or whether the `outcome` is adopted too.

Proposal: always sent by the shooter, right after its `SHOT`. The receiver adopts `outcome`, `impactX` and `impactY` **before** applying the crater — the outcome decides the score, so adopting only the coordinates leaves the scoreboards divergent. A `divergences` counter is shown on the debug screen; more than one per match is an open determinism bug, not a warning. **Consumed in M5.**

### ✅ D-12 — The §12 permission list was incomplete — *applied (§12)*

Declaring `ACCESS_FINE_LOCATION` without `ACCESS_COARSE_LOCATION` is a lint **error** (`CoarseFineLocation`) and aborts the app build. The first CI run of M0 caught it.

It is not a formality: from Android 12 the user may grant COARSE only, so the M6 permission flow (T-42) must treat "COARSE only" as a valid state and check whether Nearby works with that grant, instead of treating it as a denial.

### ✅ D-13 — The protocol belongs in `core`, not in the Android module — *applied (§1, §12)*

§1 put the whole of `transport/` in Android. But messages, codec, the `Transport`
interface, loopback and the networked session are pure logic — frames in, frames out —
and an Android library cannot be built or tested without the SDK.

Applied: `core/net/` holds the protocol; `transport/` keeps only the radios
(`NearbyTransport`, `RfcommTransport`). The result is that a **complete match across a
link is verified on a plain JVM**, which is exactly the property the M5 criterion asks
for. It also keeps golden rule §0.1 intact: none of this needs Android.

### Minor points assumed without a decision

- §3 claimed "6–9" buildings fit. With `BUILD_W` ∈ [24, 40] and `width` ∈ [320, 460] the real range is **8–19**. It does not affect the code, but the number appears in the contract document and has been corrected.
- §7.3: when absorbing the remainder, the last building **may** exceed `BUILD_W_MAX`. Accepted, and the invariant test allows for it.
- §7.8: with `W_MIN = 320` and `BUILD_W_MAX = 40` there are always ≥8 buildings, so indices 1 and n−2 never coincide. `generate` still requires `n >= 4`, so the failure is explicit if anyone touches the constants.
- `Shot.turn` is a **match-wide monotonic counter** (0-based), not per round: `windForTurn` and the protocol's `u16 turn` both consume it. Documented in the KDoc of `Shot`.
- §15.8: "low power" at 90° must be ≥10 for the cane to leave the grace steps before coming back. The test uses power 20 and asserts it actually flew.

### Open questions for M4, from the art delivery

- The panda's pivot is (12,24) while its body occupies x=4..19, so the drawing sits half a pixel off the AABB the engine centres on `panda.x`. Decide in M4 whether to shift the sprite or the box.
- Each facade carries a fourth key colour, `outline`, which §7 does not use. It is available for the building edges.
- `bamboo.png` is an 8×8 cell against a collision radius of 3. Fine, but worth pinning when the sprite is drawn.

---

## 3. Architecture and repository layout

```
throwing-bambu/
├── gradle/libs.versions.toml        version catalog (single source)
├── settings.gradle.kts
├── core/                            plain Kotlin JVM — no Android
│   ├── src/main/kotlin/…/core/      GameConstants, Rng, Trig, Palette, Model, ScenarioGen,
│   │                                Physics, Layout, (Ai, ShotSource, MatchEngine to come)
│   └── src/test/kotlin/             tests 1–9 and 11
├── transport/                       Android library
│   ├── Transport, Msg, Codec, LinkState, Peer
│   ├── LoopbackTransport, NearbyTransport, RfcommTransport
│   └── src/test/                    tests 10 and 12 (plain JVM, no instrumentation)
├── app/                             Android app — Compose
│   ├── ui/menu, ui/setup, ui/game, ui/result
│   ├── render/, audio/, settings/, di/
│   └── src/main/assets/sprites/     the delivered art (never res/drawable*)
├── art/, tools/                     art pipeline — see AGENTS.md
└── docs/
```

**Structural rules enforced in CI**, not by good intentions:

1. `core` is `kotlin("jvm")`. An `import android.` does not compile: there is no SDK on the classpath.
2. An architecture test in `core`: no class references `android.*`, and no trigonometry outside `Trig.kt`/`Rng.kt`.
3. `transport` knows nothing about Compose. `app` implements no protocol.
4. No dependency-injection library: the graph is small and a hand-written `AppContainer` is enough. Hilt here is cost without return.

---

## 4. Working conventions

- **Branch:** `claude/plan-desarrollo-pw6mrm`. Atomic commits per task, message `M<milestone>/T-xx: description`.
- **Language:** everything in English, per `AGENTS.md` — identifiers, comments, documents and commit messages.
- **No attribution** in commits or pull requests, per `AGENTS.md`.
- **CI:** `./gradlew build test` on every push, plus `:core:test` on macOS and a debug APK as an artifact. A branch with red CI takes no new tasks.
- **Quality:** `ktlint` + `detekt` in the pipeline since M0. Adding them in M7 would mean 400 warnings at once.
- **Coverage:** no percentage target. The contract is the list of 12 mandatory tests in §15 plus the ones this plan adds.
- **Any change to a constant in `G`** requires updating `DEVELOPMENT_SPEC.md` in the **same commit**. That is the rule in §0 and it is reviewed.

---

## 5. Milestone breakdown

### M0 — Skeleton ✅ *complete*

| ID | Task | Est. | Status |
|---|---|---|---|
| T-01 | `settings.gradle.kts`, `libs.versions.toml`, Gradle wrapper 8.14.3 | 0.2 d | ✅ |
| T-02 | Modules `core` (JVM), `transport` (android-lib), `app` (android-app), `minSdk 24 / target 35 / compile 35`, dependency graph of §1 | 0.15 d | ✅ |
| T-03 | Workflow `ci.yml`: JDK 17, Gradle cache, `build test`, ktlint + detekt, debug APK published as an artifact | 0.15 d | ✅ |
| T-04 | `README.md`, own `LICENSE` and the legal note of §18 | 0.05 d | ✅ |

**Two deliberate deviations from the original plan:**

- **No `build-logic`.** With three modules, an included build of convention plugins costs more configuration and build time than it saves: what `transport` and `app` share is ~15 lines. Reconsider past five modules.
- **Added `-PcoreOnly`.** Excludes the Android modules from the build. Not a workaround: it makes the §0.1 promise real — `core` is testable without Android — and allows working on the engine on a machine or container with no SDK.

**Unplanned additions:**

- Every run publishes the **debug APK** as an artifact (`apk-debug`, 14 days), built after the tests: red build, no APK. The release APK waits for M7, where keystore and R8 arrive (T-52).
- A second CI job runs `:core:test` on **macOS**. §17.1 warns about floating-point divergence between JVM implementations; from M1 on this catches it in the commit that introduces it, not in M6 as a networking bug.

**DoD:** ✅ CI green on both jobs. ⏳ `app` starting on a device is the one item still unverified: it needs a phone or an emulator, which this environment does not have.

---

### M1 — Deterministic core ✅ *complete* — *the milestone that decides the project*

| ID | Task | Est. | Status |
|---|---|---|---|
| T-05 | `GameConstants.kt` (§2) + `Palette.kt` (D-08) | 0.15 d | ✅ |
| T-06 | `Rng.kt` xorshift64\* + robust `nextGaussian` (D-09) + `windForTurn` | 0.3 d | ✅ |
| T-07 | `Trig.kt`: `SIN`/`COS` tables with `StrictMath`, enforced by an architecture test | 0.2 d | ✅ |
| T-08 | `Model.kt`: `Building`, `Terrain`, `Panda`, `Scenario`, `Shot`, `Outcome`, `ShotResult` | 0.4 d | ✅ |
| T-09 | `Layout.kt`: corrected `logicalWidth` (D-04), shared by `core` and the renderer | 0.1 d | ✅ |
| T-10 | `ScenarioGen.generate` (§7, steps 1–9 in exact order) with the window grid of D-07 | 0.8 d | ✅ |
| T-11 | `Physics.simulate` with anti-tunnelling DDA (D-03), 5 grace steps, non-blocking sun | 0.8 d | ✅ |
| T-12 | Tests 15.1–15.9 + architecture tests | 0.8 d | ✅ |
| T-13 | Calibration: measure range and time at 45°/68 and correct §2 (D-05) | 0.15 d | ✅ |

**DoD:** ✅ the nine §15 tests green (38 in total with the additions), `mask` fingerprint stable across 100 runs and two JVMs, calibration documented with the measured figure.

**Figures measured in M1** (they supersede every earlier estimate):

| Quantity | Analytical | Measured | Drift |
|---|---|---|---|
| Range at 45° / power 68 | 279.8 px | **281.2 px** | +0.52 % |
| Flight time | 2.645 s | **2.658 s** (386 steps) | +0.49 % |
| Wind shift at ±10 | — | **±52.8 px**, symmetric to ±0.01 px | — |
| Maximum advance per step | 1.83 px *(§8 estimate)* | **2.50 px** (H 2.05 / V 2.32) | +37 % |

The drift in range and time is the error of the explicit Euler integrator, inside the 2 % the §15.4 test demands. The last row forced the correction of D-03.

**Decisions consumed:** D-01, D-02, D-03 (corrected), D-05, D-06, D-07, D-08, D-09.

---

### M2 — Geometric rendering and hot-seat — *code complete, pending on-device validation*

**Goal:** a playable local 3-round match drawn with coloured rectangles. **No dependency on art.**

| ID | Task | Est. | Depends on |
|---|---|---|---|
| ✅ T-14 | `ShotSource` + `HumanShotSource` (Channel) + `AiShotSource` (stub) | 0.2 d | M1 |
| ✅ T-15 | `MatchEngine`: loop, `MatchEvent`, crater, score, rounds, `roundsToWin` | 0.7 d | T-14 |
| ✅ T-16 | `TerrainBitmap`: ARGB `IntArray` from `mask`+`color`+palette → `ImageBitmap`; regenerated only per crater, patching the affected rect | 0.5 d | M1 |
| ✅ T-17 | `GameCanvas`: sky gradient, skyline, terrain, sun, pandas, cane, 12-point trail; `FilterQuality.None` everywhere; sky-coloured side bars | 0.6 d | T-16 |
| ✅ T-18 | Shot animation: 2 path points per frame at 60 fps, `speedMultiplier` 1×/2× | 0.3 d | T-17 |
| ✅ T-19 | HUD (score, turn, proportional wind arrow + value) and controls (2 sliders + editable numeric field, **Repeat previous**, Throw! button) | 0.6 d | T-15 |
| ✅ T-20 | Navigation `Menu → Mode → Setup → Game → Result` + landscape lock (D-04) | 0.4 d | T-19 |
| ✅ T-21 | `MatchEngine` survives rotation and backgrounding: the engine lives in a `ViewModel` with `viewModelScope`, the UI only consumes `events` | 0.3 d | T-15 |

**DoD:** ✅ test 15.11 green, plus seven more engine tests (46 in total). ⏳ the playable part — a full 3-round match, wind changing each turn, craters persisting, correct score — needs a device or emulator, which the development container does not have. The CI compiles and lints `app`; playing it is the user's check.

**Design decision taken here:** `MatchEngine` emits with no buffer, so `emit` suspends until the collector has taken the event. The engine therefore runs at the pace of whoever is watching, and a crater cannot open before the UI has finished animating the throw that caused it. Without that back-pressure the engine would race ahead and the terrain would change mid-flight.

**Two additions to the contract**, both derived from the specification rather than invented, and both now in `DEVELOPMENT_SPEC.md`: `scenarioSeedForRound` (the per-round scenario seed, derived like the wind so nothing has to be transmitted) and the rule that the opening player alternates each round, as in the original.

**Risk note for §17.5:** T-16 is exactly where performance sinks if anyone regenerates the bitmap per frame. It is verified in M4 with the Profiler, but the decision is taken here.

---

### M3 — AI and one-player mode ✅ *complete*

| ID | Task | Est. | Depends on |
|---|---|---|---|
| ✅ T-22 | `AiOpponent`: 16×17 = 272 coarse search, score by Euclidean distance to the enemy centre, maximum penalty for `OffScreen`/`TimeOut`/own goal | 0.5 d | M1 |
| ✅ T-23 | ±4/±4 step-1 refinement and per-level Gaussian noise (`EASY`/`MEDIUM`/`HARD`) | 0.3 d | T-22 |
| ✅ T-24 | `simulateInto` over a reusable arena (D-01) + `Dispatchers.Default` + artificial 600–1200 ms delay | 0.3 d | T-22 |
| ✅ T-25 | Wire up one-player mode: level selection in match setup | 0.2 d | M2, T-23 |
| ✅ T-26 | **AI evaluation harness**: 200 generated scenarios, measures turns-to-hit per level; runs as a long test tagged `@Tag("slow")`, outside CI by default | 0.5 d | T-23 |
| ➖ T-27 | Tune the sigmas if T-26 misses the criterion | 0.2 d | not needed: the criterion was met first time |

**DoD:** ✅ measured over 200 generated scenarios, with the crater applied after each miss exactly as the engine does:

| Level | Hits within ≤3 turns | Mean turns |
|---|---|---|
| EASY | 41 % | 2.52 |
| MEDIUM | 73 % | 2.08 |
| **HARD** | **87 %** | **1.66** |

The criterion was 80 % for HARD, so the sigmas in §9 stand as written and T-27 was not needed. The harness lives in `core/src/test/.../slow/` and stays out of CI by default; run it with `./gradlew :core:test -PrunSlowTests` whenever the AI changes.

The ladder climbing is asserted too, not just the top rung: an AI whose difficulty levels do not separate is not a difficulty setting.

**Scope decision confirmed:** no analytical ballistics solution (§9). The search costs milliseconds and tolerates wind and intervening buildings.

---

### M4 — Sprites and visual finish — *code complete, pending on-device validation*

The art is already delivered in `art/` and passes `tools/verify_assets.py`. This milestone is integration, not production.

| ID | Task | Est. |
|---|---|---|
| ✅ T-28 | Copy the sprites to `app/src/main/assets/sprites/` and load them with `inScaled = false`, painting with filtering off | 0.4 d |
| ✅ T-29 | Panda: 6 frames (idle, arm_left, arm_right, chest_1, chest_2, defeated), 24×24 cell, pivot (12,24) | 0.5 d |
| ✅ T-30 | Cane in flight: 4 frames at 80 ms, 8×8 cell; trail behind it | 0.4 d |
| ✅ T-31 | Explosion `boom.png`: 8 frames × 40 ms, crater applied on **frame 3** | 0.4 d |
| ✅ T-32 | Sun with two expressions driven by `sunHit` (D-02), 1 s of `ouch` | 0.3 d |
| ✅ T-33 | `skyline.png` as a background band, cropped from the right, and the two-colour sky gradient | 0.4 d |
| ⏳ T-34 | Alignment pass: feet exactly on `roofY`, explosion centred on the crater, the half-pixel pivot question, no fractional scaling | 0.4 d |
| ⏳ T-35 | Profiler check on a low-end device (§17.5): no memory growth per turn, stable 60 fps | 0.4 d |

**DoD:** ⏳ no visual mismatch, verified with screenshots at 1× and at the device's maximum scale, plus a clean Profiler run. Both need a device.

**How the art is packaged.** The PNGs are build output of `tools/gen_sprites.py`, so a `Sync` task copies them from `art/sprites` into generated assets at build time instead of a second copy living in the repository. Regenerate the art and the APK picks it up; no file drifts.

**The crater lands on explosion frame 3.** The engine emits `TerrainChanged` right after `ShotFired`, so the view model plays frames 0–2 when the flight ends, lets the crater apply, and plays 3–7 afterwards. That is what puts the hole in the ground on the frame the art was drawn for, instead of before the blast is visible.

**The terrain layer is now transparent** where there is no building, and the sky is a 1×200 bitmap stretched with nearest-neighbour. The skyline band needed to sit *behind* the buildings, which the previous sky-in-the-terrain-bitmap approach made impossible.

---

### M5 — Transport, protocol and loopback ✅ *complete*

| ID | Task | Est. | Depends on |
|---|---|---|---|
| ✅ T-36 | `Msg` (sealed) + big-endian `Codec`, version `0x01`, types `0x01`–`0x09` with the D-10 corrections | 0.6 d | — |
| ✅ T-37 | `Transport`, `LinkState`, `Peer`; `LoopbackTransport` with two crossed `Channel`s | 0.4 d | T-36 |
| ✅ T-38 | `RemoteShotSource`: suspends until `Msg.Shot` with the expected `turn`; **discards** other turns (§12) | 0.4 d | T-37 |
| ✅ T-39 | Remote match session: host as authority (seed, `width` = min of both canvases, starting player), `MATCH_START`, `REMATCH`, `BYE` | 0.6 d | T-38 |
| ✅ T-40 | `RESULT` as verification, with adoption and a divergence counter (D-11); debug screen showing it | 0.4 d | T-39 |
| ✅ T-41 | Keep-alive: `PING` every 10 s, 3 failures → `LOST`; 90 s turn timeout → `BYE(TIMEOUT)` with a countdown from 75 s | 0.4 d | T-39 |
| ✅ T-42 | Tests 15.10 (round-trip of every `Msg`) and 15.12 (full loopback match, same score on both sides) | 0.5 d | T-40 |

**DoD:** ✅ a complete match over `LoopbackTransport` with `divergences == 0`, identical scores **and identical shot history** on both sides, no frame dropped. Round-trip of all nine message types plus the edges.

The loopback puts frames through the codec rather than handing objects over: a loopback that skipped the encoding would test the match and not the protocol, and the encoding is the part that has to survive a real radio.

The truncation test is exhaustive rather than representative — **every prefix of every message** must be rejected — because a half-frame that decodes as a whole one is how a link turns into a wrong move nobody ordered.

**D-11 is applied in part.** Divergences are detected and counted; adopting the sender's value is not implemented, because it requires the engine to pause before applying a crater until the peer's `RESULT` arrives, coupling the match loop to the link. With determinism holding the counter stays at zero, so adoption is a safety net for a case that has not occurred; it is scheduled for M6, where real latency makes it testable.

**The keep-alive is a pure state machine** (`LinkWatchdog`) over an injected clock, not a coroutine full of delays: "ping every 10 s, give up after 3 unanswered, abandon a turn after 90 s" would otherwise mean a test suite that waits minutes to cover one path.

---

### M6 — Nearby Connections and real pairing (4 d)

| ID | Task | Est. |
|---|---|---|
| T-43 | `NearbyTransport` with `P2P_POINT_TO_POINT`: advertise, discover, connect, `BYTES` payloads, callbacks mapped to `Flow`/`StateFlow` | 1.2 d |
| T-44 | Permission flow: explanatory screen **before** the system dialog, requested only when entering Bluetooth mode, readable error states for denied permission / Bluetooth off / location disabled on ≤ API 30, and "COARSE only" treated as valid (D-12) | 0.8 d |
| T-45 | Pairing UI: host/guest, peer list with nicknames, link state, cancellation | 0.7 d |
| ✅ T-46 | Reconnection: `RESUME` + `HISTORY` (D-10), deterministic state rebuild, resume on the right turn | 0.8 d |
| T-47 | Testing on two physical devices, including the 10 s link drop from the acceptance criterion | 0.5 d |

**DoD:** a full match between two physical devices, with a 10 s disconnection in the middle and correct resumption, `divergences == 0`. Denying each permission produces a specific message, never a silent `catch`.

---

### M7 — RFCOMM, audio, settings and polish (4 d) — *localisation already done*

| ID | Task | Est. |
|---|---|---|
| T-48 | `RfcommTransport`: `BluetoothSocket` + fixed SPP UUID, server/client, length-prefixed framing over the byte stream | 1.2 d |
| T-49 | Transport selection: Nearby when GMS is present, RFCOMM otherwise; manual override in settings (§17.2 — not a secondary mode) | 0.4 d |
| T-50 | Audio: throw, explosion, victory, defeat; `SoundPool`, respects silent mode | 0.6 d |
| T-51 | Persistent settings (DataStore): `speedMultiplier`, sound, default AI level, rounds to win | 0.4 d |
| T-52 | Release build: R8, `proguard-rules`, signing keystore, signed release APK in CI; verify that obfuscation does not break the determinism tests | 0.5 d |
| ✅ T-53a | **Localisation, brought forward from M7**: every string in resources, five languages (en/es/ca/fr/de), in-game language picker | 0.4 d |
| T-53b | Polish: transitions, empty states, accessibility of the numeric controls | 0.4 d |

**DoD:** a full RFCOMM match on a device without GMS. An installable, playable release APK in all three modes.

---

## 6. Test traceability

| # (§15) | Description | Milestone | Status |
|---|---|---|---|
| 1 | `Rng` reproduces the sequence (values pinned by hand) | M1 | ✅ |
| 2 | `generate` identical across 100 runs (`mask` fingerprint) | M1 | ✅ |
| 3 | No roof above `SKY_BAND`; pandas on different buildings | M1 | ✅ |
| 4 | Range at 45° ≈ `v²/g` ±2 % | M1 | ✅ |
| 5 | Wind ±10 gives symmetric ranges | M1 | ✅ |
| 6 | `blast()` clears exactly the circle | M1 | ✅ |
| 7 | Hitting the enemy → `HitPanda(opponent)` | M1 | ✅ |
| 8 | 90° at low power → own goal | M1 | ✅ |
| 9 | `simulate` never exceeds `MAX_STEPS` nor writes outside `path` | M1 | ✅ |
| 10 | Round-trip of every `Msg` | M5 | ✅ |
| 11 | `MatchEngine` deterministic with deterministic sources | M2 | ✅ |
| 12 | Full loopback match, same score | M5 | ✅ |

**Tests added by this plan** (not in §15; they cover real risks):

| # | Description | Milestone | Status |
|---|---|---|---|
| A1 | `core` references neither `android.*` nor trigonometry outside `Trig`/`Rng` | M1 | ✅ |
| A2 | The panda is never wider than its building (§17.4) | M1 | ✅ |
| A3 | Anti-tunnelling: a fast fall onto a 1 px slab still hits | M1 | ✅ |
| A4 | `logicalWidth` × scale ≤ screen width on 12 real resolutions | M1 | ✅ |
| A5 | The engine's palette matches the delivered art (`facades.png`, `sky.txt`) | M1 | ✅ |
| A6 | The `path` published in a `MatchEvent` is not altered by the next turn | M1 | ✅ |
| A7 | `Codec` rejects version ≠ `0x01` and truncated bodies without an uncaught exception | M5 | ✅ |
| A8 | A `SHOT` with an unexpected `turn` is discarded and does not stall the engine | M5 | ✅ |
| A9 | Resumption: applying `HISTORY` rebuilds a state identical to the uninterrupted one | M6 | ✅ |

---

## 7. Critical path and parallelism

```
M0 ──► M1 ──┬──► M2 ──┬──► M3 ✅ (one-player mode playable)
     ✅       ✅   ✅   │
                      ├──► M4 ✅ (sprites integrated)
                      │
                      └──► M5 ──► M6 ──► M7
```

- **Critical path:** M6 → M7 ≈ 8 d remaining.
- **M4 no longer blocks anything** — the art is in the repository — but it still needs M2's renderer underneath.
- **Point of no return:** already passed. Any change to `G`, to the RNG order or to the window geometry now invalidates seeds; after M5 it also breaks cross-device play and requires a protocol version bump.

---

## 8. Risks — triggers and response

| # | Risk (§17) | Observable trigger | Response |
|---|---|---|---|
| R1 | Floating-point divergence | `divergences > 0` in any match | Stop. Reproduce with the same seed on both JVMs, compare `path` step by step. Do not raise the 2 px threshold. |
| R2 | Nearby requires GMS | A device without GMS in testing | `RfcommTransport` (M7) is committed scope, not optional. |
| R3 | Bluetooth permissions on 12+ | Denial during field testing | T-44: a specific message per permission, "COARSE only" handled as valid, and a shortcut to system settings. |
| R4 | Panda wider than its building | Test A2 red | Shift to the adjacent building (§7.8), verified by test, not by inspection. |
| R5 | `ImageBitmap` leaks | Memory growth in the Profiler (T-35) | Patch the crater rect (T-16); never regenerate per frame. |
| R6 | Tunnelling after several craters | A cane passing through terrain in a long match | DDA from D-03; test A3 and the measured ceiling. |
| R7 | Art and engine drifting apart | Test A5 red after a regeneration | The art is the source: update `Palette`, never weaken the test. |
| R8 | Spec and code drifting apart | A constant changed without updating `DEVELOPMENT_SPEC.md` | PR review: a change to `G` without a change to the spec is a rejection. |

---

## 9. Out of scope (explicit backlog)

Not part of M0–M7, and not to be started "while we are at it":

- Internet play (local link only: Nearby / RFCOMM).
- Persistent scoreboards, profiles or statistics.
- More than two players.
- A scenario editor.
- Tablet/foldable-specific layouts (they work, but without dedicated optimisation).
- Saving and resuming a match across app runs (M6's resumption is intra-session only).

---

## 10. Immediate next step

Play the debug APK and confirm M2 on a real screen: that is the one part of its DoD this environment cannot check.

After that, **M3** (AI) and **M5** (transport) are both unblocked and independent of each other — M3 only touches `core`, M5 only touches `transport`. D-11 stays open until M5; D-10 is consumed by T-46.
