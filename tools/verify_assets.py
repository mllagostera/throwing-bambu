#!/usr/bin/env python3
"""Check art/ against the acceptance criteria of the brief (section 12).

It reads the delivered PNGs, not the generators: what gets validated is the file
the engine is going to consume.

    python3 tools/verify_assets.py        # exits 1 if anything fails

Piece by piece it checks:
  - palette colours only, at most 16 distinct;
  - no pixel with an alpha between 1 and 254;
  - exact strip and cell dimensions;
  - the declared pivot falls where the document says;
  - and the specific constraints: the panda's collision box, the radius of the
    explosion's peak frame, the facades' key pixels (no green base, which would
    eat the cane, and no white one, which would eat the panda) and the cropping
    behaviour of the skyline.
"""

import math
import os
import sys

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ART = os.path.join(ROOT, "art")
SPRITES = os.path.join(ART, "sprites")

EGA = {
    (0x00, 0x00, 0x00), (0x00, 0x00, 0xAA), (0x00, 0xAA, 0x00), (0x00, 0xAA, 0xAA),
    (0xAA, 0x00, 0x00), (0xAA, 0x00, 0xAA), (0xAA, 0x55, 0x00), (0xAA, 0xAA, 0xAA),
    (0x55, 0x55, 0x55), (0x55, 0x55, 0xFF), (0x55, 0xFF, 0x55), (0x55, 0xFF, 0xFF),
    (0xFF, 0x55, 0x55), (0xFF, 0x55, 0xFF), (0xFF, 0xFF, 0x55), (0xFF, 0xFF, 0xFF),
}

YELLOW = (0xFF, 0xFF, 0x55)
WHITE = (0xFF, 0xFF, 0xFF)
GREENS = {(0x00, 0xAA, 0x00), (0x55, 0xFF, 0x55)}

# file -> (width, height, cell width, cell height, frame count)
INVENTORY = {
    "panda.png":   (144, 24, 24, 24, 6),
    "bamboo.png":  (32, 8, 8, 8, 4),
    "boom.png":    (256, 32, 32, 32, 8),
    "sun.png":     (40, 20, 20, 20, 2),
    "facades.png": (80, 16, 16, 16, 5),
    "skyline.png": (460, 80, None, None, 1),
    "logo.png":    (200, 60, None, None, 1),
}

failures = []
warnings = []


def fail(msg):
    failures.append(msg)


def warn(msg):
    warnings.append(msg)


def load(name):
    img = Image.open(os.path.join(SPRITES, name)).convert("RGBA")
    return img, img.load()


def opaque(px, w, h, x0=0, y0=0, x1=None, y1=None):
    x1 = w - 1 if x1 is None else x1
    y1 = h - 1 if y1 is None else y1
    out = []
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r, g, b, a = px[x, y]
            if a:
                out.append((x, y, (r, g, b)))
    return out


def bbox(points):
    if not points:
        return None
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def common(name):
    """Palette, binary alpha and dimensions. Returns (img, px)."""
    path = os.path.join(SPRITES, name)
    if not os.path.exists(path):
        fail(f"{name}: missing")
        return None, None
    img, px = load(name)
    w, h, cw, ch, n = INVENTORY[name]
    if (img.width, img.height) != (w, h):
        fail(f"{name}: canvas {img.width}x{img.height}, declared {w}x{h}")
    if cw and img.width != cw * n:
        fail(f"{name}: {img.width} px is not {n} cells of {cw}")

    alphas, colours = set(), set()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            alphas.add(a)
            if a:
                colours.add((r, g, b))
    partial = sorted(a for a in alphas if 0 < a < 255)
    if partial:
        fail(f"{name}: intermediate alpha {partial}")
    outside = colours - EGA
    if outside:
        fail(f"{name}: colours outside the palette {sorted(outside)}")
    if len(colours) > 16:
        fail(f"{name}: {len(colours)} unique colours, 16 is the maximum")
    print(f"  {name:<13} {img.width:>3}x{img.height:<3} "
          f"{len(colours)} colours  alpha {sorted(alphas)}")
    return img, px


def check_panda():
    img, px = common("panda.png")
    if not img:
        return
    names = ["idle", "arm_left", "arm_right", "chest_1", "chest_2", "defeated"]
    for n, nom in enumerate(names):
        ox = n * 24
        pts = opaque(px, img.width, img.height, ox, 0, ox + 23, 23)
        if not pts:
            fail(f"panda/{nom}: empty frame")
            continue
        x0, y0, x1, y1 = bbox(pts)
        x0, x1 = x0 - ox, x1 - ox
        # The pivot (12,24) rests on the rooftop: every frame must touch y=23.
        if y1 != 23:
            fail(f"panda/{nom}: does not rest on the bottom edge (max y {y1})")
        # The collision box is 16x20 at x=4..19: nothing of the body may leave
        # it except arms and paws, which the brief allows into the margin.
        inside = [p for p in pts if 4 <= p[0] - ox <= 19 and p[1] >= 4]
        if len(inside) < len(pts) * 0.6:
            fail(f"panda/{nom}: most of the body falls outside the 16x20 box")
        if nom == "idle" and (x0, y0, x1, y1) != (4, 4, 19, 23):
            fail(f"panda/idle: box {x0},{y0},{x1},{y1}, "
                 f"must be exactly 4,4,19,23 (16x20)")
    print("  panda: 6 frames resting on the pivot (12,24)")


def _centred(name, pts, ox, cx, cy, tol, label):
    x0, y0, x1, y1 = bbox(pts)
    mx, my = ((x0 - ox) + (x1 - ox)) / 2.0, (y0 + y1) / 2.0
    if abs(mx - cx) > tol or abs(my - cy) > tol:
        fail(f"{name}/{label}: centre ({mx:.1f},{my:.1f}), "
             f"pivot ({cx},{cy}) with tolerance {tol}")


def check_bamboo():
    img, px = common("bamboo.png")
    if not img:
        return
    for n in range(4):
        ox = n * 8
        pts = opaque(px, img.width, img.height, ox, 0, ox + 7, 7)
        _centred("bamboo", pts, ox, 3.5, 3.5, 0.5, f"f{n}")
    print("  bamboo: 4 frames centred on the pivot (4,4)")


def check_boom():
    img, px = common("boom.png")
    if not img:
        return
    radii = []
    for n in range(8):
        ox = n * 32
        pts = opaque(px, img.width, img.height, ox, 0, ox + 31, 31)
        r = max(math.hypot((x - ox) - 15.5, y - 15.5) for x, y, _ in pts)
        radii.append(r)
        _centred("boom", pts, ox, 15.5, 15.5, 1.5, f"f{n}")
    if not (radii[0] < radii[1] < radii[2] < radii[3]):
        fail(f"boom: frames 0-3 do not grow monotonically: "
             f"{[round(r, 1) for r in radii[:4]]}")
    if radii[3] < 14.0:
        fail(f"boom/f3: radius {radii[3]:.1f}, the peak must reach 15")
    if radii[3] < 12 + 2.5:
        fail(f"boom/f3: radius {radii[3]:.1f} does not clear the crater "
             f"(radius 12) by about 3 px; the impact would feel weak")
    print("  boom: radii " + " ".join(f"{r:.1f}" for r in radii) +
          "  (crater 12 on frame 3)")


def check_sun():
    img, px = common("sun.png")
    if not img:
        return
    for n, nom in enumerate(["normal", "ouch"]):
        ox = n * 20
        pts = opaque(px, img.width, img.height, ox, 0, ox + 19, 19)
        _centred("sun", pts, ox, 9.5, 9.5, 0.5, nom)
    print("  sun: 2 frames centred on the pivot (10,10)")


def check_facades():
    img, px = common("facades.png")
    if not img:
        return
    seen = []
    for n in range(5):
        ox = n * 16
        key = [px[ox + i, 0][:3] for i in range(4)]
        base, on, off, outline = key
        for c in key:
            if c not in EGA:
                fail(f"facades/{n}: key pixel outside the palette {c}")
        if base in GREENS:
            fail(f"facades/{n}: green base; the bamboo cane would vanish")
        if base == WHITE:
            fail(f"facades/{n}: white base; the panda would vanish")
        if base in (on, off, outline):
            fail(f"facades/{n}: the base does not contrast with window or outline")
        if on == off:
            fail(f"facades/{n}: lit and unlit windows are the same colour")
        seen.append(base)
    if len(set(seen)) != 5:
        fail("facades: some variants share a base colour")
    n_white = sum(1 for n in range(5) if px[n * 16 + 1, 0][:3] == WHITE)
    if n_white > 2:
        warn(f"facades: {n_white} variants light their windows in white; "
             f"they camouflage the panda, which is white")
    n_yellow = sum(1 for n in range(5) if px[n * 16 + 1, 0][:3] == YELLOW)
    print(f"  facades: 5 distinct bases, {n_yellow} with a yellow window, "
          f"{n_white} with a white one")


def check_skyline():
    img, px = common("skyline.png")
    if not img:
        return
    tops = []
    for x in range(img.width):
        col = [y for y in range(img.height) if px[x, y][3]]
        if not col:
            fail(f"skyline: column {x} is empty; the background reads with holes")
            tops.append(img.height)
        else:
            tops.append(min(col))
    # It must work cropped from the right at any point between 320 and 460.
    for width in (320, 360, 400, 460):
        crop = tops[:width]
        if max(crop) - min(crop) < 8:
            fail(f"skyline: cropped to {width} px the silhouette goes flat")
    above60 = sum(1 for y in range(60) for x in range(img.width) if px[x, y][3])
    pct = 100.0 * above60 / (60 * img.width)
    if pct > 3.0:
        fail(f"skyline: {pct:.1f} % of the top 60 px is occupied; "
             f"they must stay reasonably clear")
    flat = longest = 1
    for i in range(1, len(tops)):
        flat = flat + 1 if tops[i] == tops[i - 1] else 1
        longest = max(longest, flat)
    if longest > 30:
        warn(f"skyline: a {longest} px flat plateau; it reads as a single block")
    print(f"  skyline: profile {min(tops)}-{max(tops)}, "
          f"{pct:.1f} % above y=60, longest plateau {longest} px")


def check_logo():
    img, px = common("logo.png")
    if not img:
        return
    pts = opaque(px, img.width, img.height)
    x0, y0, x1, y1 = bbox(pts)
    if (x1 - x0 + 1) > img.width - 4 or (y1 - y0 + 1) > img.height - 4:
        warn("logo: the wordmark reaches the canvas edge, with no margin")
    print(f"  logo: box {x0},{y0}-{x1},{y1} in {img.width}x{img.height}")


def check_extras():
    gpl = os.path.join(ART, "palette", "ega16.gpl")
    if not os.path.exists(gpl):
        fail("art/palette/ega16.gpl is missing")
    else:
        n = sum(1 for l in open(gpl) if l[:1].isdigit() or l[:1] == " ")
        if n != 16:
            fail(f"ega16.gpl: {n} colours, there must be 16")
    sky = os.path.join(ART, "sky.txt")
    if not os.path.exists(sky):
        fail("art/sky.txt is missing")
    else:
        txt = open(sky).read()
        if "top=" not in txt or "bottom=" not in txt:
            fail("sky.txt: the top= and bottom= keys are missing")
    print("  ega16.gpl and sky.txt present")


def main():
    print("Verifying art/ against the acceptance criteria\n")
    check_panda()
    check_bamboo()
    check_boom()
    check_sun()
    check_facades()
    check_skyline()
    check_logo()
    check_extras()
    print()
    for w in warnings:
        print(f"WARN  {w}")
    for f in failures:
        print(f"FAIL  {f}")
    if failures:
        print(f"\n{len(failures)} failure(s).")
        return 1
    print(f"\nAll good ({len(warnings)} warning(s)).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
