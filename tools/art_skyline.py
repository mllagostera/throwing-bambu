"""skyline.png -- 460 x 80, a single layer, no parallax.

The engine crops the band to the real canvas width (320-460 px), always from the
right, so the design is deliberately uniform: no centred composition and no
memorable building that only shows up on wide screens. If one player sees it at
460 and another at 330, they see the same background.

Flat silhouette in blue (1), no volume, anchored to the bottom edge. The window
dots are light blue (9) rather than yellow: yellow is the colour of the lit
windows on the playable facades, and repeating it here erases the difference
between what is in front and what is behind. In light blue the band stays where
it belongs, at the back.

The layout comes from a fixed-seed LCG, so the PNG is reproducible bit for bit
from this file.
"""

from ega import Canvas

WIDTH, HEIGHT = 460, 80
SILHOUETTE, WINDOW = 1, 9

BASE_Y = HEIGHT - 1                  # the band rests on the bottom edge
MIN_HEIGHT, MAX_HEIGHT = 11, 23      # leaves the top 57 px clear
MIN_WIDTH, MAX_WIDTH = 7, 24
SEED = 0x60411A5                     # arbitrary, but fixed on purpose


class _Lcg:
    """Minimal linear congruential generator, so the result depends neither on
    the Python version nor on the random module."""

    def __init__(self, seed: int):
        self.s = seed & 0xFFFFFFFF

    def next(self) -> int:
        self.s = (1664525 * self.s + 1013904223) & 0xFFFFFFFF
        return self.s

    def between(self, lo: int, hi: int) -> int:
        return lo + self.next() % (hi - lo + 1)


def build() -> Canvas:
    c = Canvas(WIDTH, HEIGHT)
    rnd = _Lcg(SEED)
    x = 0
    n = 0
    h_prev = 0
    while x < WIDTH:
        w = rnd.between(MIN_WIDTH, MAX_WIDTH)
        # Two adjacent buildings of similar height merge into one huge flat
        # block: a step of at least 3 px is required.
        for _ in range(16):
            h = rnd.between(MIN_HEIGHT, MAX_HEIGHT)
            if abs(h - h_prev) >= 3:
                break
        h_prev = h
        top = BASE_Y - h + 1
        c.fill_rect(x, top, min(x + w - 1, WIDTH - 1), BASE_Y, SILHOUETTE)

        # One antenna every few buildings. 1 px wide and never above y=48, so it
        # does not intrude on the high arc of the trajectories.
        if n % 5 == 3:
            ax = x + w // 2
            atop = max(48, top - rnd.between(4, 9))
            c.fill_rect(ax, atop, ax, top, SILHOUETTE)

        # Windows: a 1 px dot on a 3 px grid, lit sparsely.
        for wy in range(top + 2, BASE_Y - 1, 3):
            for wx in range(x + 2, x + w - 2, 3):
                if rnd.next() % 7 == 0:
                    c.set(wx, wy, WINDOW)

        x += w
        n += 1

    # Continuous plinth: without it, two adjacent buildings of the same height
    # leave empty columns and the city reads with holes of sky down to the floor.
    c.fill_rect(0, BASE_Y - 2, WIDTH - 1, BASE_Y, SILHOUETTE)
    return c
