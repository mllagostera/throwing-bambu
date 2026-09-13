#!/usr/bin/env python3
"""Assemble a fake game scene at 1x with every piece in it.

The acceptance criterion that kills the most pieces (section 12 of the brief) is
"it reads correctly at 100 % zoom in a real screenshot of the game". With no
engine yet, this is the closest thing: sky gradient in code, cropped skyline,
buildings generated from the swatches in facades.png, pandas on the rooftops,
bamboo in flight and an explosion. It is written at 1x and also magnified, but
the one that counts is the 1x.

    python3 tools/preview_scene.py
"""

import os

from PIL import Image

import art_bamboo
import art_boom
import art_facades
import art_panda
import art_skyline
import art_sun
import ega
from ega import T
from gen_sprites import ART, SKY_BOTTOM, SKY_TOP

WIDTH, HEIGHT = 400, 200
GROUND = HEIGHT
# The skyline band is anchored above the base of the buildings, not at the
# bottom edge of the screen: flush with the bottom it is completely hidden.
SKYLINE_BASE = HEIGHT - 45


def _hex(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def _paste(img, canvas, ox, oy):
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
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        col = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
        for x in range(WIDTH):
            px[x, y] = col


def _buildings(img, swatches):
    """Generate buildings the way the engine would: from the four key pixels only."""
    px = img.load()
    widths = [58, 71, 52, 66, 60, 55, 48]
    heights = [96, 62, 128, 74, 110, 58, 88]
    roofs = []
    x = 0
    for n, (w, h) in enumerate(zip(widths, heights)):
        m = swatches[n % len(swatches)]
        base, on, off, outline = (m.get(0, 0), m.get(1, 0), m.get(2, 0), m.get(3, 0))
        top = GROUND - h
        for yy in range(top, GROUND):
            for xx in range(x, min(x + w, WIDTH)):
                on_edge = xx in (x, x + w - 1) or yy == top
                px[xx, yy] = ega.EGA[outline if on_edge else base]
        # 3x4 windows with a 2 px gap.
        for wy in range(top + 4, GROUND - 5, 6):
            for wx in range(x + 3, x + w - 5, 5):
                lit = ((wx // 5) * 7 + (wy // 6) * 3 + n) % 5 < 2
                for dy in range(4):
                    for dx in range(3):
                        if wx + dx < WIDTH:
                            px[wx + dx, wy + dy] = ega.EGA[on if lit else off]
        roofs.append((x + w // 2, top))
        x += w
        if x >= WIDTH:
            break
    return roofs


def main():
    img = Image.new("RGB", (WIDTH, HEIGHT))
    _sky(img)

    sky = art_skyline.build()
    crop = ega.Canvas(WIDTH, sky.h)
    for y in range(sky.h):
        for x in range(WIDTH):
            crop.px[y][x] = sky.px[y][x]
    _paste(img, crop, 0, SKYLINE_BASE - sky.h)

    _paste(img, art_sun.frames()[0], 186, 14)

    roofs = _buildings(img, art_facades.frames())

    panda = art_panda.frames()
    _paste(img, panda[1], roofs[1][0] - 12, roofs[1][1] - 24)
    _paste(img, panda[0], roofs[4][0] - 12, roofs[4][1] - 24)
    _paste(img, panda[5], roofs[6][0] - 12, roofs[6][1] - 24)

    _paste(img, art_bamboo.frames()[1], 150, 60)
    _paste(img, art_bamboo.frames()[3], 196, 46)
    _paste(img, art_boom.frames()[3], roofs[3][0] - 16, roofs[3][1] - 8)

    prev = os.path.join(ART, "preview")
    os.makedirs(prev, exist_ok=True)
    one = os.path.join(prev, "scene_1x.png")
    img.save(one)
    img.resize((WIDTH * 3, HEIGHT * 3), Image.NEAREST).save(
        os.path.join(prev, "scene_x3.png"))
    print(one)


if __name__ == "__main__":
    main()
