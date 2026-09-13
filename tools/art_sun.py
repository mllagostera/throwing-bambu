"""sun.png -- 2 frames of 20x20, pivot (10, 10).

A 12 px disc centred on the pivot, with a black outline. The rays are solid
yellow with no outline: at 1 px thick, outlining them turns them into 3 px bars
and the sun becomes a black cogwheel.

Frame 1 (`ouch`): mouth open in an O, eyes reduced to dots and the rays pulled
back by 1 px. The engine holds it for 1 s when a projectile crosses the sun; the
projectile does not stop.
"""

from ega import Canvas

FRAME_NAMES = ["normal", "ouch"]
OUCH_MS = 1000
CELL = (20, 20)

YELLOW, BLACK = 14, 0

# Disc: width of each row, from y=4 to y=15. Geometric centre at (9.5, 9.5).
_DISC_WIDTH = [4, 8, 10, 12, 12, 12, 12, 12, 12, 10, 8, 4]
_DISC_TOP = 4


def _disc_cols(row):
    """Columns (inclusive) the disc occupies on that row, or None."""
    i = row - _DISC_TOP
    if not (0 <= i < len(_DISC_WIDTH)):
        return None
    w = _DISC_WIDTH[i]
    x0 = 10 - w // 2
    return x0, x0 + w - 1


def _disc(c: Canvas):
    inside = set()
    for y in range(20):
        cols = _disc_cols(y)
        if cols:
            for x in range(cols[0], cols[1] + 1):
                inside.add((x, y))
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c.set(x, y, BLACK if edge else YELLOW)


def _rays(c: Canvas, length: int, diagonals):
    """Four cardinal rays `length` px long plus single-pixel diagonals."""
    for n in range(length):
        c.set(9, 3 - n, YELLOW)             # north
        c.set(10, 3 - n, YELLOW)
        c.set(9, 16 + n, YELLOW)            # south
        c.set(10, 16 + n, YELLOW)
        c.set(3 - n, 9, YELLOW)             # west
        c.set(3 - n, 10, YELLOW)
        c.set(16 + n, 9, YELLOW)            # east
        c.set(16 + n, 10, YELLOW)
    for (x, y) in diagonals:
        c.set(x, y, YELLOW)


def _normal() -> Canvas:
    c = Canvas(20, 20)
    _rays(c, 4, [(3, 3), (2, 2), (16, 3), (17, 2),
                 (3, 16), (2, 17), (16, 16), (17, 17)])
    _disc(c)
    # 2x2 eyes and a smiling mouth with the corners one row higher.
    for x in (7, 8, 11, 12):
        c.set(x, 8, BLACK)
        c.set(x, 9, BLACK)
    c.set(7, 11, BLACK)
    c.set(12, 11, BLACK)
    for x in range(8, 12):
        c.set(x, 12, BLACK)
    return c


def _ouch() -> Canvas:
    c = Canvas(20, 20)
    _rays(c, 3, [(3, 3), (16, 3), (3, 16), (16, 16)])
    _disc(c)
    # Eyes as dots, set wider apart, and a 4x3 mouth open in an O.
    c.set(7, 8, BLACK)
    c.set(12, 8, BLACK)
    for x in range(8, 12):
        c.set(x, 11, BLACK)
        c.set(x, 13, BLACK)
    c.set(8, 12, BLACK)
    c.set(11, 12, BLACK)
    return c


def frames():
    return [_normal(), _ouch()]
