#!/usr/bin/env python3
"""Monta una escena de juego falsa a 1x con todas las piezas.

El criterio de aceptacion que mas piezas tumba (seccion 12 del briefing) es
«se lee al 100 % de zoom en una captura real del juego». Sin motor todavia, esto
es lo mas parecido: cielo degradado por codigo, skyline recortado, edificios
generados con las muestras de fachadas.png, gorilas sobre los tejados, platano en
vuelo y explosion. Se escribe a 1x y tambien ampliado, pero el que vale es el 1x.

    python3 tools/preview_escena.py
"""

import os

from PIL import Image

import art_banana
import art_boom
import art_fachadas
import art_gorila
import art_skyline
import art_sol
import ega
from ega import T
from gen_sprites import ART, CIELO_ABAJO, CIELO_ARRIBA

ANCHO, ALTO = 400, 200
SUELO = ALTO
# La banda del skyline se ancla por encima de la base de los edificios, no en el
# borde inferior de la pantalla: pegada abajo queda tapada por completo.
SKYLINE_BASE = ALTO - 45


def _hex(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def _pegar(img, canvas, ox, oy):
    px = img.load()
    for y in range(canvas.h):
        for x in range(canvas.w):
            v = canvas.px[y][x]
            if v == T:
                continue
            ix, iy = ox + x, oy + y
            if 0 <= ix < img.width and 0 <= iy < img.height:
                px[ix, iy] = ega.EGA[v]


def _cielo(img):
    arriba, abajo = _hex(CIELO_ARRIBA), _hex(CIELO_ABAJO)
    px = img.load()
    for y in range(ALTO):
        t = y / (ALTO - 1)
        col = tuple(round(a + (b - a) * t) for a, b in zip(arriba, abajo))
        for x in range(ANCHO):
            px[x, y] = col


def _edificios(img, muestras):
    """Genera edificios como haria el motor: solo con los cuatro pixeles clave."""
    px = img.load()
    anchos = [58, 71, 52, 66, 60, 55, 48]
    alturas = [96, 62, 128, 74, 110, 58, 88]
    tejados = []
    x = 0
    for n, (w, h) in enumerate(zip(anchos, alturas)):
        m = muestras[n % len(muestras)]
        base, on, off, borde = (m.get(0, 0), m.get(1, 0), m.get(2, 0), m.get(3, 0))
        top = SUELO - h
        for yy in range(top, SUELO):
            for xx in range(x, min(x + w, ANCHO)):
                en_borde = xx in (x, x + w - 1) or yy == top
                px[xx, yy] = ega.EGA[borde if en_borde else base]
        # Ventanas de 3x4 con 2 px de separacion.
        for wy in range(top + 4, SUELO - 5, 6):
            for wx in range(x + 3, x + w - 5, 5):
                enc = ((wx // 5) * 7 + (wy // 6) * 3 + n) % 5 < 2
                for dy in range(4):
                    for dx in range(3):
                        if wx + dx < ANCHO:
                            px[wx + dx, wy + dy] = ega.EGA[on if enc else off]
        tejados.append((x + w // 2, top))
        x += w
        if x >= ANCHO:
            break
    return tejados


def main():
    img = Image.new("RGB", (ANCHO, ALTO))
    _cielo(img)

    sky = art_skyline.build()
    recorte = ega.Canvas(ANCHO, sky.h)
    for y in range(sky.h):
        for x in range(ANCHO):
            recorte.px[y][x] = sky.px[y][x]
    _pegar(img, recorte, 0, SKYLINE_BASE - sky.h)

    _pegar(img, art_sol.frames()[0], 186, 14)

    tejados = _edificios(img, art_fachadas.frames())

    gor = art_gorila.frames()
    _pegar(img, gor[1], tejados[1][0] - 12, tejados[1][1] - 24)
    _pegar(img, gor[0], tejados[4][0] - 12, tejados[4][1] - 24)
    _pegar(img, gor[5], tejados[6][0] - 12, tejados[6][1] - 24)

    _pegar(img, art_banana.frames()[1], 150, 60)
    _pegar(img, art_banana.frames()[3], 196, 46)
    _pegar(img, art_boom.frames()[3], tejados[3][0] - 16, tejados[3][1] - 8)

    prev = os.path.join(ART, "preview")
    os.makedirs(prev, exist_ok=True)
    uno = os.path.join(prev, "escena_1x.png")
    img.save(uno)
    img.resize((ANCHO * 3, ALTO * 3), Image.NEAREST).save(
        os.path.join(prev, "escena_x3.png"))
    print(uno)


if __name__ == "__main__":
    main()
