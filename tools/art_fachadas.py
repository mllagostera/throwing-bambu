"""fachadas.png -- 5 celdas de 16x16. No son sprites: son muestras de color.

El motor solo lee cuatro pixeles por celda:
    (0,0) base de la fachada   (1,0) ventana encendida
    (2,0) ventana apagada      (3,0) contorno del edificio

El resto de la celda es libre. Aqui se usa para previsualizar la combinacion
montando un trozo de edificio con esos cuatro colores, de modo que al abrir el
PNG se ve lo que el motor va a generar sin tener que compilar nada. La fila 0 a
partir de x=4 se rellena con el color base para no pisar los cuatro pixeles
clave.

Restriccion de legibilidad: el platano es amarillo, asi que ninguna base puede
ser amarilla. La ventana encendida si, porque mide 3x4 px.

La quinta variante es marron y no azul: azul (1) es el tono de la silueta del
skyline, y un edificio jugable del mismo color que el fondo se pierde.

Solo dos variantes encienden las ventanas en amarillo; las otras tres las
encienden en blanco. Montando la escena de prueba (tools/preview_escena.py) con
las cinco en amarillo, una fachada llena de ventanas amarillas de 3x4 se traga el
platano, que mide 8x8 y es del mismo color. El blanco conserva la lectura de
«ventana encendida» sin disputarle el color al proyectil.
"""

from ega import Canvas

CELL = (16, 16)

# (nombre, base, ventana encendida, ventana apagada, contorno)
VARIANTES = [
    ("cian",    3, 15, 1, 0),
    ("rojo",    4, 14, 0, 8),
    ("gris",    8, 14, 1, 0),
    ("magenta", 5, 15, 1, 0),
    ("marron",  6, 15, 1, 0),
]

AMARILLO = 14

_WIN_X = (2, 6, 10)     # ventanas de 3 px de ancho
_WIN_Y = (2, 7)         # ventanas de 4 px de alto


def _celda(n: int, base: int, on: int, off: int, borde: int) -> Canvas:
    c = Canvas(16, 16, fill=base)
    # Contorno del edificio: laterales y remate inferior.
    for y in range(16):
        c.set(0, y, borde)
        c.set(15, y, borde)
    for x in range(16):
        c.set(x, 15, borde)
    # Rejilla de ventanas, encendidas y apagadas en tablero.
    for fy, wy in enumerate(_WIN_Y):
        for fx, wx in enumerate(_WIN_X):
            col = on if (fx + fy + n) % 2 == 0 else off
            c.fill_rect(wx, wy, wx + 2, wy + 3, col)
    # Los cuatro pixeles clave se escriben al final: manda el contrato, no la
    # previsualizacion.
    c.set(0, 0, base)
    c.set(1, 0, on)
    c.set(2, 0, off)
    c.set(3, 0, borde)
    for x in range(4, 16):
        c.set(x, 0, base)
    return c


def frames():
    out = []
    for n, (nombre, base, on, off, borde) in enumerate(VARIANTES):
        if base == AMARILLO:
            raise ValueError(f"{nombre}: base amarilla, el platano desapareceria")
        out.append(_celda(n, base, on, off, borde))
    return out
