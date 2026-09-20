#!/usr/bin/env python3
"""Generate the Play Store graphics into art/store/.

    python3 tools/gen_store.py

Play asks for exact pixel sizes, which is the one thing the rest of this
pipeline refuses to deliver: hard rule 3 in AGENTS.md says never ship scaled
art, because the engine scales by integers at runtime and pre-scaled art
double-scales and goes soft. Nothing here reaches the engine. These two files
are consumed by Play's web front end at a size Play dictates, so they are
written pre-scaled on purpose — and still by whole-number nearest-neighbour,
so every pixel stays square and the same size as its neighbours.

The sizes are chosen so that the scale factor is a whole number:

    icon      24 x 24  -> x21 = 504, padded to 512 x 512
    feature  512 x 250 ->  x2        = 1024 x 500

Neither file may carry transparency: Play rejects an icon with an alpha
channel and composites the feature graphic onto an unknown background.

The screenshots Play also wants are NOT here and cannot be. They have to come
from the real renderer on a real device; art/preview/scene_1x.png is a test
image assembled by hand from the sprites, not the game, and using it would be
showing people something they cannot play.
"""

import os

from PIL import Image

import art_bamboo
import art_boom
import art_facades
import art_icon
import art_logo
import art_panda
import art_skyline
import art_sun
import ega
from ega import T
from gen_sprites import ART, SKY_BOTTOM, SKY_TOP

# The icon's own background: index 1, the same blue the art calls "ground". The
# padding has to be this exact colour or the seam would show as a border.
GROUND = 1

ICON_ZOOM = 21
ICON_SIZE = 512

FEATURE_W, FEATURE_H = 512, 250
FEATURE_ZOOM = 2

# Above the base of the buildings, not flush with the bottom edge: flush with it
# the silhouette is completely hidden behind them.
SKYLINE_BASE = FEATURE_H - 55


def _hex(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def _paste(img, canvas, ox, oy):
    """Draw a Canvas onto an RGB image, honouring the transparent index."""
    px = img.load()
    for y in range(canvas.h):
        for x in range(canvas.w):
            v = canvas.px[y][x]
            if v == T:
                continue
            ix, iy = ox + x, oy + y
            if 0 <= ix < img.width and 0 <= iy < img.height:
                px[ix, iy] = ega.EGA[v]


def _sky(img):
    top, bottom = _hex(SKY_TOP), _hex(SKY_BOTTOM)
    px = img.load()
    for y in range(img.height):
        t = y / (img.height - 1)
        col = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
        for x in range(img.width):
            px[x, y] = col


def _buildings(img, swatches, ground):
    """The skyline the engine would generate, from the four key pixels of each swatch."""
    px = img.load()
    widths = [58, 71, 52, 66, 60, 55, 48, 63]
    # Taller than the ones preview_scene.py uses. That scene is a 400 x 200
    # playfield; this is a 512 x 250 poster, and at these proportions the short
    # buildings left the middle third of the canvas as empty sky.
    heights = [120, 88, 155, 100, 138, 82, 115, 95]
    roofs = []
    x = 0
    n = 0
    while x < img.width:
        w, h = widths[n % len(widths)], heights[n % len(heights)]
        m = swatches[n % len(swatches)]
        base, on, off, outline = (m.get(0, 0), m.get(1, 0), m.get(2, 0), m.get(3, 0))
        top = ground - h
        for yy in range(top, ground):
            for xx in range(x, min(x + w, img.width)):
                on_edge = xx in (x, x + w - 1) or yy == top
                px[xx, yy] = ega.EGA[outline if on_edge else base]
        # 3x4 windows with a 2 px gap, the same rule preview_scene.py uses.
        for wy in range(top + 4, ground - 5, 6):
            for wx in range(x + 3, x + w - 5, 5):
                lit = ((wx // 5) * 7 + (wy // 6) * 3 + n) % 5 < 2
                for dy in range(4):
                    for dx in range(3):
                        if wx + dx < img.width:
                            px[wx + dx, wy + dy] = ega.EGA[on if lit else off]
        roofs.append((x + w // 2, top))
        x += w
        n += 1
    return roofs


def icon() -> Image.Image:
    """512 x 512, opaque, every pixel a whole 21 x 21 block."""
    art = art_icon.build()
    small = Image.new("RGB", (art.w, art.h), ega.EGA[GROUND])
    _paste(small, art, 0, 0)
    big = small.resize((art.w * ICON_ZOOM, art.h * ICON_ZOOM), Image.NEAREST)

    # 24 x 21 is 504, so 8 px are left over: 4 a side. Centring it keeps the
    # piece on whole pixels; scaling to 512 outright would not, and the icon
    # already carries its own margin of ground around the head.
    canvas = Image.new("RGB", (ICON_SIZE, ICON_SIZE), ega.EGA[GROUND])
    off = (ICON_SIZE - big.width) // 2
    canvas.paste(big, (off, off))
    return canvas


def feature() -> Image.Image:
    """1024 x 500: a match in progress, with the title over it."""
    img = Image.new("RGB", (FEATURE_W, FEATURE_H))
    _sky(img)

    # The delivered silhouette is 460 px wide and this canvas is 512, so it
    # repeats. It is drawn to read cropped at any width (AGENTS.md, rule 8),
    # which is what makes the seam invisible.
    sky = art_skyline.build()
    band = ega.Canvas(FEATURE_W, sky.h)
    for y in range(sky.h):
        for x in range(FEATURE_W):
            band.px[y][x] = sky.px[y][x % sky.w]
    _paste(img, band, 0, SKYLINE_BASE - sky.h)

    _paste(img, art_sun.frames()[0], FEATURE_W - 86, 18)

    roofs = _buildings(img, art_facades.frames(), FEATURE_H)

    # Two pandas, because that is what the game is. The left one is mid-throw,
    # the right one waiting its turn.
    panda = art_panda.frames()
    _paste(img, panda[1], roofs[1][0] - 12, roofs[1][1] - 24)
    _paste(img, panda[0], roofs[6][0] - 12, roofs[6][1] - 24)

    # The cane in flight. Sampled off a real parabola rather than placed by eye:
    # the game's whole subject is a trajectory, and a row of canes that does not
    # curve the way one would advertises the wrong game.
    start = (roofs[1][0] + 10, roofs[1][1] - 20)
    hit = (roofs[4][0], roofs[4][1])
    cane = art_bamboo.frames()
    # The apex is held under the title on purpose. A taller, prettier arc puts
    # canes through the lettering, and the title is the one thing that has to
    # survive every crop Play applies.
    for n in range(1, 6):
        t = n / 6.0
        x = start[0] + (hit[0] - start[0]) * t
        y = start[1] + (hit[1] - start[1]) * t - 34 * 4 * t * (1 - t)
        _paste(img, cane[n % len(cane)], round(x) - 4, round(y) - 4)

    # Centred on the roof edge, not above it: half the blast inside the building
    # is what a hit looks like. Clear of it, it reads as a sun.
    _paste(img, art_boom.frames()[3], hit[0] - 16, hit[1] - 16)

    # Centred, and in the top third: Play crops this graphic differently on
    # different surfaces and overlays a play button on some of them. What has to
    # survive every crop is the title.
    logo = art_logo.build()
    _paste(img, logo, (FEATURE_W - logo.w) // 2, 16)

    return img.resize((FEATURE_W * FEATURE_ZOOM, FEATURE_H * FEATURE_ZOOM), Image.NEAREST)


def main():
    out = os.path.join(ART, "store")
    os.makedirs(out, exist_ok=True)

    for name, img in (("icon_512.png", icon()),
                      ("feature_1024x500.png", feature())):
        path = os.path.join(out, name)
        # RGB, so there is no alpha channel for Play to reject.
        img.convert("RGB").save(path)
        print(f"{path}  {img.width} x {img.height}")


if __name__ == "__main__":
    main()
