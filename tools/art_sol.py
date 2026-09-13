"""sol.png -- 2 fotogramas de 20x20, pivote (10, 10).

Disco de 12 px de diametro centrado en el pivote, con contorno negro. Los rayos
van en amarillo macizo sin contorno: a 1 px de grosor un contorno los
convertiria en barras de 3 px y el sol pasaria a ser una rueda dentada negra.

Fotograma 1 (`ouch`): boca en O, ojos reducidos a un punto y rayos recogidos
1 px. El motor lo mantiene 1 s cuando un platano atraviesa el sol; el platano no
se detiene.
"""

from ega import Canvas

FRAME_NAMES = ["normal", "ouch"]
OUCH_MS = 1000
CELL = (20, 20)

AMARILLO, NEGRO = 14, 0

# Disco: ancho de cada fila, de y=4 a y=15. Centro geometrico en (9.5, 9.5).
_DISC_WIDTH = [4, 8, 10, 12, 12, 12, 12, 12, 12, 10, 8, 4]
_DISC_TOP = 4


def _disc_cols(row):
    """Columnas (inclusive) que ocupa el disco en esa fila, o None."""
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
        borde = any((x + dx, y + dy) not in inside
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c.set(x, y, NEGRO if borde else AMARILLO)


def _rays(c: Canvas, largo: int, diagonales):
    """Cuatro rayos cardinales de `largo` px + diagonales puntuales."""
    for n in range(largo):
        c.set(9, 3 - n, AMARILLO)            # norte
        c.set(10, 3 - n, AMARILLO)
        c.set(9, 16 + n, AMARILLO)           # sur
        c.set(10, 16 + n, AMARILLO)
        c.set(3 - n, 9, AMARILLO)            # oeste
        c.set(3 - n, 10, AMARILLO)
        c.set(16 + n, 9, AMARILLO)           # este
        c.set(16 + n, 10, AMARILLO)
    for (x, y) in diagonales:
        c.set(x, y, AMARILLO)


def _normal() -> Canvas:
    c = Canvas(20, 20)
    _rays(c, 4, [(3, 3), (2, 2), (16, 3), (17, 2),
                 (3, 16), (2, 17), (16, 16), (17, 17)])
    _disc(c)
    # Ojos de 2x2 y boca sonriente con las comisuras una fila mas arriba.
    for x in (7, 8, 11, 12):
        c.set(x, 8, NEGRO)
        c.set(x, 9, NEGRO)
    c.set(7, 11, NEGRO)
    c.set(12, 11, NEGRO)
    for x in range(8, 12):
        c.set(x, 12, NEGRO)
    return c


def _ouch() -> Canvas:
    c = Canvas(20, 20)
    _rays(c, 3, [(3, 3), (16, 3), (3, 16), (16, 16)])
    _disc(c)
    # Ojos como puntos, mas separados, y boca abierta en O de 4x3.
    c.set(7, 8, NEGRO)
    c.set(12, 8, NEGRO)
    for x in range(8, 12):
        c.set(x, 11, NEGRO)
        c.set(x, 13, NEGRO)
    c.set(8, 12, NEGRO)
    c.set(11, 12, NEGRO)
    return c


def frames():
    return [_normal(), _ouch()]
