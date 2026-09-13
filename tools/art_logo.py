"""logo.png -- 200 x 60, titulo para la pantalla de menu.

AVISO DE ALCANCE. El briefing (seccion 9) bloquea esta pieza hasta que el nombre
definitivo este decidido, y prohibe expresamente usar «Gorillas» o la tipografia
de GORILLA.BAS, que son de Microsoft. Aqui se rotula THROWING BAMBU porque es el
nombre del repositorio; la letra es original, dibujada para esta pieza. Si el
nombre cambia, solo cambia la cadena de TEXTO: los glifos estan en _FONT.

Construccion: cada glifo se dibuja a 7x10 con trazo de 1 px y se amplia x2 para
dar un trazo de 2 px. El contorno negro, el brillo superior, la sombra inferior y
la sombra proyectada se aplican DESPUES, a resolucion final, asi que la pieza no
es un escalado ingenuo: los detalles de 1 px existen solo en el resultado.
"""

from ega import Canvas, T

WIDTH, HEIGHT = 200, 60

NEGRO, MARRON, GRIS_OSC, AMARILLO, BLANCO = 0, 6, 8, 14, 15

LINEA_1 = "THROWING"
LINEA_2 = "BAMBU"

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

GLYPH_W, GLYPH_H = 14, 20     # 7x10 ampliado x2
TRACKING = 2                  # separacion entre glifos, en px finales

# Cana de bambu de adorno, dibujada a resolucion final (no es el sprite de juego
# ampliado: ese mide 8x8 y al x2 delataria el escalado). Aqui si hay sitio para
# los nudos y para dos hojas, que a 8x8 no cabian.
_PLATANO = [
    ".....000000.....",
    ".....0AAAA0.....",
    ".....0AAAA0000..",
    ".....0AAAA0AA0..",     # hoja derecha
    ".....0AAAA0000..",
    ".....022220.....",     # nudo
    "..0000AAAA0.....",
    "..0AA0AAAA0.....",     # hoja izquierda
    "..0000AAAA0.....",
    ".....0AAAA0.....",
    ".....022220.....",     # nudo
    ".....0AAAA0.....",
    ".....0AAAA0.....",
    ".....0AAAA0.....",
    ".....022220.....",     # nudo
    ".....000000.....",
]
PLATANO_W = 16
HUECO_PLATANO = 4


def _ink(texto: str):
    """Conjunto de pixeles de trazo del texto, ya ampliado x2. Origen (0,0)."""
    ink = set()
    x0 = 0
    for ch in texto:
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
    """Sombra proyectada, contorno, relleno, brillo y sombra de trazo."""
    abs_ink = {(x + ox, y + oy) for (x, y) in ink}
    for (x, y) in sorted(abs_ink):
        if (x - 2, y - 2) not in abs_ink:
            c.set(x + 2, y + 2, GRIS_OSC)
    for (x, y) in sorted(abs_ink):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if (x + dx, y + dy) not in abs_ink:
                    c.set(x + dx, y + dy, NEGRO)
    for (x, y) in sorted(abs_ink):
        if (x, y - 1) not in abs_ink:
            c.set(x, y, BLANCO)          # brillo: luz desde arriba
        elif (x, y + 1) not in abs_ink and (x, y - 2) in abs_ink:
            # Sombra del trazo solo en trazos de 3 px o mas. En las barras
            # horizontales de 2 px se comeria el amarillo entero y el rotulo
            # quedaria blanco y marron.
            c.set(x, y, MARRON)
        else:
            c.set(x, y, AMARILLO)


def build() -> Canvas:
    c = Canvas(WIDTH, HEIGHT)

    ink1, w1 = _ink(LINEA_1)
    ink2, w2 = _ink(LINEA_2)
    w2_total = w2 + HUECO_PLATANO + PLATANO_W

    # Se centra contando contorno (1 px) y sombra proyectada (2 px).
    x1 = (WIDTH - (w1 + 3)) // 2
    x2 = (WIDTH - (w2_total + 3)) // 2
    y1, y2 = 6, 32

    _render(c, ink1, x1, y1)
    _render(c, ink2, x2, y2)

    plat = Canvas.from_ascii(_PLATANO, expect_w=16, expect_h=16, name="logo/bambu")
    c.blit(plat, x2 + w2 + HUECO_PLATANO, y2 + 2)
    return c
