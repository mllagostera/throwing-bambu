# Gorillas for Android — art brief

Document for sprite production. All measurements are in **logical pixels at 1×**.
The game scales by integers with nearest-neighbour at runtime, so **no art is
delivered at 2× or 3×**: delivering it scaled would cause a double scaling and
destroy the sharpness.

> **Translator's note.** This is an English translation of the original Spanish
> brief (`ESPEC_SPRITES.md`), kept verbatim in content: it is the starting
> document and the record of what was asked for. The delivery diverges from it in
> several places — most visibly, the characters are pandas throwing bamboo canes
> rather than gorillas throwing bananas. Every divergence is listed and justified
> in `art/README.md`; nothing has been edited out of this file to hide one.

---

## 0. Technical constraints — read before drawing

1. **Strict pixel art.** No antialiasing, no soft gradients, no blurred shadows.
2. **Binary alpha.** Every pixel is either fully opaque or fully transparent. No
   50 % opacity: the collision mask and the integer scaling will not tolerate it.
3. **Closed palette** (§1). No colour outside it. If a piece needs a new colour,
   it gets discussed and added to this document; it does not get added on the fly.
4. **Uniform cell per animation.** Every frame of a given sprite shares exact
   dimensions. If a pose overflows, the cell grows for the whole animation, not
   for one frame.
5. **Delivery format:** PNG-8 with an indexed palette + transparency, or PNG-24
   with binary alpha. Plus the source `.aseprite` for each piece.

---

## 1. Palette

16-colour EGA. It is the palette of the original and it is what gives the retro
read without tipping into parody.

| # | Name | Hex |
|---|---|---|
| 0 | Black | `#000000` |
| 1 | Blue | `#0000AA` |
| 2 | Green | `#00AA00` |
| 3 | Cyan | `#00AAAA` |
| 4 | Red | `#AA0000` |
| 5 | Magenta | `#AA00AA` |
| 6 | Brown | `#AA5500` |
| 7 | Light grey | `#AAAAAA` |
| 8 | Dark grey | `#555555` |
| 9 | Light blue | `#5555FF` |
| 10 | Light green | `#55FF55` |
| 11 | Light cyan | `#55FFFF` |
| 12 | Light red | `#FF5555` |
| 13 | Light magenta | `#FF55FF` |
| 14 | Yellow | `#FFFF55` |
| 15 | White | `#FFFFFF` |

**Deliverable 0:** `paleta_gorilas.gpl` + `paleta_gorilas.aseprite` with these 16
colours in this order. Everything else is drawn with it loaded as an indexed
palette.

With 16 colours, the technique for volume is **dithering**, not gradients.
Checkerboard and stair (2:1 chequer) patterns, never random noise.

---

## 2. Inventory and deliverables

Each animation is delivered as **one horizontal strip** in a single PNG. No mixed
atlas: it keeps both production and consumption simple.

| File | Full canvas | Cell | Frame count |
|---|---|---|---|
| `gorila.png` | 144 × 24 | 24 × 24 | 6 |
| `banana.png` | 32 × 8 | 8 × 8 | 4 |
| `boom.png` | 256 × 32 | 32 × 32 | 8 |
| `sol.png` | 40 × 20 | 20 × 20 | 2 |
| `skyline.png` | 460 × 80 | — | 1 |
| `fachadas.png` | 80 × 16 | 16 × 16 | 5 |
| `logo.png` | 200 × 60 | — | 1 |

---

## 3. `gorila.png` — 6 frames of 24 × 24

**Pivot: (12, 24)** — horizontal centre, bottom edge. That point rests exactly on
the rooftop pixel.

The actual body must occupy **16 px wide × 20 px tall**, horizontally centred and
resting on the bottom edge of the cell. The 4 px left over at the top and the
4 px on each side exist **only** to absorb raised arms without moving the pivot.
Do not use them to make the body bigger: the engine collides against a 16 × 20
rectangle, and a gorilla bigger than its collision box feels unfair.

| # | Name | Description |
|---|---|---|
| 0 | `idle` | Resting pose. Arms down, close to the body. It is the frame on screen 95 % of the time: this is where the character's identity is decided. |
| 1 | `brazo_izq` | Left arm extended upwards. Used while the projectile travels to the left. |
| 2 | `brazo_der` | Right arm extended upwards. Mirror of the previous one, but **drawn by hand**: an automatic flip gives the symmetry away and looks dead. |
| 3 | `pecho_1` | Chest beat, fists up. Victory celebration. |
| 4 | `pecho_2` | Chest beat, fists against the torso. Alternates with 3 at 150 ms. |
| 5 | `muerto` | Defeated. Suggestion: lying down, arms extended, inside the same cell and resting on the same bottom edge. |

Style notes: broad-shouldered silhouette and small head, which is what makes a
gorilla legible at 20 px tall. Two body tones (7 and 8 from the palette) plus
black for the internal outline. The face needs at most 3 pixels of information;
do not attempt more.

---

## 4. `banana.png` — 4 frames of 8 × 8

**Pivot: (4, 4)**, geometric centre.

Rotation in 4 steps of 90°: horizontal, diagonal up, vertical, diagonal down. The
engine **does not rotate the sprite**: it loops through the frames at 80 ms. That
is why all four must read as the same object rotating, not as four different
bananas.

Yellow (14) for the body, brown (6) or black (0) at the tips. Nothing else fits
in 8 × 8.

---

## 5. `boom.png` — 8 frames of 32 × 32

**Pivot: (16, 16)**, geometric centre, which lines up with the exact point of
impact.

This piece has a hard constraint: the engine erases a **radius-12 circle** of
terrain centred on the pivot, and it does so on **frame 3**.

- Frames 0–2: expansion. Visible radius growing from ~4 to ~12 px.
- **Frame 3: peak.** Visible radius **15 px** — it must clear the crater by about
  3 px. If the fire is smaller than the hole, the impact reads as weak and fake.
- Frames 4–7: dissipation. The fire comes apart into fragments that burn out, not
  a circle shrinking uniformly.

Colour ramp: white (15) → yellow (14) → light red (12) → red (4) → transparent.
Transitions between tones are resolved with dithering, not with invented
intermediate tones.

---

## 6. `sol.png` — 2 frames of 20 × 20

**Pivot: (10, 10)**.

| # | Name | Description |
|---|---|---|
| 0 | `normal` | Smiling sun with rays. Yellow (14) with an outline. |
| 1 | `ouch` | Mouth open in an "O", eyes as dots, rays slightly pulled back. |

The sun switches to `ouch` for 1 s when a banana passes through it, and the
banana **does not stop**. It is a nod to the original and one of the things people
remember about the game; it is worth the 20 minutes it costs.

---

## 7. `fachadas.png` — 5 cells of 16 × 16

These are not sprites: they are **colour swatches** that the engine reads in order
to paint procedurally generated buildings.

Each 16 × 16 cell defines one facade variant and contains, at fixed positions:

- **Pixel (0, 0):** facade base colour.
- **Pixel (1, 0):** **lit** window colour.
- **Pixel (2, 0):** **unlit** window colour.
- **Pixel (3, 0):** building outline/edge colour.
- Rest of the cell: free, ignored by the engine. Use it to preview how the
  combination looks.

Five variants with good contrast between them. Reference from the original: dark
cyan, red, grey and magenta facades, with windows in yellow (lit) and dark blue
(unlit).

**Legibility constraint:** the banana is yellow. No facade may use yellow as its
base colour, or the projectile will disappear as it passes in front. The lit
window may be yellow, because it measures 3 × 4 px.

---

## 8. `skyline.png` — 460 × 80

Background city silhouette, behind the playable buildings. **A single layer, no
parallax.**

- It gets cropped to the real canvas width (320–460). The design must work
  cropped from the right at any point: **no centred composition and no unique,
  memorable elements** that show up sometimes and sometimes not.
- Flat silhouette in a single dark tone (1 or 8) with scattered window dots. No
  volume.
- The band is anchored to the bottom edge of the sky area; the top 60 px of the
  canvas must stay reasonably clear for the sun and for high trajectories.

The sky gradient is generated by the engine in code; **do not draw it**. Deliver
a `cielo.txt` instead, with two hex colours: top and bottom.

---

## 9. `logo.png` — 200 × 60

Game title for the menu screen. Pixel art, same palette.

**Do not use the name "Gorillas" or the typeface of the original.** The code and
assets of `GORILLA.BAS` belong to Microsoft. The final name gets decided before
this piece; until then, do not start it.

---

## 10. Interface — outside the logical canvas

The UI is **not** scaled pixel art. It is drawn in native dp with Compose,
because text 200 px tall is illegible on a 1080p screen.

What is needed from Design here:

- **Typeface:** a pixel font legible at 14 sp with support for accents and "ñ".
  Recommended for their free licence and Latin-1 coverage: *Press Start 2P* (OFL)
  or *Silkscreen* (OFL). Verify the accented glyphs before committing.
- **Wind arrow:** vector SVG, not pixel art. It scales horizontally according to
  `|wind|` (0–10). Deliver the base stroke and the colour.
- **UI colour specification:** panel background, slider accent colour, active and
  inactive text colour. Derived from the EGA palette but it may be desaturated so
  it does not compete with the game.
- **Icons:** mute, settings, back, Bluetooth. 24 dp, 2 dp stroke.

---

## 11. Recommended production order

1. Palette (`paleta_gorilas.gpl`) — it unblocks everything else.
2. `fachadas.png` + `cielo.txt` — they let development stop using rectangles.
3. `gorila.png` frames 0, 1, 2 — with just these three the game is presentable.
4. `banana.png` + `boom.png` — the pair that changes the feel of impact the most.
5. `sol.png`, `gorila.png` frames 3–5.
6. `skyline.png`.
7. Typeface, icons, wind arrow.
8. `logo.png`.

Point 3 is the minimum delivery that unblocks development milestone M4.
Everything before it is blocking; everything after it is incremental.

---

## 12. Acceptance criteria

A piece is considered done when:

- It uses palette colours exclusively (verifiable with a unique-colour count ≤ 16).
- It has no pixel with an alpha between 1 and 254.
- Every frame in the strip has exactly the declared cell dimensions.
- The declared pivot falls where this document says (verify by overlaying a guide).
- It reads correctly at 100 % zoom in a real screenshot of the game, not just
  magnified in the editor.

That last point is the one that kills the most pieces. Always review at 1× before
calling something finished.
