"""Shared art-production core: EGA palette, ASCII canvases and PNG writing.

Rules this module makes impossible to break (see docs/SPRITE_SPEC.md, section 0):
  - closed 16-colour palette: any index outside 0..15 is an error;
  - binary alpha: PNGs are written indexed with a single transparent index, so
    there is no representation for a partial alpha value;
  - uniform cell: the constructors validate dimensions as they build.
"""

from __future__ import annotations

# The 16-colour EGA palette, in the order the brief declares (section 1).
EGA = [
    (0x00, 0x00, 0x00),  # 0  black
    (0x00, 0x00, 0xAA),  # 1  blue
    (0x00, 0xAA, 0x00),  # 2  green
    (0x00, 0xAA, 0xAA),  # 3  cyan
    (0xAA, 0x00, 0x00),  # 4  red
    (0xAA, 0x00, 0xAA),  # 5  magenta
    (0xAA, 0x55, 0x00),  # 6  brown
    (0xAA, 0xAA, 0xAA),  # 7  light grey
    (0x55, 0x55, 0x55),  # 8  dark grey
    (0x55, 0x55, 0xFF),  # 9  light blue
    (0x55, 0xFF, 0x55),  # 10 light green
    (0x55, 0xFF, 0xFF),  # 11 light cyan
    (0xFF, 0x55, 0x55),  # 12 light red
    (0xFF, 0x55, 0xFF),  # 13 light magenta
    (0xFF, 0xFF, 0x55),  # 14 yellow
    (0xFF, 0xFF, 0xFF),  # 15 white
]

EGA_NAMES = [
    "black", "blue", "green", "cyan", "red", "magenta", "brown", "light grey",
    "dark grey", "light blue", "light green", "light cyan", "light red",
    "light magenta", "yellow", "white",
]

# Index reserved for "unpainted". It is not a palette colour: it goes into the
# PNG tRNS chunk and is therefore 100 % transparent, never a partial alpha.
T = 16

# Colour of the transparent slot in the PNG table. Pure magenta is NOT part of
# the EGA palette, so if some tool ignores tRNS the failure is obvious instead
# of passing for a legitimate black.
TRANSPARENT_KEY = (0xFF, 0x00, 0xFF)

# Character map used by the ASCII canvases in art_*.py.
#   '0'..'9','A'..'F' -> palette index     '.' -> transparent
_CHARS = "0123456789ABCDEF"


def idx_of_char(ch: str) -> int:
    if ch == ".":
        return T
    up = ch.upper()
    if up not in _CHARS:
        raise ValueError(f"character {ch!r} is outside the palette (use 0-9, A-F or '.')")
    return _CHARS.index(up)


class Canvas:
    """Grid of palette indices. Origin is top-left."""

    def __init__(self, w: int, h: int, fill: int = T):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    # -- construction -----------------------------------------------------
    @classmethod
    def from_ascii(cls, rows, expect_w=None, expect_h=None, name="<anon>"):
        rows = [r for r in rows]
        h = len(rows)
        if expect_h is not None and h != expect_h:
            raise ValueError(f"{name}: {h} rows, expected {expect_h}")
        w = len(rows[0]) if rows else 0
        if expect_w is not None:
            w = expect_w
        for y, row in enumerate(rows):
            if len(row) != w:
                raise ValueError(f"{name}: row {y} is {len(row)} px, expected {w}")
        c = cls(w, h)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                c.px[y][x] = idx_of_char(ch)
        return c

    # -- access -----------------------------------------------------------
    def get(self, x: int, y: int) -> int:
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return T

    def set(self, x: int, y: int, i: int) -> None:
        if not (0 <= i <= 16):
            raise ValueError(f"index {i} is outside the closed palette")
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = i

    def fill_rect(self, x0, y0, x1, y1, i) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, i)

    def blit(self, other: "Canvas", dx: int, dy: int, skip_transparent=True) -> None:
        for y in range(other.h):
            for x in range(other.w):
                v = other.px[y][x]
                if skip_transparent and v == T:
                    continue
                self.set(x + dx, y + dy, v)

    def bbox(self):
        """Smallest box containing opaque pixels, or None if the canvas is empty."""
        xs, ys = [], []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] != T:
                    xs.append(x)
                    ys.append(y)
        if not xs:
            return None
        return (min(xs), min(ys), max(xs), max(ys))

    def colors(self):
        return {v for row in self.px for v in row if v != T}

    def to_ascii(self):
        out = []
        for row in self.px:
            out.append("".join("." if v == T else _CHARS[v] for v in row))
        return out


def hstrip(frames, cell_w: int, cell_h: int, name="<anon>") -> Canvas:
    """Assemble a horizontal strip out of frames sharing one cell size."""
    for n, f in enumerate(frames):
        if (f.w, f.h) != (cell_w, cell_h):
            raise ValueError(
                f"{name}: frame {n} is {f.w}x{f.h}, the cell is {cell_w}x{cell_h}"
            )
    strip = Canvas(cell_w * len(frames), cell_h)
    for n, f in enumerate(frames):
        strip.blit(f, n * cell_w, 0, skip_transparent=False)
    return strip


def save_png(canvas: Canvas, path: str) -> None:
    """Write a PNG-8 with a single fully transparent index."""
    from PIL import Image

    img = Image.new("P", (canvas.w, canvas.h), T)
    pal = []
    for rgb in EGA:
        pal += list(rgb)
    pal += list(TRANSPARENT_KEY)
    pal += [0, 0, 0] * (256 - len(EGA) - 1)
    img.putpalette(pal)
    img.putdata([v for row in canvas.px for v in row])
    # No optimize: it would reindex the table and move the transparent index.
    img.save(path, transparency=T)
