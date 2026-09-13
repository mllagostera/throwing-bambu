"""panda.png -- 6 frames of 24x24, pivot (12, 24).

Replaces the gorilla of docs/SPRITE_SPEC.md. The geometric contract is kept
intact -- 24x24 cell, pivot (12,24), 16x20 body inside x=4..19 / y=4..23, six
frames in the same order -- and so are the pose names; only the character
changes.

A panda reads better than a gorilla at this size, and not by luck: its identity
lives in high-contrast patches -- ears, eye masks, chest band, legs -- and not in
the volume of the fur, which does not fit in 20 px of height. That is why the
tone assignment is inverted with respect to the gorilla: the body is white (15),
the patches black (0), and the greys are support -- dark grey (8) to give shape
to the black masses and light grey (7) for the shading on the belly.

Black works as outline and as patch at the same time, so the raised arms carry
no outline of their own: they would become 4 px black bands. The silhouette is
defined wherever the white or the background begins.
"""

from ega import Canvas

FRAME_NAMES = ["idle", "arm_left", "arm_right", "chest_1", "chest_2", "defeated"]

# Suggested duration per frame, in ms. The brief only fixes the chest beat.
FRAME_MS = {"chest_1": 150, "chest_2": 150}

CELL_W = CELL_H = 24
BODY_X, BODY_Y = 4, 4            # corner of the 16x20 rectangle
BODY_W, BODY_H = 16, 20

BLACK, LIGHT_GREY, DARK_GREY, WHITE = 0, 7, 8, 15

# --- parts ----------------------------------------------------------------

# Head, 10x7, at (7,4). It is as wide as the torso: a big head relative to the
# body is part of the panda silhouette, the opposite of the gorilla, where the
# brief asked for a small head and broad shoulders.
_HEAD = [
    ".00....00.",     # ears
    "0000000000",     # the ears merge into the top of the skull
    "0FFFFFFFF0",
    "0F00FF00F0",     # eye masks: two 2 px patches with a white bridge
    "0F00FF00F0",
    "0FFF00FFF0",     # muzzle
    "00FFFFFF00",
]

# Shoulder row, 12 px wide, at (6,11). Black: it is the band across the panda's
# chest, and what separates the white head from the white belly.
_SHOULDERS = "000000000000"

# Torso, 10 px wide, at (7,12). Black band on top, white belly with a stepped
# light-grey shadow that gives it bulk without introducing a fourth tone.
_TORSO = [
    "0000000000",
    "0000000000",
    "0FFFFFFFF0",
    "0FFFFFFFF0",
    "0FFFFFFF70",
    "0FFFFFFF70",
    "0FFFFFF770",
    "0FFFFFF770",
    "0FFFFF7770",
]

_LEGS = [
    "0000..0000",
    "0000..0000",
    "0000000000",     # contact with the rooftop
]

# Hanging arm, 3 px wide, the left one at (4,12). Black with a dark-grey line so
# it is not a flat hole; the paw is solid.
_ARM_LEFT = [".00", "080", "080", "080", "080", "080", "000", "000", ".00"]
_ARM_RIGHT = ["00.", "080", "080", "080", "080", "080", "000", "000", "00."]


def _base(c: Canvas, head_dx=0):
    c.blit(Canvas.from_ascii(_HEAD, 10, 7, "panda/head"), 7 + head_dx, 4)
    c.blit(Canvas.from_ascii([_SHOULDERS], 12, 1, "panda/shoulders"), 6, 11)
    c.blit(Canvas.from_ascii(_TORSO, 10, 9, "panda/torso"), 7, 12)
    c.blit(Canvas.from_ascii(_LEGS, 10, 3, "panda/legs"), 7, 21)


def _hanging_arm(c: Canvas, left=True):
    part = _ARM_LEFT if left else _ARM_RIGHT
    c.blit(Canvas.from_ascii(part, 3, 9, "panda/arm"), 4 if left else 17, 12)


def _raised_arm(c: Canvas, ladder, paw_rows):
    """Draw a raised arm.

    `ladder` is the list of arm spans (row, x0, x1), bottom to top. Unlike the
    gorilla, no outline is drawn: the arm is already black, and surrounding it
    with black would fatten it to 4 px. Rows in `paw_rows` are solid; the rest
    carry a dark-grey line down the middle.
    """
    for row, x0, x1 in ladder:
        for x in range(x0, x1 + 1):
            c.set(x, row, BLACK)
        if row not in paw_rows:
            c.set((x0 + x1) // 2, row, DARK_GREY)


# --- frames ---------------------------------------------------------------

def _idle() -> Canvas:
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _hanging_arm(c, left=True)
    _hanging_arm(c, left=False)
    return c


def _arm_left() -> Canvas:
    """Left arm raised: it follows the projectile travelling to the left."""
    c = Canvas(CELL_W, CELL_H)
    _base(c, head_dx=-1)
    _hanging_arm(c, left=False)
    _raised_arm(c, [(12, 4, 6), (11, 4, 6), (10, 3, 5), (9, 3, 5), (8, 2, 4),
                    (7, 2, 4), (6, 1, 3), (5, 1, 3), (4, 0, 2)],
                paw_rows={4, 5})
    return c


def _arm_right() -> Canvas:
    """Mirror of the previous one but drawn by hand: the arm rises steeper
    (three rows per step instead of two) and the head leans the other way. An
    automatic flip gives the symmetry away and looks dead."""
    c = Canvas(CELL_W, CELL_H)
    _base(c, head_dx=1)
    _hanging_arm(c, left=True)
    _raised_arm(c, [(12, 17, 19), (11, 17, 19), (10, 18, 20), (9, 18, 20),
                    (8, 18, 20), (7, 19, 21), (6, 19, 21), (5, 20, 22),
                    (4, 20, 22)],
                paw_rows={4, 5})
    return c


def _chest_1() -> Canvas:
    """Paws up. The left one tops out one row before the right one."""
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _raised_arm(c, [(12, 4, 6), (11, 3, 5), (10, 2, 4), (9, 1, 4), (8, 1, 4)],
                paw_rows={8, 9})
    _raised_arm(c, [(12, 17, 19), (11, 18, 20), (10, 19, 22), (9, 19, 22)],
                paw_rows={9, 10})
    return c


def _chest_2() -> Canvas:
    """Paws against the torso. This is where the panda beats the gorilla: the
    forearms are black and the belly white, so contrast separates them on its
    own -- no outline and no invented intermediate tone needed."""
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _hanging_arm(c, left=True)
    _hanging_arm(c, left=False)
    c.fill_rect(8, 17, 15, 19, WHITE)       # the belly shading ends up covered
    for row in (17, 18, 19):
        for x in range(4, 11):
            c.set(x, row, BLACK)
        for x in range(13, 20):
            c.set(x, row, BLACK)
    for x in (5, 6, 7):                     # highlight along the forearm
        c.set(x, 18, DARK_GREY)
    for x in (16, 17, 18):
        c.set(x, 18, DARK_GREY)
    return c


_DEFEATED = [
    "....00....00....",     # ears
    "...0000000000...",
    "...0FFFFFFFF0...",
    "...0F00FF00F0...",
    "...0F00FF00F0...",
    "...0FFF00FFF0...",
    "...00FFFFFF00...",
    "...0000000000...",     # sunken shoulders
    ".00000000000000.",
    "00000FFFFFF00000",     # arms sprawled over the rooftop
    "0000FFFFFFFF0000",
    "0000000000000000",     # contact with the rooftop
]


def _defeated() -> Canvas:
    """Beaten: collapsed onto the rooftop, head sunk between the shoulders and
    the arms sprawled out.

    Two versions lying in profile were tried: at 1x one reads as a lump and the
    other as a factory with chimneys. Keeping the vertical silhouette and simply
    squashing it from 20 to 12 px is the only one recognisable at a glance. Same
    bottom edge and same 16 px width as the rest of the strip."""
    c = Canvas(CELL_W, CELL_H)
    c.blit(Canvas.from_ascii(_DEFEATED, 16, 12, "panda/defeated"), BODY_X, 12)
    return c


def frames():
    return [_idle(), _arm_left(), _arm_right(), _chest_1(), _chest_2(), _defeated()]
