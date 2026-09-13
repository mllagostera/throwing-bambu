"""boom.png -- 8 frames of 32x32, pivot (16, 16) = point of impact.

Hard constraint from the brief: the engine erases a radius-12 circle of terrain
centred on the pivot, and it does so on frame 3. That is why frame 3 is the peak
and its visible radius is 15 px, 3 px beyond the crater: if the fire were
smaller than the hole, the impact would feel weak.

  0-2  expansion, visible radius 4 -> 8 -> 12
  3    peak, radius 15
  4    the core empties out and the edge turns ragged
  5    the ring breaks into six arcs
  6-7  loose fragments burning out

Ramp: 15 white -> 14 yellow -> 12 light red -> 4 red -> transparent. Transitions
are resolved with a checkerboard: the dither offset is 0 or +1 px depending on
the parity of (x+y), never negative, so each frame's maximum radius is exactly
the declared one and never overshoots.
"""

import math

from ega import Canvas, T

CELL = (32, 32)
FRAME_MS = 40
CRATER_RADIUS = 12          # what the engine erases
CRATER_FRAME = 3            # when it erases it
PEAK_RADIUS = 15            # visible radius of the peak frame

CX = CY = 15.5              # the pivot (16,16) falls between pixels

WHITE, YELLOW, LIGHT_RED, RED = 15, 14, 12, 4
HOLLOW = None               # transparent band (core already burnt out)

# Radial ramp per frame, inside out: (outer radius, colour).
_BANDS = {
    0: [(1.5, WHITE), (3.0, YELLOW), (4.0, LIGHT_RED)],
    1: [(2.5, WHITE), (5.0, YELLOW), (7.0, LIGHT_RED), (8.0, RED)],
    2: [(4.0, WHITE), (7.5, YELLOW), (10.0, LIGHT_RED), (12.0, RED)],
    3: [(5.0, WHITE), (9.0, YELLOW), (12.5, LIGHT_RED), (15.0, RED)],
    4: [(3.0, HOLLOW), (6.5, YELLOW), (10.0, LIGHT_RED), (14.0, RED)],
    5: [(6.0, HOLLOW), (9.5, LIGHT_RED), (15.0, RED)],
}

# Frame 4: notches that break the edge. (centre angle, half-width) in degrees;
# they only bite beyond r=9, so the core stays whole.
_NOTCHES = [(22, 5), (67, 4), (112, 6), (158, 3),
            (203, 5), (248, 4), (293, 6), (338, 3)]

# Frame 5: six arcs. (centre angle, half-width, r_inner, r_outer).
_ARCS = [(15, 27, 8.5, 15), (72, 21, 9.0, 14), (133, 28, 8.5, 15),
         (195, 22, 9.0, 14), (254, 26, 8.5, 15), (310, 20, 9.5, 13)]

# Fragment templates. 'X' = body, 'o' = still-hot core, '.' = nothing.
_TEMPLATES = {
    "a": [".XXX.", "XXoXX", "XXoXX", ".XXXX", "..XX."],
    "b": [".XX.", "XoXX", "XXX.", ".XX."],
    "c": [".X.", "XoX", ".X."],
    "d": ["XX", "XX"],
    "e": ["X"],
}

# Frames 6 and 7: (template, angle, radius). Placed by hand: the brief forbids
# random noise, and an evenly spaced ring of fragments reads as a cogwheel
# rather than as fire coming apart.
_FRAGMENTS = {
    6: [("a", 16, 10.5), ("a", 58, 11.5), ("b", 96, 10.0), ("a", 134, 11.0),
        ("b", 172, 12.0), ("a", 212, 10.5), ("b", 252, 11.5), ("a", 292, 10.0),
        ("c", 330, 12.0), ("c", 352, 10.0)],
    7: [("b", 24, 12.5), ("c", 82, 13.0), ("b", 140, 12.0), ("d", 186, 13.5),
        ("c", 232, 12.5), ("b", 296, 13.0), ("d", 342, 13.5)],
}


def _color_at(frame: int, r: float):
    for r_out, col in _BANDS[frame]:
        if r <= r_out:
            return col
    return None


def _is_hollow(frame: int, r: float) -> bool:
    bands = _BANDS[frame]
    return bands[0][1] is HOLLOW and r <= bands[0][0]


def _ang_dist(a: float, b: float) -> float:
    return abs((a - b + 180.0) % 360.0 - 180.0)


def _radial(n: int) -> Canvas:
    """Frames 0-5: concentric bands with checkerboard dithering."""
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            dx, dy = x - CX, y - CY
            r = math.hypot(dx, dy)
            rd = r + (1.0 if (x + y) % 2 else 0.0)
            ang = math.degrees(math.atan2(-dy, dx)) % 360.0
            if n == 4 and rd > 9.0:
                if any(_ang_dist(ang, a) <= w for a, w in _NOTCHES):
                    continue
            if n == 5:
                if not any(ri <= rd <= ro and _ang_dist(ang, a) <= w
                           for a, w, ri, ro in _ARCS):
                    continue
            # The core hole is not dithered: at r=3 the checkerboard reads as
            # noise rather than as a transition.
            col = _color_at(n, r if _is_hollow(n, r) else rd)
            if col is not None:
                c.set(x, y, col)
    return c


def _fragmented(n: int) -> Canvas:
    """Frames 6-7: loose fragments placed one by one."""
    c = Canvas(32, 32)
    for name, ang, r in _FRAGMENTS[n]:
        rows = _TEMPLATES[name]
        h, w = len(rows), len(rows[0])
        px = round(CX + r * math.cos(math.radians(ang)) - (w - 1) / 2.0)
        py = round(CY - r * math.sin(math.radians(ang)) - (h - 1) / 2.0)
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch == ".":
                    continue
                # On frame 6 the fragment core is still light red; on 7
                # everything has cooled to red.
                col = LIGHT_RED if (ch == "o" and n == 6) else RED
                c.set(px + dx, py + dy, col)
    return c


def _frame(n: int) -> Canvas:
    return _radial(n) if n in _BANDS else _fragmented(n)


def frames():
    return [_frame(n) for n in range(8)]


def max_radius(c: Canvas) -> float:
    """Maximum visible radius from the pivot, for verify_assets.py."""
    best = 0.0
    for y in range(c.h):
        for x in range(c.w):
            if c.px[y][x] != T:
                best = max(best, math.hypot(x - CX, y - CY))
    return best
