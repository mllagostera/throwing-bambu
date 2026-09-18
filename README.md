# Throwing Bambu

A turn-based artillery game for Android: two pandas, a destructible skyline and wind. Inspired by the mechanics of the classic artillery genre; **original code and assets**, with nothing ported from `GORILLA.BAS` (see §18 of the specification).

## Documentation

| Document | Contents |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Rules for anyone — human or agent — working in this repository. |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Where the project stands, what is left, and what still needs a device. Start here. |
| [`docs/DEVELOPMENT_SPEC.md`](docs/DEVELOPMENT_SPEC.md) | The technical **contract**: constants, signatures, protocol, mandatory tests. Changed here before it is changed in code. |
| [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) | The **execution plan**: prior decisions, task breakdown, milestones M0–M7, test traceability, risks. |
| [`docs/CI.md`](docs/CI.md) | The **pipeline**: what runs on a pull request, on `main` and on a tag; where the version comes from; the upload key; how to cut a release. |
| [`docs/SPRITE_SPEC.md`](docs/SPRITE_SPEC.md) | The original art brief. Not edited — see AGENTS.md. |
| [`art/README.md`](art/README.md) | Art delivery notes: inventory, format and every divergence from the brief. |

## Status

**M5 complete.** The game is playable against the computer or against a person on the same device, with the delivered sprites and in five languages. Playing across a link is M6 — the protocol is built and tested, the radio is not.

`:core` carries the deterministic engine: xorshift64\* RNG, trigonometry tables, scenario generation, physics with segment sampling, the match loop, the AI and the binary protocol. **74 tests**, run on Linux and again on macOS, because determinism cannot be verified on a single JVM.

See the [roadmap](docs/ROADMAP.md) for what is left and what still needs a real device.

## Architecture

```
core/       Plain Kotlin JVM — physics, deterministic RNG, scenario, AI, match engine. No Android.
transport/  Android — Nearby Connections, RFCOMM and loopback behind one interface.
app/        Android — Compose, whole-pixel rendering, audio, navigation.
art/, tools/  The art pipeline. See AGENTS.md before touching it.
```

Three modes (one player, two local, Bluetooth) share a single match loop; only the source of each shot changes.

## Build and test

```bash
./gradlew build test        # full build (needs the Android SDK)
./gradlew ktlintCheck detekt
./gradlew ktlintFormat      # style autofix
```

`:core` is plain Kotlin JVM and **does not need the Android SDK**. To work on the engine alone — physics, RNG, scenario, AI — on a machine without one:

```bash
./gradlew -PcoreOnly :core:test
```

Requirements: JDK 17 or newer. The Gradle version is pinned by the wrapper.

## APK

Every push builds a debug APK, **only if the tests and static analysis pass**. Download it from *Actions* → the run → the **`apk-debug`** artifact, named `throwing-bambu-<version>-debug-<sha>.apk`. Kept for 14 days.

It is signed with Android's debug key, so it installs as is:

```bash
adb install throwing-bambu-0.1.0-debug-<sha>.apk
```

To build one locally:

```bash
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

The **release** APK needs its own keystore and is not built yet: it arrives in M7 together with R8 and the signing configuration (T-52 in the plan).

## Legal

Original code and assets, under the MIT licence (see [`LICENSE`](LICENSE)). This project does **not** port code from `GORILLA.BAS` and does not reuse its assets, which belong to Microsoft: game mechanics are not protectable, but code and assets are. The title and the theme are our own.
