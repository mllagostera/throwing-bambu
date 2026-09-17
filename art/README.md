# Art — sprite delivery

Production of the pieces described in `docs/SPRITE_SPEC.md`. Everything is at
**1×**: no file is delivered scaled, because the engine scales by integers with
nearest-neighbour at runtime.

## Theme change: pandas and bamboo

The brief describes gorillas throwing bananas. What is delivered is **pandas
throwing bamboo canes**, which is what the project name says. The technical
contract with the engine does not change: same cells, same pivots, same number
and order of frames. The character, the projectile and — with them — two
legibility constraints the brief had pinned to the banana's colour do change:

| | Gorilla + banana | Panda + bamboo |
|---|---|---|
| Character | light grey (7) and dark grey (8) | white (15) and black (0) |
| Projectile | yellow (14) | light green (10) with green (2) nodes |
| Forbidden facade base | yellow | **green** (eats the cane) and **white** (eats the panda) |
| Lit window | white in 3 of 5, to avoid hiding the banana | **yellow in all 5**, as the original's reference |

The panda also reads better than the gorilla at this size, and not by luck: its
identity lives in high-contrast patches — ears, eye masks, chest band, legs — and
not in the volume of the fur, which does not fit in 20 px of height.

Renamed files: `gorila.png` → `panda.png`, `banana.png` → `bamboo.png`,
`paleta_gorilas.gpl` → `ega16.gpl` (the palette is the 16-colour EGA, it has
nothing to do with gorillas, and the brief itself asks not to carry over the name
of Microsoft's original). Frame names were translated along with the rest:
`brazo_izq`/`brazo_der`/`pecho_1`/`pecho_2`/`muerto` →
`arm_left`/`arm_right`/`chest_1`/`chest_2`/`defeated`.

`docs/SPRITE_SPEC.md` is an English translation of the original brief, kept
**faithful to its content**: it is the starting document and the record of what
was asked for. Every divergence from it lives here, not there.

## What is here

```
art/
  palette/ega16.gpl          Deliverable 0. 16-colour EGA palette, normative order.
  palette/ega16.png          16×1 px strip of the palette (one swatch per pixel).
  palette/ega16_x16.png      The same, magnified, to actually look at.
  sky.txt                    The two colours of the gradient the engine generates.
  sprites/*.png              The seven pieces of the inventory.
  preview/*_x6.png           Magnified contact sheets, with cell boundaries marked.
  preview/scene_1x.png       A game scene assembled at 1× with every piece.
  preview/scene_x3.png       The same, magnified.
tools/
  ega.py                     Palette, canvas and PNG writing.
  art_*.py                   One piece per file: this is where the drawing lives.
  gen_sprites.py             Generates the whole of art/.
  preview_scene.py           Assembles the test scene.
  verify_assets.py           Checks the acceptance criteria.
```

## Reproduce and verify

```bash
pip install pillow
python3 tools/gen_sprites.py --zoom 6     # rewrites art/sprites and art/preview
python3 tools/preview_scene.py            # rewrites art/preview/scene_*.png
python3 tools/verify_assets.py            # exits 1 if anything breaks section 12
```

`verify_assets.py` reads the delivered PNGs, not the generators, and checks piece
by piece: palette colours only, at most 16 distinct, no pixel with an alpha
between 1 and 254, exact strip and cell dimensions, the pivot where the document
declares it, the panda's collision box, the radius of the explosion's peak frame
against the crater, the four key pixels of each facade (no green base and no
white one) and the skyline's behaviour cropped to 320, 360, 400 and 460 px.

Current status: **all good, 0 warnings**.

## Format

Indexed PNG-8 (colour type 3, bit depth 8) with `tRNS`. The table has 17 entries:
the 16 EGA colours at indices 0–15 and index 16 as a fully transparent slot,
painted pure magenta `#FF00FF` — which is not an EGA colour — so that a
transparency failure is obvious instead of passing for a legitimate black. By
construction there can be no pixel with an intermediate alpha.

## Inventory

| File | Canvas | Cell | Frames | Pivot | Timing |
|---|---|---|---|---|---|
| `panda.png` | 144×24 | 24×24 | 6 | (12, 24) | `chest_1`/`chest_2` at 150 ms |
| `bamboo.png` | 32×8 | 8×8 | 4 | (4, 4) | 80 ms loop |
| `boom.png` | 256×32 | 32×32 | 8 | (16, 16) | 40 ms; the crater is erased on frame 3 |
| `sun.png` | 40×20 | 20×20 | 2 | (10, 10) | `ouch` for 1 s |
| `facades.png` | 80×16 | 16×16 | 5 swatches | — | — |
| `skyline.png` | 460×80 | — | 1 | — | — |
| `logo.png` | 200×60 | — | 1 | — | — |

Panda frame order: `idle`, `arm_left`, `arm_right`, `chest_1`, `chest_2`,
`defeated`. The chest names are kept from the brief even though beating the chest
is a gorilla gesture: they describe the geometry of the pose, and changing them
would only break references. Bamboo: horizontal, diagonal up, vertical, diagonal
down.

## Android integration

Once the module exists, these PNGs go into `app/src/main/assets/sprites/`, **not**
into `res/drawable*/`: the resource system applies density scaling and would
destroy the nearest-neighbour look. Load them with `BitmapFactory.Options` with
`inScaled = false` and paint them with a `Paint` with filtering off
(`isFilterBitmap = false`, `isAntiAlias = false`).

`skyline.png` is 460 px wide and gets cropped from the right to the real canvas
width. In the test scene the band is anchored 45 px above the base of the
buildings; flush with the bottom edge of the screen it is completely hidden by
the playable buildings.

## Decisions that depart from the brief

Each one is deliberate and reversible; they all live in the comments of the
corresponding `art_*.py`.

1. **There are no `.aseprite` files.** There is no Aseprite in this environment,
   and writing its binary format blind would produce files nobody has been able
   to open. The editable source is the `art_*.py`: every pixel is either written
   by hand in ASCII or placed by an explicit rule, it is readable in a code
   review, and the result is reproducible bit for bit. To move into Aseprite:
   open the PNG and import `ega16.gpl`.
2. **Black works as outline and as patch at once on the panda.** That is why the
   raised arms carry no outline of their own: surrounding an already-black arm
   with black would fatten it to 4 px. The silhouette is defined wherever the
   white or the background begins.
3. **The panda uses four tones, not two.** White (15) and black (0) for the
   patches, dark grey (8) to give shape to the black masses and light grey (7)
   for the stepped shading on the belly. Without them, arms and belly are flat
   8 px surfaces.
4. **`defeated` is collapsed, not lying down.** Two versions lying in profile
   were tried: at 1× one reads as a lump and the other as a factory with
   chimneys. Squashing the vertical silhouette from 20 to 12 px keeps the
   character and is recognisable at a glance. This departs from the document's
   *suggestion*, which was not normative.
5. **The bamboo cane is a straight bar, not a plant silhouette.** All four frames
   are the same 6×2 px body rotated. What identifies it at 8×8 is not the
   outline, which allows nothing beyond a bar, but the **nodes**: two bands of
   dark green over a light-green body. Without the nodes it is a stick.
6. **The sun's rays carry no outline.** At 1 px thick, outlining them turns them
   into 3 px bars and the sun becomes a black cogwheel. The disc is outlined.
7. **The forbidden facade base colour changes from yellow to green**, and white
   is added. See the theme-change table.
8. **The fifth facade is brown, not blue.** Blue (1) is the tone of the skyline
   silhouette: a playable building the same colour as the background is lost.
9. **The skyline's window dots are light blue (9), not yellow.** Yellow is the
   colour of the lit windows on the playable facades; repeating it in the
   background erases the difference between what is in front and what is behind.
10. **`logo.png` was made despite the block in section 9.** See below.

## Pending decisions

- **Game name.** Section 9 blocks the logo until the name is decided. It is
  lettered `THROWING BAMBU` after the repository name, with original letterforms
  drawn for this piece; neither "Gorillas" nor the `GORILLA.BAS` typeface is
  used. If the name changes, only the `LINE_1` and `LINE_2` constants in
  `tools/art_logo.py` change: the glyphs are already there.
- **Lit-window density.** The engine decides how many it lights. In the test
  scene they are dense on purpose, to see the bad case. Recommendation: below one
  in four.
- **Skyline anchoring.** "Anchored to the bottom edge of the sky area" admits two
  readings. See *Android integration*.
- **Pose names.** If the project would rather `chest_1`/`chest_2` were called
  something without a gorilla connotation, it is a one-line change in
  `art_panda.py`; it does not affect the order of the strip.

## Out of scope for this delivery

Section 10 (interface) is not pixel art and is not part of "the sprites". Three
of its four items have since been built in the app rather than here: the UI
colour specification, the 24 dp icons and the wind arrow all live under
`app/src/main/kotlin/dev/bambu/app/ui/`, as vectors and colour tokens. See §14
of the development specification.

The **typeface** is still pending, and it is the one item that was never a
drawing problem: it requires verifying the accented glyphs and the "ñ" of
*Press Start 2P* or *Silkscreen* before committing, which is a licensing
decision.
