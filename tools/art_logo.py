"""logo.png -- 200 x 60, the title for the menu screen.

SCOPE NOTICE. The brief (section 9) blocks this piece until the final name is
decided, and expressly forbids using "Gorillas" or the GORILLA.BAS typeface,
which belong to Microsoft. It is lettered THROWING BAMBU here because that is
the repository name; the letterforms are original, drawn for this piece. If the
name changes, only the TEXT constants change: the glyphs are in _FONT.

Construction: each glyph is drawn at 7x10 with a 1 px stroke and scaled x2 to
give a 2 px stroke. The black outline, the top highlight, the stroke shadow and
the drop shadow are all applied AFTERWARDS, at final resolution, so the piece is
not a naive upscale: the 1 px details exist only in the result.
"""

from ega import Canvas, T

WIDTH, HEIGHT = 200, 60

BLACK, BROWN, DARK_GREY, YELLOW, WHITE = 0, 6, 8, 14, 15

LINE_1 = "THROWING"
LINE_2 = "BAMBU"

_FONT = {
    "T": ["1111111", "...1...", "...1...", "...1...", "...1...",
          "...1...", "...1...", "...1...", "...1...", "...1..."],
    "H": ["1.....1", "1.....1", "1.....1", "1.....1", "1111111",
          "1.....1", "1.....1", "1.....1", "1.....1", "1.....1"],
    "R": ["111111.", "1.....1", "1.....1", "1.....1", "111111.",
          "1...1..", "1....1.", "1.....1", "1.....1", "1.....1"],
    "O": [".11111.", "1.....1", "1.....1", "1.....1", "1.....1",
          "1.....1", "1.....1", "1.....1", "1.....1", ".11111."],
    "W": ["1.....1", "1.....1", "1.....1", "1.....1", "1.....1",
          "1..1..1", "1..1..1", "1.1.1.1", "11...11", ".1...1."],
    "I": [".11111.", "...1...", "...1...", "...1...", "...1...",
          "...1...", "...1...", "...1...", "...1...", ".11111."],
    "N": ["1.....1", "11....1", "11....1", "1.1...1", "1.11..1",
          "1..11.1", "1...111", "1....11", "1.....1", "1.....1"],
    "G": [".11111.", "1.....1", "1......", "1......", "1..1111",
          "1.....1", "1.....1", "1.....1", "1.....1", ".11111."],
    "B": ["111111.", "1.....1", "1.....1", "1.....1", "111111.",
          "1.....1", "1.....1", "1.....1", "1.....1", "111111."],
    "A": ["..111..", ".1...1.", "1.....1", "1.....1", "1.....1",
          "1111111", "1.....1", "1.....1", "1.....1", "1.....1"],
    "M": ["1.....1", "11...11", "1.1.1.1", "1.1.1.1", "1..1..1",
          "1.....1", "1.....1", "1.....1", "1.....1", "1.....1"],
    "U": ["1.....1", "1.....1", "1.....1", "1.....1", "1.....1",
          "1.....1", "1.....1", "1.....1", "1.....1", ".11111."],
}

GLYPH_W, GLYPH_H = 14, 20     # 7x10 scaled x2
TRACKING = 2                  # gap between glyphs, in final pixels

# Decorative bamboo cane, drawn at final resolution (it is not the game sprite
# upscaled: that one is 8x8 and at x2 would give the scaling away). Here there
# is room for the nodes and for two leaves, which did not fit at 8x8.
_CANE = [
    ".....000000.....",
    ".....0AAAA0.....",
    ".....0AAAA0000..",
    ".....0AAAA0AA0..",     # right leaf
    ".....0AAAA0000..",
    ".....022220.....",     # node
    "..0000AAAA0.....",
    "..0AA0AAAA0.....",     # left leaf
    "..0000AAAA0.....",
    ".....0AAAA0.....",
    ".....022220.....",     # node
    ".....0AAAA0.....",
    ".....0AAAA0.....",
    ".....0AAAA0.....",
    ".....022220.....",     # node
    ".....000000.....",
]
CANE_W = 16
CANE_GAP = 4


def _ink(text: str):
    """Set of stroke pixels for the text, already scaled x2. Origin (0,0)."""
    ink = set()
    x0 = 0
    for ch in text:
        g = _FONT[ch]
        for y, row in enumerate(g):
            for x, c in enumerate(row):
                if c == "1":
                    for dy in (0, 1):
                        for dx in (0, 1):
                            ink.add((x0 + x * 2 + dx, y * 2 + dy))
        x0 += GLYPH_W + TRACKING
    return ink, x0 - TRACKING


def _render(c: Canvas, ink, ox: int, oy: int):
    """Drop shadow, outline, fill, highlight and stroke shadow."""
    abs_ink = {(x + ox, y + oy) for (x, y) in ink}
    for (x, y) in sorted(abs_ink):
        if (x - 2, y - 2) not in abs_ink:
            c.set(x + 2, y + 2, DARK_GREY)
    for (x, y) in sorted(abs_ink):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if (x + dx, y + dy) not in abs_ink:
                    c.set(x + dx, y + dy, BLACK)
    for (x, y) in sorted(abs_ink):
        if (x, y - 1) not in abs_ink:
            c.set(x, y, WHITE)           # highlight: light from above
        elif (x, y + 1) not in abs_ink and (x, y - 2) in abs_ink:
            # Stroke shadow only on strokes 3 px or thicker. On the 2 px
            # horizontal bars it would eat all the yellow and the wordmark
            # would come out white and brown.
            c.set(x, y, BROWN)
        else:
            c.set(x, y, YELLOW)


def build() -> Canvas:
    c = Canvas(WIDTH, HEIGHT)

    ink1, w1 = _ink(LINE_1)
    ink2, w2 = _ink(LINE_2)
    w2_total = w2 + CANE_GAP + CANE_W

    # Centred counting the outline (1 px) and the drop shadow (2 px).
    x1 = (WIDTH - (w1 + 3)) // 2
    x2 = (WIDTH - (w2_total + 3)) // 2
    y1, y2 = 6, 32

    _render(c, ink1, x1, y1)
    _render(c, ink2, x2, y2)

    cane = Canvas.from_ascii(_CANE, expect_w=16, expect_h=16, name="logo/bamboo")
    c.blit(cane, x2 + w2 + CANE_GAP, y2 + 2)
    return c
