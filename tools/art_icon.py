"""icon.png -- 24 x 24, the launcher icon.

Not a sprite: it never reaches the playfield and the engine never draws it. It
lives here because it is pixel art in the same closed palette, and because the
one thing this project refuses to do is hand-edit a PNG.

Why 24 x 24. An adaptive icon is 108 dp of which only the central 72 dp is
guaranteed to survive the launcher's mask, and the art has to land on whole
pixels at every density. 24 divides both: centred in a 36 x 36 canvas it is
exactly the 72 dp safe zone (36 x 3 dp = 108 dp), and for the legacy icon that
API 24 and 25 still need it scales by 2, 4, 6 and 8 to 48, 96, 144 and 192 px
with no fractional step anywhere. It is also, not by coincidence, the panda's
own cell size.

Composition. The head fills the frame and the bamboo cane crosses its lower left
on a diagonal. Both follow the identity rules the rest of the art already
obeys: the panda is read from high-contrast patches -- ears, eye masks, nose --
and never from fur volume, which does not survive at this size; the cane is read
from its nodes, not its outline, so the two dark green bands are the part that
must stay visible.

Two rules the corners impose. Every launcher masks the square differently, and
some mask it to a circle, so the four corners hold nothing but the ground --
`verify_assets.py` fails the build if anything creeps in. And the piece is fully
opaque: a hole would let the wallpaper through the middle of the icon.

No outline around the head, and no grey anywhere. Both are the same decision
the panda sprite already made (see the divergences in art/README.md): the
silhouette is defined where the white meets the ground, and ringing or shading
it only softens the edge it is supposed to sharpen. Five colours are enough;
a sixth was tried as shading under the eye masks and read as a smudge.
"""

from ega import Canvas

WIDTH = HEIGHT = 24

# 1 ground (blue)   F face (white)   0 ears, eye masks, nose (black)
# A cane body (light green)   2 cane nodes (green)
_ART = [
    "111111111111111111111111",
    "111111111111111111111111",
    "111100011111111110001111",
    "111000001111111100000111",
    "11100000FFFFFFFF00000111",
    "1110000FFFFFFFFFF0000111",
    "111100FFFFFFFFFFFF001111",
    "111FFFFFFFFFFFFFFFFFF111",
    "111FFFFFFFFFFFFFFFFFF111",
    "111FFF0000FFFF0000FFF111",
    "111FF000000FF000000FF111",
    "111FF000000FF000000FF111",
    "AA1FF000000FF000000FF111",
    "1AAFFF0000FFFF0000FFF111",
    "11AAFFFFFFFFFFFFFFFF1111",
    "11122FFFFF0000FFFFFF1111",
    "1111AAFFFFF00FFFFFF11111",
    "11111AAFFFFFFFFFFF111111",
    "111111AAFFFFFFFFF1111111",
    "111111122FFFFFF111111111",
    "11111111AA11111111111111",
    "111111111AA1111111111111",
    "1111111111AA111111111111",
    "11111111111AA11111111111",
]


def build() -> Canvas:
    return Canvas.from_ascii(_ART, expect_w=WIDTH, expect_h=HEIGHT, name="icon")
