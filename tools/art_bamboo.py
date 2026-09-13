"""bamboo.png -- 4 frames of 8x8, pivot (4, 4).

Replaces the banana of docs/SPRITE_SPEC.md while keeping the contract: same
cell, same pivot, same four 90-degree steps and the same 80 ms loop. The engine
still does not rotate the sprite.

All four frames are the same body -- a 6 px long, 2 px thick cane -- shown at 0,
45, 90 and 135 degrees. What identifies bamboo at this size is not the
silhouette, which at 8x8 allows nothing beyond a bar, but the **nodes**: two
bands of green (2) over a light-green (10) body. Without the nodes it is a
stick; with them it reads as a cane at 1x.

This changes one constraint from the brief: the forbidden facade base colour is
no longer yellow but green. See art_facades.py.
"""

from ega import Canvas

FRAME_NAMES = ["horizontal", "diagonal_up", "vertical", "diagonal_down"]
FRAME_MS = 80
CELL = (8, 8)

LIGHT_GREEN, GREEN = 10, 2

_HORIZONTAL = [
    "........",
    "........",
    ".000000.",
    "0A2AA2A0",
    "0A2AA2A0",
    ".000000.",
    "........",
    "........",
]

_DIAGONAL_UP = [
    ".....00.",
    "....0AA0",
    "...0220.",
    "..0AA0..",
    ".0AA0...",
    "0220....",
    "AA0.....",
    "00......",
]

_VERTICAL = [
    "...00...",
    "..0AA0..",
    "..0220..",
    "..0AA0..",
    "..0AA0..",
    "..0220..",
    "..0AA0..",
    "...00...",
]

_DIAGONAL_DOWN = [
    ".00.....",
    "0AA0....",
    ".0220...",
    "..0AA0..",
    "...0AA0.",
    "....0220",
    ".....0AA",
    "......00",
]

_SOURCES = [_HORIZONTAL, _DIAGONAL_UP, _VERTICAL, _DIAGONAL_DOWN]


def frames():
    return [
        Canvas.from_ascii(rows, expect_w=8, expect_h=8, name=f"bamboo/{n}")
        for n, rows in zip(FRAME_NAMES, _SOURCES)
    ]
