"""fachadas.png -- 5 celdas de 16x16. No son sprites: son muestras de color.

El motor solo lee cuatro pixeles por celda:
    (0,0) base de la fachada   (1,0) ventana encendida
    (2,0) ventana apagada      (3,0) contorno del edificio

El resto de la celda es libre. Aqui se usa para previsualizar la combinacion
montando un trozo de edificio con esos cuatro colores, de modo que al abrir el
PNG se ve lo que el motor va a generar sin tener que compilar nada. La fila 0 a
partir de x=4 se rellena con el color base para no pisar los cuatro pixeles
clave.

Restriccion de legibilidad, reescrita al cambiar el tema. El briefing prohibia la
base amarilla porque el proyectil era un platano; el proyectil es ahora una cana
de bambu, asi que **lo prohibido es el verde**: ninguna base puede ser verde (2)
ni verde claro (10), o la cana desaparece al pasar por delante.

Y al reves: las cinco variantes vuelven a encender las ventanas en amarillo, como
pedia la referencia del original. Con el platano hubo que pasar tres a blanco
porque se lo tragaban; con el bambu verde el amarillo ya no estorba, y el blanco
ha pasado a ser el color malo: el panda es blanco y una fachada salpicada de
ventanas blancas de 3x4 lo camufla. Se comprueba montando
tools/preview_escena.py.

La quinta variante es marron y no azul: azul (1) es el tono de la silueta del
skyline, y un edificio jugable del mismo color que el fondo se pierde.
"""

from ega import Canvas

CELL = (16, 16)

# (nombre, base, ventana encendida, ventana apagada, contorno)
VARIANTES = [
    ("cian",    3, 14, 1, 0),
    ("rojo",    4, 14, 0, 8),
    ("gris",    8, 14, 1, 0),
    ("magenta", 5, 14, 1, 0),
    ("marron",  6, 14, 1, 0),
]

BLANCO = 15
VERDES = (2, 10)

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
        if base in VERDES:
            raise ValueError(f"{nombre}: base verde, la cana de bambu desapareceria")
        if base == BLANCO:
            raise ValueError(f"{nombre}: base blanca, el panda desapareceria")
        out.append(_celda(n, base, on, off, borde))
    return out
