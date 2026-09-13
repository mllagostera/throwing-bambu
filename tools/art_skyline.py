"""skyline.png -- 460 x 80, una sola capa, sin paralaje.

El motor recorta la banda a la anchura real del lienzo (320-460 px), siempre por
la derecha, asi que el diseno es deliberadamente uniforme: no hay composicion
centrada ni ningun edificio memorable que aparezca solo en pantallas anchas. Si
un jugador lo ve en 460 y otro en 330, ven el mismo fondo.

Silueta plana en azul (1), sin volumen, anclada al borde inferior. Los puntos de
ventana van en azul claro (9), no en amarillo: el platano es amarillo y un fondo
salpicado de puntos amarillos compite con el proyectil justo donde hay que
seguirlo con la vista.

La distribucion se genera con un LCG de semilla fija, asi que el PNG es
reproducible bit a bit desde este fichero.
"""

from ega import Canvas

WIDTH, HEIGHT = 460, 80
SILUETA, VENTANA = 1, 9

BASE_Y = HEIGHT - 1          # la banda se apoya en el borde inferior
ALTURA_MIN, ALTURA_MAX = 11, 23      # deja despejados los 57 px superiores
ANCHO_MIN, ANCHO_MAX = 7, 24
SEED = 0x60411A5             # "GORILAS" en hex aproximado; fijo a proposito


class _Lcg:
    """Generador congruencial lineal minimo, para que el resultado no dependa
    de la version de Python ni del modulo random."""

    def __init__(self, seed: int):
        self.s = seed & 0xFFFFFFFF

    def next(self) -> int:
        self.s = (1664525 * self.s + 1013904223) & 0xFFFFFFFF
        return self.s

    def rango(self, lo: int, hi: int) -> int:
        return lo + self.next() % (hi - lo + 1)


def build() -> Canvas:
    c = Canvas(WIDTH, HEIGHT)
    rnd = _Lcg(SEED)
    x = 0
    n = 0
    h_prev = 0
    while x < WIDTH:
        w = rnd.rango(ANCHO_MIN, ANCHO_MAX)
        # Dos edificios contiguos de altura parecida se fusionan en un bloque
        # plano enorme: se exige un escalon de al menos 3 px.
        for _ in range(16):
            h = rnd.rango(ALTURA_MIN, ALTURA_MAX)
            if abs(h - h_prev) >= 3:
                break
        h_prev = h
        top = BASE_Y - h + 1
        c.fill_rect(x, top, min(x + w - 1, WIDTH - 1), BASE_Y, SILUETA)

        # Una antena cada pocos edificios. 1 px de ancho y nunca por encima de
        # y=48, para no invadir el arco alto de las trayectorias.
        if n % 5 == 3:
            ax = x + w // 2
            atop = max(48, top - rnd.rango(4, 9))
            c.fill_rect(ax, atop, ax, top, SILUETA)

        # Ventanas: rejilla de 1 px cada 3 px, encendidas de forma dispersa.
        for wy in range(top + 2, BASE_Y - 1, 3):
            for wx in range(x + 2, x + w - 2, 3):
                if rnd.next() % 7 == 0:
                    c.set(wx, wy, VENTANA)

        x += w
        n += 1

    # Zocalo continuo: sin el, dos edificios contiguos de la misma altura dejan
    # columnas vacias y la ciudad se lee con agujeros de cielo hasta el suelo.
    c.fill_rect(0, BASE_Y - 2, WIDTH - 1, BASE_Y, SILUETA)
    return c
