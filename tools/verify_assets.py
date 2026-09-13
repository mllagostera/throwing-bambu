#!/usr/bin/env python3
"""Comprueba art/ contra los criterios de aceptacion del briefing (seccion 12).

Lee los PNG entregados, no los generadores: lo que se valida es el fichero que
va a consumir el motor.

    python3 tools/verify_assets.py        # devuelve 1 si algo falla

Comprueba, pieza por pieza:
  - solo colores de la paleta EGA, maximo 16 distintos;
  - ningun pixel con alfa entre 1 y 254;
  - dimensiones de tira y de celda exactas;
  - el pivote declarado cae donde dice el documento;
  - y las restricciones especificas: caja de colision del panda, radio del
    fotograma de pico de la explosion, pixeles clave de las fachadas (ninguna
    base verde, que se comeria la cana, ni blanca, que se comeria al panda) y
    recorte del skyline.
"""

import math
import os
import sys

from PIL import Image

RAIZ = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ART = os.path.join(RAIZ, "art")
SPRITES = os.path.join(ART, "sprites")

EGA = {
    (0x00, 0x00, 0x00), (0x00, 0x00, 0xAA), (0x00, 0xAA, 0x00), (0x00, 0xAA, 0xAA),
    (0xAA, 0x00, 0x00), (0xAA, 0x00, 0xAA), (0xAA, 0x55, 0x00), (0xAA, 0xAA, 0xAA),
    (0x55, 0x55, 0x55), (0x55, 0x55, 0xFF), (0x55, 0xFF, 0x55), (0x55, 0xFF, 0xFF),
    (0xFF, 0x55, 0x55), (0xFF, 0x55, 0xFF), (0xFF, 0xFF, 0x55), (0xFF, 0xFF, 0xFF),
}

# fichero -> (ancho, alto, ancho de celda, alto de celda, n fotogramas)
INVENTARIO = {
    "panda.png":    (144, 24, 24, 24, 6),
    "bambu.png":    (32, 8, 8, 8, 4),
    "boom.png":     (256, 32, 32, 32, 8),
    "sol.png":      (40, 20, 20, 20, 2),
    "fachadas.png": (80, 16, 16, 16, 5),
    "skyline.png":  (460, 80, None, None, 1),
    "logo.png":     (200, 60, None, None, 1),
}

fallos = []
avisos = []


def fallo(msg):
    fallos.append(msg)


def aviso(msg):
    avisos.append(msg)


def cargar(nombre):
    img = Image.open(os.path.join(SPRITES, nombre)).convert("RGBA")
    return img, img.load()


def opacos(px, w, h, x0=0, y0=0, x1=None, y1=None):
    x1 = w - 1 if x1 is None else x1
    y1 = h - 1 if y1 is None else y1
    out = []
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r, g, b, a = px[x, y]
            if a:
                out.append((x, y, (r, g, b)))
    return out


def bbox(puntos):
    if not puntos:
        return None
    xs = [p[0] for p in puntos]
    ys = [p[1] for p in puntos]
    return min(xs), min(ys), max(xs), max(ys)


def comunes(nombre):
    """Paleta, alfa binario y dimensiones. Devuelve (img, px)."""
    ruta = os.path.join(SPRITES, nombre)
    if not os.path.exists(ruta):
        fallo(f"{nombre}: no existe")
        return None, None
    img, px = cargar(nombre)
    w, h, cw, ch, n = INVENTARIO[nombre]
    if (img.width, img.height) != (w, h):
        fallo(f"{nombre}: lienzo {img.width}x{img.height}, declarado {w}x{h}")
    if cw and img.width != cw * n:
        fallo(f"{nombre}: {img.width} px no son {n} celdas de {cw}")

    alfas, colores = set(), set()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            alfas.add(a)
            if a:
                colores.add((r, g, b))
    parciales = sorted(a for a in alfas if 0 < a < 255)
    if parciales:
        fallo(f"{nombre}: alfa intermedio {parciales}")
    fuera = colores - EGA
    if fuera:
        fallo(f"{nombre}: colores fuera de la paleta {sorted(fuera)}")
    if len(colores) > 16:
        fallo(f"{nombre}: {len(colores)} colores unicos, maximo 16")
    print(f"  {nombre:<13} {img.width:>3}x{img.height:<3} "
          f"{len(colores)} colores  alfa {sorted(alfas)}")
    return img, px


def check_panda():
    img, px = comunes("panda.png")
    if not img:
        return
    nombres = ["idle", "brazo_izq", "brazo_der", "pecho_1", "pecho_2", "muerto"]
    for n, nom in enumerate(nombres):
        ox = n * 24
        pts = opacos(px, img.width, img.height, ox, 0, ox + 23, 23)
        if not pts:
            fallo(f"panda/{nom}: fotograma vacio")
            continue
        x0, y0, x1, y1 = bbox(pts)
        x0, x1 = x0 - ox, x1 - ox
        # El pivote (12,24) se apoya en el tejado: todo fotograma toca y=23.
        if y1 != 23:
            fallo(f"panda/{nom}: no se apoya en el borde inferior (y max {y1})")
        # La caja de colision es 16x20 en x=4..19: nada del cuerpo puede salirse
        # de ella salvo brazos y punos, que el briefing autoriza en el margen.
        dentro = [p for p in pts if 4 <= p[0] - ox <= 19 and p[1] >= 4]
        if len(dentro) < len(pts) * 0.6:
            fallo(f"panda/{nom}: la mayor parte del cuerpo cae fuera de 16x20")
        if nom == "idle" and (x0, y0, x1, y1) != (4, 4, 19, 23):
            fallo(f"panda/idle: caja {x0},{y0},{x1},{y1}, "
                  f"debe ser exactamente 4,4,19,23 (16x20)")
    print("  panda: 6 fotogramas apoyados en el pivote (12,24)")


def _centrado(nombre, pts, ox, cx, cy, tol, etiqueta):
    x0, y0, x1, y1 = bbox(pts)
    mx, my = ((x0 - ox) + (x1 - ox)) / 2.0, (y0 + y1) / 2.0
    if abs(mx - cx) > tol or abs(my - cy) > tol:
        fallo(f"{nombre}/{etiqueta}: centro ({mx:.1f},{my:.1f}), "
              f"pivote ({cx},{cy}) con tolerancia {tol}")


def check_bambu():
    img, px = comunes("bambu.png")
    if not img:
        return
    for n in range(4):
        ox = n * 8
        pts = opacos(px, img.width, img.height, ox, 0, ox + 7, 7)
        _centrado("bambu", pts, ox, 3.5, 3.5, 0.5, f"f{n}")
    print("  bambu: 4 fotogramas centrados en el pivote (4,4)")


def check_boom():
    img, px = comunes("boom.png")
    if not img:
        return
    radios = []
    for n in range(8):
        ox = n * 32
        pts = opacos(px, img.width, img.height, ox, 0, ox + 31, 31)
        r = max(math.hypot((x - ox) - 15.5, y - 15.5) for x, y, _ in pts)
        radios.append(r)
        _centrado("boom", pts, ox, 15.5, 15.5, 1.5, f"f{n}")
    if not (radios[0] < radios[1] < radios[2] < radios[3]):
        fallo(f"boom: los fotogramas 0-3 no crecen de forma monotona: "
              f"{[round(r, 1) for r in radios[:4]]}")
    if radios[3] < 14.0:
        fallo(f"boom/f3: radio {radios[3]:.1f}, el pico debe llegar a 15")
    if radios[3] < 12 + 2.5:
        fallo(f"boom/f3: radio {radios[3]:.1f} no sobrepasa el crater "
              f"(radio 12) en unos 3 px; el impacto se percibe flojo")
    print("  boom: radios " + " ".join(f"{r:.1f}" for r in radios) +
          "  (crater 12 en el fotograma 3)")


def check_sol():
    img, px = comunes("sol.png")
    if not img:
        return
    for n, nom in enumerate(["normal", "ouch"]):
        ox = n * 20
        pts = opacos(px, img.width, img.height, ox, 0, ox + 19, 19)
        _centrado("sol", pts, ox, 9.5, 9.5, 0.5, nom)
    print("  sol: 2 fotogramas centrados en el pivote (10,10)")


def check_fachadas():
    img, px = comunes("fachadas.png")
    if not img:
        return
    amarillo = (0xFF, 0xFF, 0x55)
    blanco = (0xFF, 0xFF, 0xFF)
    verdes = {(0x00, 0xAA, 0x00), (0x55, 0xFF, 0x55)}
    vistos = []
    for n in range(5):
        ox = n * 16
        clave = [px[ox + i, 0][:3] for i in range(4)]
        base, on, off, borde = clave
        for c in clave:
            if c not in EGA:
                fallo(f"fachadas/{n}: pixel clave fuera de paleta {c}")
        if base in verdes:
            fallo(f"fachadas/{n}: base verde; la cana de bambu desapareceria")
        if base == blanco:
            fallo(f"fachadas/{n}: base blanca; el panda desapareceria")
        if base in (on, off, borde):
            fallo(f"fachadas/{n}: la base no contrasta con ventana o contorno")
        if on == off:
            fallo(f"fachadas/{n}: ventana encendida y apagada del mismo color")
        vistos.append(base)
    if len(set(vistos)) != 5:
        fallo("fachadas: hay variantes con el mismo color base")
    n_blancas = sum(1 for n in range(5) if px[n * 16 + 1, 0][:3] == blanco)
    if n_blancas > 2:
        aviso(f"fachadas: {n_blancas} variantes encienden las ventanas en "
              f"blanco; camuflan al panda, que es blanco")
    n_amarillas = sum(1 for n in range(5) if px[n * 16 + 1, 0][:3] == amarillo)
    print(f"  fachadas: 5 bases distintas, {n_amarillas} con ventana amarilla, "
          f"{n_blancas} con ventana blanca")


def check_skyline():
    img, px = comunes("skyline.png")
    if not img:
        return
    tops = []
    for x in range(img.width):
        col = [y for y in range(img.height) if px[x, y][3]]
        if not col:
            fallo(f"skyline: columna {x} vacia; el fondo se ve agujereado")
            tops.append(img.height)
        else:
            tops.append(min(col))
    # Debe funcionar recortado por la derecha en cualquier punto entre 320 y 460.
    for ancho in (320, 360, 400, 460):
        recorte = tops[:ancho]
        if max(recorte) - min(recorte) < 8:
            fallo(f"skyline: recortado a {ancho} px la silueta queda plana")
    sobre60 = sum(1 for y in range(60) for x in range(img.width) if px[x, y][3])
    pct = 100.0 * sobre60 / (60 * img.width)
    if pct > 3.0:
        fallo(f"skyline: {pct:.1f} % de los 60 px superiores ocupado; "
              f"deben quedar razonablemente despejados")
    llano = mayor = 1
    for i in range(1, len(tops)):
        llano = llano + 1 if tops[i] == tops[i - 1] else 1
        mayor = max(mayor, llano)
    if mayor > 30:
        aviso(f"skyline: meseta plana de {mayor} px; se lee como un solo bloque")
    print(f"  skyline: perfil {min(tops)}-{max(tops)}, "
          f"{pct:.1f} % sobre y=60, meseta maxima {mayor} px")


def check_logo():
    img, px = comunes("logo.png")
    if not img:
        return
    pts = opacos(px, img.width, img.height)
    x0, y0, x1, y1 = bbox(pts)
    if (x1 - x0 + 1) > img.width - 4 or (y1 - y0 + 1) > img.height - 4:
        aviso("logo: el rotulo llega al borde del lienzo, sin margen")
    print(f"  logo: caja {x0},{y0}-{x1},{y1} en {img.width}x{img.height}")


def check_extras():
    gpl = os.path.join(ART, "palette", "paleta_ega16.gpl")
    if not os.path.exists(gpl):
        fallo("falta art/palette/paleta_ega16.gpl")
    else:
        n = sum(1 for l in open(gpl) if l[:1].isdigit() or l[:1] == " ")
        if n != 16:
            fallo(f"paleta_ega16.gpl: {n} colores, deben ser 16")
    cielo = os.path.join(ART, "cielo.txt")
    if not os.path.exists(cielo):
        fallo("falta art/cielo.txt")
    else:
        txt = open(cielo).read()
        if "arriba=" not in txt or "abajo=" not in txt:
            fallo("cielo.txt: faltan las claves arriba= y abajo=")
    print("  paleta_ega16.gpl y cielo.txt presentes")


def main():
    print("Verificando art/ contra los criterios de aceptacion\n")
    check_panda()
    check_bambu()
    check_boom()
    check_sol()
    check_fachadas()
    check_skyline()
    check_logo()
    check_extras()
    print()
    for a in avisos:
        print(f"AVISO  {a}")
    for f in fallos:
        print(f"FALLO  {f}")
    if fallos:
        print(f"\n{len(fallos)} fallo(s).")
        return 1
    print(f"\nTodo correcto ({len(avisos)} aviso(s)).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
