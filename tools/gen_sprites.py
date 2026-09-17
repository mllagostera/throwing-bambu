#!/usr/bin/env python3
"""Generate every art deliverable into art/.

    python3 tools/gen_sprites.py            # writes art/
    python3 tools/gen_sprites.py --zoom 6   # plus magnified contact sheets

Requires Pillow (pip install pillow). The PNGs come out indexed (PNG-8) with a
single fully transparent index: by construction there can be no pixel with an
intermediate alpha.
"""

import argparse
import os

import art_bamboo
import art_boom
import art_facades
import art_icon
import art_logo
import art_panda
import art_skyline
import art_sun
import ega
from ega import Canvas, hstrip, save_png

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ART = os.path.join(ROOT, "art")
SPRITES = os.path.join(ART, "sprites")
ICON = os.path.join(ART, "icon")
PALETTE = os.path.join(ART, "palette")

# Sky gradient: the engine generates it in code, it is not drawn. Only the pair
# of colours is delivered, both taken from the EGA palette (1 blue on top, 9
# light blue at the bottom), so the blue-1 skyline silhouette keeps separating
# from the horizon.
SKY_TOP = "#0000AA"
SKY_BOTTOM = "#5555FF"


def _gpl() -> str:
    lines = ["GIMP Palette", "Name: EGA 16", "Columns: 16",
             "# Closed palette for the project. The order is normative: each",
             "# colour's index is the one the art_*.py files use.", "#"]
    for i, (r, g, b) in enumerate(ega.EGA):
        lines.append(f"{r:3d} {g:3d} {b:3d}\t{i:2d} {ega.EGA_NAMES[i]}")
    return "\n".join(lines) + "\n"


def _circle_mask(c: Canvas) -> Canvas:
    """What a circular launcher mask leaves of the icon.

    Not a deliverable: it exists so the corners can be judged by looking rather
    than by trusting the check in verify_assets.py.
    """
    m = Canvas(c.w, c.h)
    r = c.w / 2.0
    for y in range(c.h):
        for x in range(c.w):
            if ((x + 0.5) - r) ** 2 + ((y + 0.5) - r) ** 2 <= r * r:
                m.set(x, y, c.px[y][x])
    return m


def _zoom(c: Canvas, factor: int, grid=None) -> Canvas:
    """Magnify with nearest neighbour. `grid` = cell width, to mark frame
    boundaries with a 1 px line."""
    z = Canvas(c.w * factor, c.h * factor)
    for y in range(c.h):
        for x in range(c.w):
            v = c.px[y][x]
            if v == ega.T:
                continue
            for dy in range(factor):
                for dx in range(factor):
                    z.set(x * factor + dx, y * factor + dy, v)
    if grid:
        for n in range(1, c.w // grid):
            for y in range(z.h):
                if y % 2 == 0:
                    z.set(n * grid * factor, y, 5)
    return z


PIECES = []


def piece(name, canvas, cell=None):
    PIECES.append((name, canvas, cell))
    return canvas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zoom", type=int, default=0,
                    help="also write magnified contact sheets into art/preview/")
    args = ap.parse_args()

    os.makedirs(SPRITES, exist_ok=True)
    os.makedirs(PALETTE, exist_ok=True)
    os.makedirs(ICON, exist_ok=True)

    piece("panda", hstrip(art_panda.frames(), 24, 24, "panda"), 24)
    piece("bamboo", hstrip(art_bamboo.frames(), 8, 8, "bamboo"), 8)
    piece("boom", hstrip(art_boom.frames(), 32, 32, "boom"), 32)
    piece("sun", hstrip(art_sun.frames(), 20, 20, "sun"), 20)
    piece("facades", hstrip(art_facades.frames(), 16, 16, "facades"), 16)
    piece("skyline", art_skyline.build())
    piece("logo", art_logo.build())

    for name, canvas, _ in PIECES:
        path = os.path.join(SPRITES, name + ".png")
        save_png(canvas, path)
        print(f"{path}  {canvas.w}x{canvas.h}  {len(canvas.colors())} colours")

    # The launcher icon is a resource, not a sprite, so it stays out of
    # art/sprites/: the SyncSpritesTask in app/build.gradle.kts copies that
    # directory wholesale into the app's assets, and a launcher icon has no
    # business being packaged there as well.
    icon = art_icon.build()
    save_png(icon, os.path.join(ICON, "icon.png"))
    print(f"{os.path.join(ICON, 'icon.png')}  {icon.w}x{icon.h}  "
          f"{len(icon.colors())} colours")

    # Deliverable 0: the palette.
    with open(os.path.join(PALETTE, "ega16.gpl"), "w") as f:
        f.write(_gpl())
    swatch = Canvas(16, 1)
    for i in range(16):
        swatch.set(i, 0, i)
    save_png(swatch, os.path.join(PALETTE, "ega16.png"))
    save_png(_zoom(swatch, 16), os.path.join(PALETTE, "ega16_x16.png"))

    with open(os.path.join(ART, "sky.txt"), "w") as f:
        f.write("# Sky gradient. The engine interpolates it in code.\n"
                f"top={SKY_TOP}\nbottom={SKY_BOTTOM}\n")

    if args.zoom:
        prev = os.path.join(ART, "preview")
        os.makedirs(prev, exist_ok=True)
        for name, canvas, cell in PIECES:
            save_png(_zoom(canvas, args.zoom, cell),
                     os.path.join(prev, f"{name}_x{args.zoom}.png"))
        save_png(_zoom(icon, args.zoom),
                 os.path.join(prev, f"icon_x{args.zoom}.png"))
        save_png(_zoom(_circle_mask(icon), args.zoom),
                 os.path.join(prev, f"icon_masked_x{args.zoom}.png"))
        print(f"magnified contact sheets in {prev}")


if __name__ == "__main__":
    main()
