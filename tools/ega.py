"""Nucleo comun de produccion de arte: paleta EGA, lienzos ASCII y escritura PNG.

Reglas que este modulo hace imposibles de romper (ver ESPEC_SPRITES.md, seccion 0):
  - paleta cerrada de 16 colores: cualquier indice fuera de 0..15 es un error;
  - alfa binario: el PNG se escribe indexado con un unico indice transparente,
    asi que no existe representacion posible para un alfa intermedio;
  - celda uniforme: los constructores validan dimensiones al construir.
"""

from __future__ import annotations

# Paleta EGA de 16 colores, en el orden declarado por el briefing (seccion 1).
EGA = [
    (0x00, 0x00, 0x00),  # 0  negro
    (0x00, 0x00, 0xAA),  # 1  azul
    (0x00, 0xAA, 0x00),  # 2  verde
    (0x00, 0xAA, 0xAA),  # 3  cian
    (0xAA, 0x00, 0x00),  # 4  rojo
    (0xAA, 0x00, 0xAA),  # 5  magenta
    (0xAA, 0x55, 0x00),  # 6  marron
    (0xAA, 0xAA, 0xAA),  # 7  gris claro
    (0x55, 0x55, 0x55),  # 8  gris oscuro
    (0x55, 0x55, 0xFF),  # 9  azul claro
    (0x55, 0xFF, 0x55),  # 10 verde claro
    (0x55, 0xFF, 0xFF),  # 11 cian claro
    (0xFF, 0x55, 0x55),  # 12 rojo claro
    (0xFF, 0x55, 0xFF),  # 13 magenta claro
    (0xFF, 0xFF, 0x55),  # 14 amarillo
    (0xFF, 0xFF, 0xFF),  # 15 blanco
]

EGA_NAMES = [
    "negro", "azul", "verde", "cian", "rojo", "magenta", "marron", "gris claro",
    "gris oscuro", "azul claro", "verde claro", "cian claro", "rojo claro",
    "magenta claro", "amarillo", "blanco",
]

# Indice reservado para "sin pintar". No es un color de la paleta: se escribe en
# el chunk tRNS del PNG y por tanto es 100 % transparente, nunca un alfa parcial.
T = 16

# Color del hueco transparente en la tabla del PNG. Magenta puro NO pertenece a
# la EGA, de modo que si alguna herramienta ignora el tRNS el fallo salta a la
# vista en lugar de pasar por un negro legitimo.
TRANSPARENT_KEY = (0xFF, 0x00, 0xFF)

# Mapa de caracteres usado por los lienzos ASCII de art_*.py.
#   '0'..'9','A'..'F' -> indice de paleta     '.' -> transparente
_CHARS = "0123456789ABCDEF"


def idx_of_char(ch: str) -> int:
    if ch == ".":
        return T
    up = ch.upper()
    if up not in _CHARS:
        raise ValueError(f"caracter {ch!r} fuera de la paleta (use 0-9, A-F o '.')")
    return _CHARS.index(up)


class Canvas:
    """Rejilla de indices de paleta. Origen arriba-izquierda."""

    def __init__(self, w: int, h: int, fill: int = T):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    # -- construccion -----------------------------------------------------
    @classmethod
    def from_ascii(cls, rows, expect_w=None, expect_h=None, name="<anon>"):
        rows = [r for r in rows]
        h = len(rows)
        if expect_h is not None and h != expect_h:
            raise ValueError(f"{name}: {h} filas, se esperaban {expect_h}")
        w = len(rows[0]) if rows else 0
        if expect_w is not None:
            w = expect_w
        for y, row in enumerate(rows):
            if len(row) != w:
                raise ValueError(
                    f"{name}: fila {y} mide {len(row)} px, se esperaban {w}"
                )
        c = cls(w, h)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                c.px[y][x] = idx_of_char(ch)
        return c

    # -- acceso -----------------------------------------------------------
    def get(self, x: int, y: int) -> int:
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return T

    def set(self, x: int, y: int, i: int) -> None:
        if not (0 <= i <= 16):
            raise ValueError(f"indice {i} fuera de la paleta cerrada")
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
        """Caja minima de pixeles opacos, o None si el lienzo esta vacio."""
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
    """Monta una tira horizontal de fotogramas de celda identica."""
    for n, f in enumerate(frames):
        if (f.w, f.h) != (cell_w, cell_h):
            raise ValueError(
                f"{name}: fotograma {n} mide {f.w}x{f.h}, la celda es {cell_w}x{cell_h}"
            )
    strip = Canvas(cell_w * len(frames), cell_h)
    for n, f in enumerate(frames):
        strip.blit(f, n * cell_w, 0, skip_transparent=False)
    return strip


def save_png(canvas: Canvas, path: str) -> None:
    """Escribe PNG-8 indexado con un unico indice totalmente transparente."""
    from PIL import Image

    img = Image.new("P", (canvas.w, canvas.h), T)
    pal = []
    for rgb in EGA:
        pal += list(rgb)
    pal += list(TRANSPARENT_KEY)
    pal += [0, 0, 0] * (256 - len(EGA) - 1)
    img.putpalette(pal)
    img.putdata([v for row in canvas.px for v in row])
    # Sin optimize: reindexaria la tabla y movería el indice transparente.
    img.save(path, transparency=T)
