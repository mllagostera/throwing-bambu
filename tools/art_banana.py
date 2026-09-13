"""banana.png -- 4 fotogramas de 8x8, pivote (4, 4).

El motor no rota el sprite: recorre los cuatro fotogramas en bucle a 80 ms. Para
que se lean como un solo objeto girando, los cuatro son el mismo cuerpo -- una
barra de 6 px de largo y 2 px de grueso, puntas en marron (6) y cuerpo en
amarillo (14) -- presentado a 0, 45, 90 y 135 grados. Cualquier intento de
silueta de platano mas elaborada a 8x8 cambia de masa entre fotogramas y el
bucle empieza a parpadear.
"""

from ega import Canvas

FRAME_NAMES = ["horizontal", "diagonal_asc", "vertical", "diagonal_desc"]
FRAME_MS = 80
CELL = (8, 8)

_HORIZONTAL = [
    "........",
    "........",
    ".000000.",
    "06EEEE60",
    "06EEEE60",
    ".000000.",
    "........",
    "........",
]

_DIAGONAL_ASC = [
    ".....00.",
    "....0660",
    "...0EE0.",
    "..0EE0..",
    ".0EE0...",
    "0EE0....",
    "660.....",
    "00......",
]

_VERTICAL = [
    "...00...",
    "..0660..",
    "..0EE0..",
    "..0EE0..",
    "..0EE0..",
    "..0EE0..",
    "..0660..",
    "...00...",
]

_DIAGONAL_DESC = [
    ".00.....",
    "0660....",
    ".0EE0...",
    "..0EE0..",
    "...0EE0.",
    "....0EE0",
    ".....066",
    "......00",
]

_SOURCES = [_HORIZONTAL, _DIAGONAL_ASC, _VERTICAL, _DIAGONAL_DESC]


def frames():
    return [
        Canvas.from_ascii(rows, expect_w=8, expect_h=8, name=f"banana/{n}")
        for n, rows in zip(FRAME_NAMES, _SOURCES)
    ]
