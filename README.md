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
| [`docs/PRIVACY.md`](docs/PRIVACY.md) | The **privacy policy**, as published on the Play listing. |
| [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md) | The **Play listing**: every field, the graphics, and what still blocks a first upload. |
| [`art/README.md`](art/README.md) | Art delivery notes: inventory, format and every divergence from the brief. |

## Status

**M5 complete, M6 written but unproven.** The game is playable against the computer or against a person on the same device: the delivered sprites, five languages, sound effects and a looping theme, each switchable on its own. Playing across a link is built end to end — protocol, pairing, permissions and reconnection — but no two physical devices have ever run a match, which is the whole of what M6 has left.

`:core` carries the deterministic engine: xorshift64\* RNG, trigonometry tables, scenario generation, physics with segment sampling, the match loop, the AI and the binary protocol. **121 tests** in all; the 104 in `:core` run on Linux and again on macOS, because determinism cannot be verified on a single JVM.

See the [roadmap](docs/ROADMAP.md) for what is left and what still needs a real device.

## Architecture

```
core/       Plain Kotlin JVM — physics, deterministic RNG, scenario, AI, match engine. No Android.
transport/  Android — Nearby Connections, RFCOMM and loopback behind one interface.
app/        Android — Compose, whole-pixel rendering, audio, navigation.
art/, tools/  The art and sound pipelines. See AGENTS.md before touching them.
```

Everything under `art/` is build output: the sprites come from `tools/gen_sprites.py`, the effects and the theme from `tools/gen_sfx.py` and `tools/gen_music.py`. Nothing there is hand-edited, nothing is recorded and nothing is downloaded — regenerating is byte-identical, and the build packages it straight from `art/`.

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
adb install throwing-bambu-0.0.0-dev+e2b80b2-debug-e2b80b2.apk
```

The commit appears twice because the version already carries it. That is what an untagged build looks like: the version comes from `git describe` and from nothing else, the repository carries **no tags**, so every build today is `0.0.0-dev+<sha>` with version code 1 — and `release.yml` refuses to publish that. Past a tag the name reads `throwing-bambu-0.1.0-debug-<sha>.apk`. A release is cut by pushing a tag and by nothing else; see [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md).

To build one locally:

```bash
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

The **release** build already works: R8, resource shrinking and `proguard-rules.pro` are configured, and `release.yml` signs from repository secrets. `./gradlew :app:assembleRelease` produces an unsigned APK on any machine. What is missing is not the build but the key — the four signing secrets are unset — and the on-device half of T-52: confirming R8 has not broken the determinism the engine depends on.

## Legal

Original code and assets, under the MIT licence (see [`LICENSE`](LICENSE)). This project does **not** port code from `GORILLA.BAS` and does not reuse its assets, which belong to Microsoft: game mechanics are not protectable, but code and assets are. The title and the theme are our own.
