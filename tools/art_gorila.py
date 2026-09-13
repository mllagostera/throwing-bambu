"""gorila.png -- 6 fotogramas de 24x24, pivote (12, 24).

El cuerpo ocupa exactamente 16 x 20 px en el rectangulo x=4..19, y=4..23, que es
la caja de colision del motor. El margen (4 px arriba y 4 px a cada lado) existe
solo para absorber brazos levantados y punos; verify_assets.py falla si algun
fotograma engorda el cuerpo dentro de esa caja.

Tonos: 8 (gris oscuro) pelaje, 7 (gris claro) cara, pecho y punos, 0 (negro)
contorno. El negro envuelve tambien la silueta exterior, no solo las lineas
internas: el gorila se ve sobre cielo degradado y sobre cinco colores de fachada
distintos, y sin contorno exterior se pierde contra las variantes claras.

Los fotogramas se componen a partir de piezas (cabeza, torso, piernas, brazo
colgando, brazo en alto) en lugar de escribirse como bloques ASCII de 24x24:
asi una pose nueva no puede desalinear el cuerpo respecto a la caja de colision.
"""

from ega import Canvas

FRAME_NAMES = ["idle", "brazo_izq", "brazo_der", "pecho_1", "pecho_2", "muerto"]

# Duracion sugerida por fotograma, en ms. El briefing solo fija el golpe de pecho.
FRAME_MS = {"pecho_1": 150, "pecho_2": 150}

CELL_W = CELL_H = 24
CUERPO_X, CUERPO_Y = 4, 4        # esquina del rectangulo 16x20
CUERPO_W, CUERPO_H = 16, 20

NEGRO, CLARO, OSCURO = 0, 7, 8

# --- piezas ---------------------------------------------------------------

# Cabeza, 8x7. Se apoya en (8,4) del lienzo; las poses de lanzamiento la
# desplazan 1 px al lado del brazo que sube, que es lo que da la inclinacion.
_CABEZA = [
    ".000000.",
    "08888880",     # pelaje de la frente
    "08777780",     # la cara es una mancha clara de 4x4...
    "08077080",     # ...con los dos ojos dentro
    "08777780",
    "08700780",     # ...y la boca. Tres rasgos: no cabe un cuarto
    "00888800",     # mandibula
]

# Fila de hombros, 12 px de ancho, en (6,11). Un escalon mas ancho que el torso
# para que la silueta no sea un rectangulo.
_HOMBROS = "008888888800"

# Torso, 10 px de ancho, en (7,12). La mancha clara del pecho lleva los bordes
# superior e inferior en tablero de ajedrez, no un tono intermedio inventado.
_TORSO = [
    "0887777880",
    "0887777880",
    "0887777880",
    "0888778880",
    "0888888880",
    "0888888880",
    "0888888880",
    "0888888880",
    "0888888880",
]

_PIERNAS = [
    "0888008880",
    "0888008880",
    "0000000000",
]

# Brazo colgando, 3 px de ancho, en (4,12) el izquierdo. Arranca estrecho para
# rematar el hombro redondeado y acaba en puno claro.
_BRAZO_IZQ = [".08", "088", "088", "088", "088", "088", "077", "077", ".00"]
_BRAZO_DER = ["80.", "880", "880", "880", "880", "880", "770", "770", "00."]


def _base(c: Canvas, cabeza_dx=0):
    c.blit(Canvas.from_ascii(_CABEZA, 8, 7, "gorila/cabeza"), 8 + cabeza_dx, 4)
    c.blit(Canvas.from_ascii([_HOMBROS], 12, 1, "gorila/hombros"), 6, 11)
    c.blit(Canvas.from_ascii(_TORSO, 10, 9, "gorila/torso"), 7, 12)
    c.blit(Canvas.from_ascii(_PIERNAS, 10, 3, "gorila/piernas"), 7, 21)


def _brazo_colgando(c: Canvas, izq=True):
    pieza = _BRAZO_IZQ if izq else _BRAZO_DER
    c.blit(Canvas.from_ascii(pieza, 3, 9, "gorila/brazo"), 4 if izq else 17, 12)


def _brazo_alto(c: Canvas, escalera, filas_puno):
    """Dibuja un brazo levantado.

    `escalera` es la lista de tramos (fila, x0, x1) del pelaje, de abajo arriba.
    El contorno negro (un pixel a cada lado y un remate encima) se deduce del
    propio tramo, asi que cambiar el angulo del brazo no obliga a redibujar el
    contorno a mano.
    """
    for fila, x0, x1 in escalera:
        c.set(x0 - 1, fila, NEGRO)
        c.set(x1 + 1, fila, NEGRO)
    for fila, x0, x1 in escalera:
        tono = CLARO if fila in filas_puno else OSCURO
        for x in range(x0, x1 + 1):
            c.set(x, fila, tono)
    fila, x0, x1 = escalera[-1]
    for x in range(x0, x1 + 1):
        c.set(x, fila - 1, NEGRO)


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
    _brazo_alto(c, [(12, 5, 6), (11, 4, 5), (10, 4, 5), (9, 3, 4), (8, 3, 4),
                    (7, 2, 3), (6, 2, 3), (5, 1, 2), (4, 1, 2)],
                filas_puno={4, 5})
    return c


def _brazo_der() -> Canvas:
    """Espejo del anterior pero dibujado a mano: el brazo sube mas vertical
    (tres filas por escalon en vez de dos), el puno es de 2 px en lugar de 3 y la
    cabeza se inclina al otro lado. Un volteo automatico delata la simetria."""
    c = Canvas(CELL_W, CELL_H)
    _base(c, cabeza_dx=1)
    _brazo_colgando(c, izq=True)
    _brazo_alto(c, [(12, 17, 18), (11, 17, 18), (10, 18, 19), (9, 18, 19),
                    (8, 18, 19), (7, 19, 20), (6, 19, 20), (5, 20, 21),
                    (4, 20, 21)],
                filas_puno={4, 5})
    return c


def _pecho_1() -> Canvas:
    """Punos arriba. El puno izquierdo remata una fila antes que el derecho."""
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _brazo_alto(c, [(12, 5, 6), (11, 4, 5), (10, 3, 4), (9, 2, 4), (8, 2, 4)],
                filas_puno={8, 9})
    _brazo_alto(c, [(12, 17, 18), (11, 18, 19), (10, 19, 21), (9, 19, 21)],
                filas_puno={9, 10})
    return c


def _pecho_2() -> Canvas:
    """Punos contra el torso. Los antebrazos cruzan el pecho en gris claro sobre
    el pelaje oscuro: a esta escala el contraste de tono separa brazo de torso
    mejor que una linea negra de 14 px de ancho."""
    c = Canvas(CELL_W, CELL_H)
    _base(c)
    _brazo_colgando(c, izq=True)
    _brazo_colgando(c, izq=False)
    # El pecho queda tapado: se rellena de pelaje antes de posar los antebrazos.
    c.fill_rect(8, 17, 15, 19, OSCURO)
    for fila in (17, 18, 19):
        c.set(4, fila, NEGRO)
        c.set(19, fila, NEGRO)
        for x in range(5, 11):
            c.set(x, fila, CLARO)
        for x in range(13, 19):
            c.set(x, fila, CLARO)
        c.set(11, fila, OSCURO)
        c.set(12, fila, OSCURO)
    c.set(9, 18, NEGRO)      # nudillos
    c.set(14, 18, NEGRO)
    return c


_MUERTO = [
    ".....000000.....",
    "....08888880....",
    "....08777780....",
    "....08077080....",     # ojos
    "....08000080....",     # boca recta: mueca, no sonrisa
    "....08777780....",
    "....00888800....",
    "...0888888880...",     # hombros hundidos
    ".00088888888000.",
    "0778888888888770",     # brazos despatarrados, punos claros en los extremos
    "0888888888888880",
    "0000000000000000",     # contacto con el tejado
]


def _muerto() -> Canvas:
    """Derrotado: desplomado sobre el tejado, la cabeza hundida entre los
    hombros y los brazos despatarrados sobre el suelo.

    Se probaron dos versiones tumbadas de perfil (16x9 y con las patas al aire):
    a 1x la primera queda como un bulto y la segunda se lee como una fabrica con
    chimeneas. Conservar la silueta vertical del gorila y limitarse a aplastarla
    de 20 a 12 px es lo unico que se reconoce de un vistazo. Mismo borde inferior
    y mismos 16 px de ancho que el resto de la tira."""
    c = Canvas(CELL_W, CELL_H)
    c.blit(Canvas.from_ascii(_MUERTO, 16, 12, "gorila/muerto"), CUERPO_X, 12)
    return c


def frames():
    return [_idle(), _brazo_izq(), _brazo_der(), _pecho_1(), _pecho_2(), _muerto()]
