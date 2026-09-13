"""bambu.png -- 4 fotogramas de 8x8, pivote (4, 4).

Sustituye al platano de docs/ESPEC_SPRITES.md conservando el contrato: misma
celda, mismo pivote, mismos cuatro pasos de 90 grados y mismo bucle a 80 ms. El
motor sigue sin rotar el sprite.

Los cuatro fotogramas son el mismo cuerpo -- una cana de 6 px de largo y 2 px de
grueso -- presentado a 0, 45, 90 y 135 grados. Lo que identifica al bambu a esta
escala no es la silueta, que a 8x8 no da para mas que una barra, sino los
**nudos**: dos bandas de verde (2) sobre el cuerpo en verde claro (10). Sin
nudos es un palo; con ellos se lee como cana a 1x.

Cambia una restriccion del briefing: el color prohibido para la base de las
fachadas ya no es el amarillo sino el verde. Ver art_fachadas.py.
"""

from ega import Canvas

FRAME_NAMES = ["horizontal", "diagonal_asc", "vertical", "diagonal_desc"]
FRAME_MS = 80
CELL = (8, 8)

VERDE_CLARO, VERDE = 10, 2

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

_DIAGONAL_ASC = [
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

_DIAGONAL_DESC = [
    ".00.....",
    "0AA0....",
    ".0220...",
    "..0AA0..",
    "...0AA0.",
    "....0220",
    ".....0AA",
    "......00",
]

_SOURCES = [_HORIZONTAL, _DIAGONAL_ASC, _VERTICAL, _DIAGONAL_DESC]


def frames():
    return [
        Canvas.from_ascii(rows, expect_w=8, expect_h=8, name=f"bambu/{n}")
        for n, rows in zip(FRAME_NAMES, _SOURCES)
    ]
