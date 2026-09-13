#!/usr/bin/env python3
"""Genera todos los entregables de arte en art/.

    python3 tools/gen_sprites.py            # escribe art/
    python3 tools/gen_sprites.py --zoom 6   # ademas, contactos ampliados

Requiere Pillow (pip install pillow). Los PNG salen indexados (PNG-8) con un
unico indice totalmente transparente: por construccion no puede existir un pixel
con alfa intermedio.
"""

import argparse
import os

import art_banana
import art_boom
import art_fachadas
import art_gorila
import art_logo
import art_skyline
import art_sol
import ega
from ega import Canvas, hstrip, save_png

RAIZ = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ART = os.path.join(RAIZ, "art")
SPRITES = os.path.join(ART, "sprites")
PALETA = os.path.join(ART, "palette")

# Degradado de cielo: lo genera el motor por codigo, no se dibuja. Se entrega
# solo el par de colores, tomados de la EGA (1 azul arriba, 9 azul claro abajo),
# para que la silueta del skyline en azul 1 siga separandose del horizonte.
CIELO_ARRIBA = "#0000AA"
CIELO_ABAJO = "#5555FF"


def _gpl() -> str:
    lineas = ["GIMP Palette", "Name: Gorilas EGA 16", "Columns: 16",
              "# Paleta cerrada del proyecto. El orden es normativo: el indice",
              "# de cada color es el que usan los ficheros art_*.py.", "#"]
    for i, (r, g, b) in enumerate(ega.EGA):
        lineas.append(f"{r:3d} {g:3d} {b:3d}\t{i:2d} {ega.EGA_NAMES[i]}")
    return "\n".join(lineas) + "\n"


def _zoom(c: Canvas, factor: int, rejilla=None) -> Canvas:
    """Amplia por vecino mas proximo. `rejilla` = ancho de celda para marcar
    limites de fotograma con una linea de 1 px."""
    z = Canvas(c.w * factor, c.h * factor)
    for y in range(c.h):
        for x in range(c.w):
            v = c.px[y][x]
            if v == ega.T:
                continue
            for dy in range(factor):
                for dx in range(factor):
                    z.set(x * factor + dx, y * factor + dy, v)
    if rejilla:
        for n in range(1, c.w // rejilla):
            for y in range(z.h):
                if y % 2 == 0:
                    z.set(n * rejilla * factor, y, 5)
    return z


PIEZAS = []


def pieza(nombre, canvas, celda=None):
    PIEZAS.append((nombre, canvas, celda))
    return canvas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zoom", type=int, default=0,
                    help="escribe ademas contactos ampliados en art/preview/")
    args = ap.parse_args()

    os.makedirs(SPRITES, exist_ok=True)
    os.makedirs(PALETA, exist_ok=True)

    pieza("gorila", hstrip(art_gorila.frames(), 24, 24, "gorila"), 24)
    pieza("banana", hstrip(art_banana.frames(), 8, 8, "banana"), 8)
    pieza("boom", hstrip(art_boom.frames(), 32, 32, "boom"), 32)
    pieza("sol", hstrip(art_sol.frames(), 20, 20, "sol"), 20)
    pieza("fachadas", hstrip(art_fachadas.frames(), 16, 16, "fachadas"), 16)
    pieza("skyline", art_skyline.build())
    pieza("logo", art_logo.build())

    for nombre, canvas, _ in PIEZAS:
        ruta = os.path.join(SPRITES, nombre + ".png")
        save_png(canvas, ruta)
        print(f"{ruta}  {canvas.w}x{canvas.h}  {len(canvas.colors())} colores")

    # Entregable 0: la paleta.
    with open(os.path.join(PALETA, "paleta_gorilas.gpl"), "w") as f:
        f.write(_gpl())
    swatch = Canvas(16, 1)
    for i in range(16):
        swatch.set(i, 0, i)
    save_png(swatch, os.path.join(PALETA, "paleta_gorilas.png"))
    save_png(_zoom(swatch, 16), os.path.join(PALETA, "paleta_gorilas_x16.png"))

    with open(os.path.join(ART, "cielo.txt"), "w") as f:
        f.write(f"# Degradado de cielo. Lo interpola el motor por codigo.\n"
                f"arriba={CIELO_ARRIBA}\nabajo={CIELO_ABAJO}\n")

    if args.zoom:
        prev = os.path.join(ART, "preview")
        os.makedirs(prev, exist_ok=True)
        for nombre, canvas, celda in PIEZAS:
            save_png(_zoom(canvas, args.zoom, celda),
                     os.path.join(prev, f"{nombre}_x{args.zoom}.png"))
        print(f"contactos ampliados en {prev}")


if __name__ == "__main__":
    main()
