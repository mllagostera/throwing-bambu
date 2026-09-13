"""facades.png -- 5 cells of 16x16. These are not sprites: they are colour swatches.

The engine reads only four pixels per cell:
    (0,0) facade base       (1,0) lit window
    (2,0) unlit window      (3,0) building outline

The rest of the cell is free. Here it previews the combination by assembling a
slice of building out of those four colours, so that opening the PNG shows what
the engine is going to generate without compiling anything. Row 0 from x=4
onwards is filled with the base colour so the four key pixels are never
overwritten.

Legibility constraint, rewritten when the theme changed. The brief forbade a
yellow base because the projectile was a banana; the projectile is now a bamboo
cane, so **what is forbidden is green**: no base may be green (2) or light green
(10), or the cane vanishes as it passes in front.

And the other way round: all five variants light their windows in yellow again,
as the original's reference asked. With the banana, three had to be switched to
white because they swallowed it; with green bamboo, yellow no longer gets in the
way, and white has become the bad colour: the panda is white, and a facade
speckled with 3x4 white windows camouflages it. Check it by building
tools/preview_scene.py.

The fifth variant is brown, not blue: blue (1) is the tone of the skyline
silhouette, and a playable building the same colour as the background is lost.
"""

from ega import Canvas

CELL = (16, 16)

# (name, base, lit window, unlit window, outline)
VARIANTS = [
    ("cyan",    3, 14, 1, 0),
    ("red",     4, 14, 0, 8),
    ("grey",    8, 14, 1, 0),
    ("magenta", 5, 14, 1, 0),
    ("brown",   6, 14, 1, 0),
]

WHITE = 15
GREENS = (2, 10)

_WIN_X = (2, 6, 10)     # windows are 3 px wide
_WIN_Y = (2, 7)         # windows are 4 px tall


def _cell(n: int, base: int, on: int, off: int, outline: int) -> Canvas:
    c = Canvas(16, 16, fill=base)
    # Building outline: sides and bottom edge.
    for y in range(16):
        c.set(0, y, outline)
        c.set(15, y, outline)
    for x in range(16):
        c.set(x, 15, outline)
    # Window grid, lit and unlit in a checkerboard.
    for fy, wy in enumerate(_WIN_Y):
        for fx, wx in enumerate(_WIN_X):
            col = on if (fx + fy + n) % 2 == 0 else off
            c.fill_rect(wx, wy, wx + 2, wy + 3, col)
    # The four key pixels are written last: the contract wins over the preview.
    c.set(0, 0, base)
    c.set(1, 0, on)
    c.set(2, 0, off)
    c.set(3, 0, outline)
    for x in range(4, 16):
        c.set(x, 0, base)
    return c


def frames():
    out = []
    for n, (name, base, on, off, outline) in enumerate(VARIANTS):
        if base in GREENS:
            raise ValueError(f"{name}: green base, the bamboo cane would vanish")
        if base == WHITE:
            raise ValueError(f"{name}: white base, the panda would vanish")
        out.append(_cell(n, base, on, off, outline))
    return out
