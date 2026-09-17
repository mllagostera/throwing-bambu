# Throwing Bambu — Roadmap

Where the project stands and what is left. The [development plan](DEVELOPMENT_PLAN.md)
holds the task-level detail and the record of every decision; the
[specification](DEVELOPMENT_SPEC.md) is the contract this is built against.

**Today:** the game is playable against the computer and against a person on the same
device, with the delivered sprites, in five languages. What is missing is playing against
someone on *another* device.

---

## What you can play right now

Download the `apk-debug` artifact from the latest green CI run and install it.

| | |
|---|---|
| **One player** | Three difficulties. On Hard the AI hits within three turns in 87 % of generated scenarios. |
| **Two players, same device** | Hot-seat, best of five. |
| **Play over Bluetooth** | Built end to end, but unproven: no two devices have run it. |
| **Settings** | Language: English, Español, Català, Français, Deutsch, or the system's. |

A match is a destructible skyline, wind that changes every turn, and a bamboo cane with
real ballistics. Same seed, same shots, same match — on any device.

---

## Done

| Milestone | What it bought | How it is known to work |
|---|---|---|
| **M0** Skeleton | Three modules, version catalog, ktlint + detekt, CI publishing a debug APK | CI green on Linux and macOS |
| **M1** Deterministic core | RNG, trigonometry tables, scenario generation, physics | 38 tests, incl. the nine mandatory ones; calibration measured, not assumed |
| **M2** Engine and rendering | Match loop, events, crater, score; whole-pixel renderer; local hot-seat | Test §15.11: same script and seed, same event sequence twice over |
| **M3** Computer opponent | Coarse search, refinement, per-level noise | Harness over 200 scenarios: 41 % / 73 % / **87 %** hit rate by level |
| **M4** Sprites | Panda, cane, explosion, sun, skyline, logo, all at integer scale | Art packaged from `art/` at build time; a test ties the engine palette to the delivered PNGs |
| **M5** Protocol | Binary codec, transport abstraction, loopback, networked match | Test §15.12: a full match across a link, identical scores, zero divergences |
| *(brought forward)* | Five languages and a settings screen | 29 strings in resources, placeholders verified across all four translations |

**74 tests**, all run on every push, and `:core` runs them twice — on Linux and on macOS —
because determinism cannot be verified on a single JVM.

---

## Left to do

### M6 — Play against another device (~4 d)

The one thing the game cannot do yet. The hard part is already built and tested: the
protocol, the loopback, the networked match and reconnection all run on a plain JVM.
What M6 adds is the radio underneath them and the screens around them.

- ~~`NearbyTransport` over Nearby Connections.~~ Written. The lifecycle it runs on is
  tested on a plain JVM (`LinkLifecycle`); the Nearby calls themselves are the part
  only a device can prove.
- ~~Permission flow, with "COARSE only" treated as a valid grant (D-12).~~ Done.
- ~~Pairing UI: host or guest, peer list, link state.~~ Done, along with the `HELLO` /
  `MATCH_START` handshake that settles the seed, the width and who throws first.
- ~~Reconnection after a drop, by replaying the shot history (D-10).~~ Done: `RESUME`
  asks, `HISTORY` answers, and the engine replays. What is left is only the part that
  needs a radio — noticing the drop and dialling back.
- Adopting the peer's `RESULT` on a divergence (D-11), which needs real latency to be
  worth implementing.

**Done when:** a full match between two physical devices, including a 10 s disconnection
in the middle, with zero divergences.

### M7 — Release shape (~4 d)

- `RfcommTransport`, so the game works on a device without Google Play Services. This is
  committed scope, not a nice-to-have: without it the app simply does not run on those
  devices.
- Audio: throw, explosion, victory, defeat.
- Persistent settings beyond the language: speed multiplier, sound, default difficulty.
- Release build: R8, signing, a signed APK from CI.
- Polish: transitions, empty states, accessibility of the numeric controls.

**Done when:** a full RFCOMM match on a device without GMS, and an installable release
APK.

---

## What still needs a device

Everything below is written and passes CI, but CI has no screen and no radio:

- **The game as it looks and feels.** Flight timing, the crater landing mid-explosion,
  the panda's feet on the roof, nothing blurry.
- **Memory and frame rate** on a low-end phone (§17.5).
- **Anything involving two devices**, which is most of M6.

The engine is a different matter: physics, generation, AI and protocol are pure logic and
are verified here, on every push.

---

## Not planned

Deliberately out of scope, so nobody starts them "while they are at it":

- Internet play. The link is local only: Nearby or RFCOMM.
- Persistent scoreboards, profiles or statistics.
- More than two players.
- A scenario editor.
- Saving a match between app runs. M6's reconnection is within one session.

---

## Open risks

| Risk | Where it would show |
|---|---|
| Floating-point divergence between devices | The divergence counter above zero in a real match. It is a bug, not noise: both sides run the same code over the same inputs. |
| Nearby needs Google Play Services | Hence RFCOMM in M7. |
| Bluetooth permissions on Android 12+ | Denial is common; every path needs a readable error, never a silent `catch`. |
| `ImageBitmap` leaks | Regenerating the terrain per frame instead of per crater sinks low-end devices. Verified with the Profiler in M6/M7. |
