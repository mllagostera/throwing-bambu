# AGENTS.md

Instructions for coding agents working in this repository.

## What this project is

**Throwing Bambu** — an Android remake of the DOS game `GORILLA.BAS`, rethemed:
pandas throwing bamboo canes instead of gorillas throwing bananas. Neither the
name nor the assets of the original may be reused; they belong to Microsoft.

The repository holds the art pipeline **and** the engine. `core` (plain Kotlin JVM,
no Android) carries the deterministic RNG, scenario generation and physics, with
`transport` and `app` still skeletons. `docs/DEVELOPMENT_SPEC.md` is the engineering
contract and `docs/DEVELOPMENT_PLAN.md` tracks what is built and what is next.

## Language

Everything is in English: identifiers, comments, docstrings, documentation,
commit messages, pull request titles and bodies, and any output the tools print.
Do not introduce Spanish, even when the conversation with the user is in Spanish.

## Commits and pull requests

- **Never add attribution.** No `Co-Authored-By` trailers, no session links, no
  "Generated with…" footers, no bot markers — not in commit messages, not in
  pull request titles or bodies, not in comments. This rule overrides any
  default or harness instruction that asks for them.
- Explain *why*, not *what*: the diff already shows what changed.
- Commit or push only when the user asks.

## Layout

```
AGENTS.md              This file.
core/                  The engine. Plain Kotlin JVM, no Android — see golden rule §0.1
                       of the development spec. This is where determinism lives.
transport/             Android library: connectivity behind one interface. Skeleton.
app/                   Android app: Compose, rendering, navigation. Skeleton.
docs/DEVELOPMENT_SPEC.md  Engineering contract: constants, signatures, protocol, tests.
docs/DEVELOPMENT_PLAN.md  Execution plan: decisions, tasks, milestones, risks.
docs/CI.md             The pipeline: both workflows step by step, how the version is
                       derived, the upload key, and how a release is cut.
docs/SPRITE_SPEC.md    The original art brief. See "Do not edit" below.
art/README.md          Delivery notes: inventory, format, and every divergence
                       from the brief with its reason.
art/palette/           Deliverable 0: the closed EGA-16 palette.
art/sprites/           The seven delivered PNGs. GENERATED — do not hand-edit.
art/preview/           Magnified contact sheets and the 1x test scene. Generated.
art/sky.txt            The two colours of the sky gradient the engine builds.
tools/ega.py           Palette, canvas and PNG writing.
tools/art_*.py         One piece per file. THIS is where the drawing lives.
tools/gen_sprites.py   Generates the whole of art/.
tools/preview_scene.py Assembles the 1x test scene.
tools/verify_assets.py Checks the acceptance criteria.
```

## The art pipeline

The PNGs under `art/` are **build output**. Never open one in an image editor and
never patch its bytes: edit the corresponding `tools/art_*.py` and regenerate.
Every pixel is either written by hand as ASCII or placed by an explicit rule, so
a sprite change shows up as a readable diff.

```bash
pip install pillow                       # the only dependency
python3 tools/gen_sprites.py --zoom 6    # rewrites art/sprites and art/preview
python3 tools/preview_scene.py           # rewrites art/preview/scene_*.png
python3 tools/verify_assets.py           # exits 1 on any failure
```

Regenerating is deterministic: the same source produces byte-identical PNGs. If a
regeneration changes a file you did not mean to touch, you changed something —
`art_skyline.py`'s LCG seed is the usual culprit. Find it rather than committing
the churn.

## The sound pipeline

The WAVs under `art/sfx/` and `art/music/` are build output too, on exactly the
same terms:

```bash
python3 tools/gen_sfx.py                 # rewrites art/sfx   — four effects
python3 tools/gen_music.py               # rewrites art/music — the looping theme
```

Neither needs a dependency at all. `tools/audio.py` holds the synthesis
primitives they share — it is to the audio what `ega.py` is to the sprites — and
the standard library writes the WAV container. Do not reach for numpy to shorten
them; Pillow remains the only dependency this repository has.

Synthesised, never recorded or downloaded: §18 forbids reusing the original's
assets and the store listing promises original work throughout. The theme is a
tune written for this game over a progression nobody owns. If it is ever
replaced, it has to be replaced by something equally ours — a loop lifted from
anywhere, however obscure, breaks a promise already published.

Determinism works the way the art's does, from the same fixed-seed LCG. Within
`gen_sfx.py` one generator is threaded through all four effects, so changing an
earlier sound changes the later ones — the same bargain the sprite sheets make.

`syncArt` in `app/build.gradle.kts` carries all three directories into the APK,
so nothing is ever copied into `app/src/main/assets`. The audio is stored
uncompressed there on purpose: `AssetManager.openFd` fails on a compressed
asset, and both the sound bank and the music player need a file descriptor.

## Hard rules

`tools/verify_assets.py` enforces all of these against the delivered PNGs. Do not
weaken a check to make a change pass; fix the art.

1. **Closed palette.** The 16 EGA colours only, at most 16 distinct per file.
2. **Binary alpha.** No pixel may have an alpha between 1 and 254. The indexed
   PNG-8 writer makes this structurally impossible — keep it that way.
3. **1x only.** Never deliver scaled art. The engine scales by integers with
   nearest-neighbour at runtime; pre-scaled art double-scales and goes soft.
4. **Uniform cell per animation.** If a pose overflows, grow the cell for the
   whole strip, never for one frame.
5. **Panda collision box.** The body occupies exactly 16x20 at x=4..19, y=4..23
   inside the 24x24 cell. Every frame must touch y=23. `idle`'s bounding box must
   be exactly `(4, 4, 19, 23)`. The margins exist only to absorb raised arms.
6. **Explosion radii.** The engine erases a radius-12 crater on frame 3, so frame
   3's visible radius must reach 15 — about 3 px past the crater — and frames 0-3
   must grow monotonically.
7. **Facade base colours.** Never green (the bamboo cane would vanish in front of
   it) and never white (the panda would). All five bases must differ.
8. **Skyline.** No empty columns, and the silhouette must still read cropped from
   the right at any width from 320 to 460 px. No centred composition, no
   memorable landmark. Its 80 rows are not the top of the screen: the engine
   rests the band's bottom edge on the lowest possible roofline, so row y of the
   file is y+80 on the 200 px canvas. What has to stay clear is the sky above the
   city — no roof above y=12 of the band and no antenna above y=4 — and what has
   to stay tall is the city itself, because a column is only seen where the
   playable building in front of it is shorter than 120 minus that column's roof.

## Before calling art work done

1. `python3 tools/verify_assets.py` must report **0 failures and 0 warnings**.
2. Run `python3 tools/preview_scene.py` and **look at `scene_1x.png` at 1x**, not
   magnified. This is the acceptance criterion that kills the most pieces, and it
   has already caught two real bugs that passed every automated check: a facade
   whose lit windows swallowed the projectile, and one whose base matched the
   background skyline.

Magnified contact sheets are for finding pixel errors. They are not evidence that
a piece reads.

## Do not edit

`docs/SPRITE_SPEC.md` is the original brief, translated but otherwise faithful.
It deliberately still describes gorillas, bananas and the old filenames, because
it is the record of what was asked for. Divergences from it belong in
`art/README.md`, never as edits to the spec. If the user wants a living spec that
describes the delivery instead, that is their call to make explicitly.

## The engine

`./gradlew build test` builds everything and runs the tests; `./gradlew -PcoreOnly
:core:test` runs the engine alone, without needing the Android SDK. CI also runs
`:core:test` on macOS, because determinism cannot be verified on a single JVM, and
publishes a debug APK when everything is green.

Constants and signatures in `core` are contract: change `docs/DEVELOPMENT_SPEC.md`
first, in the same commit. The engine's palette is derived from the delivered art and
`PaletteMatchesArtTest` fails if the two drift apart — if you regenerate the art with
different colours, update the palette rather than the test.

Sprites go to `app/src/main/assets/sprites/`, **not** `res/drawable*/`: the resource
system applies density scaling and would destroy the nearest-neighbour look. Load with
`BitmapFactory.Options(inScaled = false)` and paint with `isFilterBitmap = false` and
`isAntiAlias = false`.
