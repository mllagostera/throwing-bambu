"""boom.png -- 8 fotogramas de 32x32, pivote (16, 16) = punto de impacto.

Restriccion dura del briefing: el motor borra del terreno un circulo de radio 12
centrado en el pivote, y lo hace en el fotograma 3. Por eso el fotograma 3 es el
pico y su radio visible es 15 px, 3 px por encima del crater: si el fuego fuese
mas pequeno que el agujero el impacto se percibe flojo.

  0-2  expansion, radio visible 4 -> 8 -> 12
  3    pico, radio 15
  4    el nucleo se vacia y el borde se vuelve irregular
  5    el anillo se rompe en seis arcos
  6-7  fragmentos sueltos que se apagan

Rampa: 15 blanco -> 14 amarillo -> 12 rojo claro -> 4 rojo -> transparente. Las
transiciones se resuelven con tablero de ajedrez: el desplazamiento del dither es
0 o +1 px segun la paridad de (x+y), nunca negativo, de modo que el radio maximo
de cada fotograma es exactamente el declarado y jamas se pasa de largo.
"""

import math

from ega import Canvas, T

CELL = (32, 32)
FRAME_MS = 40
CRATER_RADIUS = 12          # lo que borra el motor
CRATER_FRAME = 3            # cuando lo borra
PEAK_RADIUS = 15            # radio visible del fotograma de pico

CX = CY = 15.5              # el pivote (16,16) cae entre pixeles

BLANCO, AMARILLO, ROJO_CLARO, ROJO = 15, 14, 12, 4
HUECO = None                # banda transparente (nucleo ya apagado)

# Rampa radial por fotograma, de dentro afuera: (radio exterior, color).
_BANDS = {
    0: [(1.5, BLANCO), (3.0, AMARILLO), (4.0, ROJO_CLARO)],
    1: [(2.5, BLANCO), (5.0, AMARILLO), (7.0, ROJO_CLARO), (8.0, ROJO)],
    2: [(4.0, BLANCO), (7.5, AMARILLO), (10.0, ROJO_CLARO), (12.0, ROJO)],
    3: [(5.0, BLANCO), (9.0, AMARILLO), (12.5, ROJO_CLARO), (15.0, ROJO)],
    4: [(3.0, HUECO), (6.5, AMARILLO), (10.0, ROJO_CLARO), (14.0, ROJO)],
    5: [(6.0, HUECO), (9.5, ROJO_CLARO), (15.0, ROJO)],
}

# Fotograma 4: muescas que rompen el borde. (angulo central, semiancho) en grados;
# solo muerden mas alla de r=9, asi que el nucleo sigue entero.
_MUESCAS = [(22, 5), (67, 4), (112, 6), (158, 3),
            (203, 5), (248, 4), (293, 6), (338, 3)]

# Fotograma 5: seis arcos. (angulo central, semiancho, r_int, r_ext).
_ARCOS = [(15, 27, 8.5, 15), (72, 21, 9.0, 14), (133, 28, 8.5, 15),
          (195, 22, 9.0, 14), (254, 26, 8.5, 15), (310, 20, 9.5, 13)]

# Plantillas de fragmento. 'X' = cuerpo, 'o' = nucleo aun caliente, '.' = nada.
_PLANTILLAS = {
    "a": [".XXX.", "XXoXX", "XXoXX", ".XXXX", "..XX."],
    "b": [".XX.", "XoXX", "XXX.", ".XX."],
    "c": [".X.", "XoX", ".X."],
    "d": ["XX", "XX"],
    "e": ["X"],
}

# Fotogramas 6 y 7: (plantilla, angulo, radio). Colocados a mano: el briefing
# prohibe el ruido aleatorio, y un anillo de fragmentos regular se lee como
# rueda dentada en lugar de como fuego disgregandose.
_FRAGMENTOS = {
    6: [("a", 16, 10.5), ("a", 58, 11.5), ("b", 96, 10.0), ("a", 134, 11.0),
        ("b", 172, 12.0), ("a", 212, 10.5), ("b", 252, 11.5), ("a", 292, 10.0),
        ("c", 330, 12.0), ("c", 352, 10.0)],
    7: [("b", 24, 12.5), ("c", 82, 13.0), ("b", 140, 12.0), ("d", 186, 13.5),
        ("c", 232, 12.5), ("b", 296, 13.0), ("d", 342, 13.5)],
}


def _color_at(frame: int, r: float):
    for r_out, col in _BANDS[frame]:
        if r <= r_out:
            return col
    return None


def _es_hueco(frame: int, r: float) -> bool:
    bands = _BANDS[frame]
    return bands[0][1] is HUECO and r <= bands[0][0]


def _ang_dist(a: float, b: float) -> float:
    return abs((a - b + 180.0) % 360.0 - 180.0)


def _radial(n: int) -> Canvas:
    """Fotogramas 0-5: bandas concentricas con dither de tablero."""
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            dx, dy = x - CX, y - CY
            r = math.hypot(dx, dy)
            rd = r + (1.0 if (x + y) % 2 else 0.0)
            ang = math.degrees(math.atan2(-dy, dx)) % 360.0
            if n == 4 and rd > 9.0:
                if any(_ang_dist(ang, a) <= w for a, w in _MUESCAS):
                    continue
            if n == 5:
                if not any(ri <= rd <= ro and _ang_dist(ang, a) <= w
                           for a, w, ri, ro in _ARCOS):
                    continue
            col = _color_at(n, r if _es_hueco(n, r) else rd)
            if col is not None:
                c.set(x, y, col)
    return c


def _fragmentado(n: int) -> Canvas:
    """Fotogramas 6-7: fragmentos sueltos colocados uno a uno."""
    c = Canvas(32, 32)
    for nombre, ang, r in _FRAGMENTOS[n]:
        rows = _PLANTILLAS[nombre]
        h, w = len(rows), len(rows[0])
        px = round(CX + r * math.cos(math.radians(ang)) - (w - 1) / 2.0)
        py = round(CY - r * math.sin(math.radians(ang)) - (h - 1) / 2.0)
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch == ".":
                    continue
                # En el 6 el nucleo del fragmento aun tiene rojo claro; en el 7
                # ya esta todo apagado a rojo.
                col = ROJO_CLARO if (ch == "o" and n == 6) else ROJO
                c.set(px + dx, py + dy, col)
    return c


def _frame(n: int) -> Canvas:
    return _radial(n) if n in _BANDS else _fragmentado(n)


def frames():
    return [_frame(n) for n in range(8)]


def max_radius(c: Canvas) -> float:
    """Radio visible maximo respecto al pivote, para verify_assets.py."""
    best = 0.0
    for y in range(c.h):
        for x in range(c.w):
            if c.px[y][x] != T:
                best = max(best, math.hypot(x - CX, y - CY))
    return best
