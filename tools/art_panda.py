"""panda.png -- 6 fotogramas de 24x24, pivote (12, 24).

Sustituye al gorila de docs/ESPEC_SPRITES.md. Se conservan intactos el contrato
geometrico (celda 24x24, pivote (12,24), cuerpo de 16x20 en x=4..19 / y=4..23,
seis fotogramas en el mismo orden) y los nombres de pose; cambia el personaje.

Un panda se lee mejor que un gorila a esta escala, y no por casualidad: su
identidad esta en manchas de alto contraste -- orejas, antifaces, banda del
pecho, patas -- y no en el volumen del pelaje, que a 20 px de alto no cabe. Por
eso aqui el reparto de tonos se invierte respecto al gorila: el cuerpo es blanco
(15), las manchas negras (0), y los grises quedan como apoyo -- gris oscuro (8)
para dar forma a las masas negras y gris claro (7) para la sombra de la barriga.

El negro hace de contorno y de mancha a la vez, asi que los brazos levantados no
llevan contorno propio: serian bandas negras de 4 px. La silueta se define donde
empieza el blanco o el fondo.
"""

from ega import Canvas

FRAME_NAMES = ["idle", "brazo_izq", "brazo_der", "pecho_1", "pecho_2", "muerto"]

# Duracion sugerida por fotograma, en ms. El briefing solo fija el golpe de pecho.
FRAME_MS = {"pecho_1": 150, "pecho_2": 150}

CELL_W = CELL_H = 24
CUERPO_X, CUERPO_Y = 4, 4        # esquina del rectangulo 16x20
CUERPO_W, CUERPO_H = 16, 20

NEGRO, GRIS_CLARO, GRIS_OSC, BLANCO = 0, 7, 8, 15

# --- piezas ---------------------------------------------------------------

# Cabeza, 10x7, en (7,4). Es tan ancha como el torso: la cabeza grande respecto
# al cuerpo es parte de la silueta del panda, al reves que en el gorila, donde el
# briefing pedia cabeza pequena y hombros anchos.
_CABEZA = [
    ".00....00.",     # orejas
    "0000000000",     # las orejas se funden con el remate del craneo
    "0FFFFFFFF0",
    "0F00FF00F0",     # antifaces: dos manchas de 2 px con el puente en blanco
    "0F00FF00F0",
    "0FFF00FFF0",     # hocico
    "00FFFFFF00",
]

# Fila de hombros, 12 px de ancho, en (6,11). Negra: es la banda que cruza el
# pecho del panda y la que separa la cabeza blanca de la barriga blanca.
_HOMBROS = "000000000000"

# Torso, 10 px de ancho, en (7,12). Banda negra arriba, barriga blanca con una
# sombra escalonada en gris claro que le da bulto sin sacar un cuarto tono.
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

_PIERNAS = [
    "0000..0000",
    "0000..0000",
    "0000000000",     # contacto con el tejado
]

# Brazo colgando, 3 px de ancho, en (4,12) el izquierdo. Negro con una linea de
# gris oscuro para que no sea un agujero plano; la zarpa, maciza.
_BRAZO_IZQ = [".00", "080", "080", "080", "080", "080", "000", "000", ".00"]
_BRAZO_DER = ["00.", "080", "080", "080", "080", "080", "000", "000", "00."]


def _base(c: Canvas, cabeza_dx=0):
    c.blit(Canvas.from_ascii(_CABEZA, 10, 7, "panda/cabeza"), 7 + cabeza_dx, 4)
    c.blit(Canvas.from_ascii([_HOMBROS], 12, 1, "panda/hombros"), 6, 11)
    c.blit(Canvas.from_ascii(_TORSO, 10, 9, "panda/torso"), 7, 12)
    c.blit(Canvas.from_ascii(_PIERNAS, 10, 3, "panda/piernas"), 7, 21)


def _brazo_colgando(c: Canvas, izq=True):
    pieza = _BRAZO_IZQ if izq else _BRAZO_DER
    c.blit(Canvas.from_ascii(pieza, 3, 9, "panda/brazo"), 4 if izq else 17, 12)


def _brazo_alto(c: Canvas, escalera, filas_zarpa):
    """Dibuja un brazo levantado.

    `escalera` es la lista de tramos (fila, x0, x1) del brazo, de abajo arriba.
    A diferencia del gorila no se dibuja contorno: el brazo ya es negro y
    rodearlo de negro lo engordaria a 4 px. Las filas de `filas_zarpa` van
    macizas; el resto lleva una linea de gris oscuro por el centro.
    """
    for fila, x0, x1 in escalera:
        for x in range(x0, x1 + 1):
            c.set(x, fila, NEGRO)
        if fila not in filas_zarpa:
            c.set((x0 + x1) // 2, fila, GRIS_OSC)


# --- fotogramas -----------------------------------------------------------

def _idle() -> Canvas:
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _brazo_colgando(c, izq=True)
    _brazo_colgando(c, izq=False)
    return c


def _brazo_izq() -> Canvas:
    """Brazo izquierdo en alto: acompana al proyectil que viaja a la izquierda."""
    c = Canvas(CELL_W, CELL_H)
    _base(c, cabeza_dx=-1)
    _brazo_colgando(c, izq=False)
    _brazo_alto(c, [(12, 4, 6), (11, 4, 6), (10, 3, 5), (9, 3, 5), (8, 2, 4),
                    (7, 2, 4), (6, 1, 3), (5, 1, 3), (4, 0, 2)],
                filas_zarpa={4, 5})
    return c


def _brazo_der() -> Canvas:
    """Espejo del anterior pero dibujado a mano: el brazo sube mas vertical
    (tres filas por escalon en vez de dos) y la cabeza se inclina al otro lado.
    Un volteo automatico delata la simetria y queda muerto."""
    c = Canvas(CELL_W, CELL_H)
    _base(c, cabeza_dx=1)
    _brazo_colgando(c, izq=True)
    _brazo_alto(c, [(12, 17, 19), (11, 17, 19), (10, 18, 20), (9, 18, 20),
                    (8, 18, 20), (7, 19, 21), (6, 19, 21), (5, 20, 22),
                    (4, 20, 22)],
                filas_zarpa={4, 5})
    return c


def _pecho_1() -> Canvas:
    """Zarpas arriba. La izquierda remata una fila antes que la derecha."""
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _brazo_alto(c, [(12, 4, 6), (11, 3, 5), (10, 2, 4), (9, 1, 4), (8, 1, 4)],
                filas_zarpa={8, 9})
    _brazo_alto(c, [(12, 17, 19), (11, 18, 20), (10, 19, 22), (9, 19, 22)],
                filas_zarpa={9, 10})
    return c


def _pecho_2() -> Canvas:
    """Zarpas contra el torso. Aqui el panda gana al gorila: los antebrazos son
    negros y la barriga blanca, de modo que el contraste los separa solo y no
    hace falta ni contorno ni cambio de tono inventado."""
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _brazo_colgando(c, izq=True)
    _brazo_colgando(c, izq=False)
    c.fill_rect(8, 17, 15, 19, BLANCO)      # la sombra de la barriga queda tapada
    for fila in (17, 18, 19):
        for x in range(4, 11):
            c.set(x, fila, NEGRO)
        for x in range(13, 20):
            c.set(x, fila, NEGRO)
    for x in (5, 6, 7):                     # brillo a lo largo del antebrazo
        c.set(x, 18, GRIS_OSC)
    for x in (16, 17, 18):
        c.set(x, 18, GRIS_OSC)
    return c


_MUERTO = [
    "....00....00....",     # orejas
    "...0000000000...",
    "...0FFFFFFFF0...",
    "...0F00FF00F0...",
    "...0F00FF00F0...",
    "...0FFF00FFF0...",
    "...00FFFFFF00...",
    "...0000000000...",     # hombros hundidos
    ".00000000000000.",
    "00000FFFFFF00000",     # brazos despatarrados sobre el tejado
    "0000FFFFFFFF0000",
    "0000000000000000",     # contacto con el tejado
]


def _muerto() -> Canvas:
    """Derrotado: desplomado sobre el tejado, la cabeza hundida entre los
    hombros y los brazos despatarrados.

    Se probaron dos versiones tumbadas de perfil: a 1x una queda como un bulto y
    la otra se lee como una fabrica con chimeneas. Conservar la silueta vertical
    y limitarse a aplastarla de 20 a 12 px es lo unico que se reconoce de un
    vistazo. Mismo borde inferior y mismos 16 px de ancho que el resto."""
    c = Canvas(CELL_W, CELL_H)
    c.blit(Canvas.from_ascii(_MUERTO, 16, 12, "panda/muerto"), CUERPO_X, 12)
    return c


def frames():
    return [_idle(), _brazo_izq(), _brazo_der(), _pecho_1(), _pecho_2(), _muerto()]
