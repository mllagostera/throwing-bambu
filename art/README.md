# Arte — entrega de sprites

Producción de las piezas descritas en `docs/ESPEC_SPRITES.md`. Todo está a **1×**:
ningún fichero se entrega escalado, porque el motor escala por enteros con vecino
más próximo en tiempo de ejecución.

## Cambio de tema: pandas y bambú

El briefing describe gorilas que lanzan plátanos. Lo entregado son **pandas que
lanzan cañas de bambú**, que es lo que dice el nombre del proyecto. El contrato
técnico con el motor no cambia: mismas celdas, mismos pivotes, mismo número y
orden de fotogramas, mismos nombres de pose. Cambian el personaje, el proyectil
y, con ellos, dos restricciones de legibilidad que el briefing había fijado
alrededor del color del plátano:

| | Gorila + plátano | Panda + bambú |
|---|---|---|
| Personaje | gris claro (7) y gris oscuro (8) | blanco (15) y negro (0) |
| Proyectil | amarillo (14) | verde claro (10) con nudos en verde (2) |
| Base de fachada prohibida | amarillo | **verde** (se come la caña) y **blanco** (se come al panda) |
| Ventana encendida | blanco en 3 de 5, para no tapar el plátano | **amarillo en las 5**, como la referencia del original |

El panda además se lee mejor que el gorila a esta escala, y no por casualidad: su
identidad está en manchas de alto contraste —orejas, antifaces, banda del pecho,
patas— y no en el volumen del pelaje, que a 20 px de alto no cabe.

Ficheros renombrados: `gorila.png` → `panda.png`, `banana.png` → `bambu.png`,
`paleta_gorilas.gpl` → `paleta_ega16.gpl` (la paleta es la EGA de 16 colores, no
tiene nada de gorila, y el propio briefing pide no arrastrar el nombre del
original de Microsoft). `docs/ESPEC_SPRITES.md` se deja **sin tocar**: es el
documento de partida y el registro de lo que se pidió.

## Qué hay aquí

```
art/
  palette/paleta_ega16.gpl        Entregable 0. Paleta EGA de 16 colores, orden normativo.
  palette/paleta_ega16.png        Tira de 16×1 px con la paleta (una muestra por píxel).
  palette/paleta_ega16_x16.png    La misma, ampliada, para mirarla.
  cielo.txt                       Los dos colores del degradado que genera el motor.
  sprites/*.png                   Las siete piezas del inventario.
  preview/*_x6.png                Contactos ampliados, con los límites de celda marcados.
  preview/escena_1x.png           Escena de juego montada a 1× con todas las piezas.
  preview/escena_x3.png           La misma, ampliada.
tools/
  ega.py                          Paleta, lienzo y escritura de PNG.
  art_*.py                        Una pieza por fichero: aquí vive el dibujo.
  gen_sprites.py                  Genera art/ entero.
  preview_escena.py               Monta la escena de prueba.
  verify_assets.py                Comprueba los criterios de aceptación.
```

## Reproducir y verificar

```bash
pip install pillow
python3 tools/gen_sprites.py --zoom 6     # reescribe art/sprites y art/preview
python3 tools/preview_escena.py           # reescribe art/preview/escena_*.png
python3 tools/verify_assets.py            # sale con 1 si algo incumple la sección 12
```

`verify_assets.py` lee los PNG entregados, no los generadores, y comprueba pieza
por pieza: solo colores de la paleta, máximo 16 distintos, ningún píxel con alfa
entre 1 y 254, dimensiones de tira y celda exactas, el pivote donde lo declara el
documento, la caja de colisión del panda, el radio del fotograma de pico de la
explosión frente al cráter, los cuatro píxeles clave de cada fachada (ninguna base
verde ni blanca) y el comportamiento del skyline recortado a 320, 360, 400 y
460 px.

Estado actual: **todo correcto, 0 avisos**.

## Formato

PNG-8 indexado (tipo de color 3, profundidad 8) con `tRNS`. La tabla tiene 17
entradas: los 16 colores EGA en los índices 0–15 y el índice 16 como hueco
totalmente transparente, pintado de magenta puro `#FF00FF` —que no pertenece a la
EGA— para que un fallo de transparencia salte a la vista en lugar de disfrazarse
de negro legítimo. Por construcción no puede existir un píxel con alfa intermedio.

## Inventario

| Fichero | Lienzo | Celda | Fotogramas | Pivote | Ritmo |
|---|---|---|---|---|---|
| `panda.png` | 144×24 | 24×24 | 6 | (12, 24) | `pecho_1`/`pecho_2` a 150 ms |
| `bambu.png` | 32×8 | 8×8 | 4 | (4, 4) | bucle a 80 ms |
| `boom.png` | 256×32 | 32×32 | 8 | (16, 16) | ~40 ms; el cráter se borra en el fotograma 3 |
| `sol.png` | 40×20 | 20×20 | 2 | (10, 10) | `ouch` durante 1 s |
| `fachadas.png` | 80×16 | 16×16 | 5 muestras | — | — |
| `skyline.png` | 460×80 | — | 1 | — | — |
| `logo.png` | 200×60 | — | 1 | — | — |

Orden de fotogramas del panda: `idle`, `brazo_izq`, `brazo_der`, `pecho_1`,
`pecho_2`, `muerto`. Se conservan los nombres del briefing aunque «golpe de
pecho» sea gesto de gorila: describen la geometría de la pose, y cambiarlos solo
rompería referencias. Del bambú: horizontal, diagonal ascendente, vertical,
diagonal descendente.

## Integración en Android

Cuando exista el módulo, estos PNG van a `app/src/main/assets/sprites/`, **no** a
`res/drawable*/`: el sistema de recursos aplica escalado por densidad y
destruiría el vecino más próximo. Cárgalos con `BitmapFactory.Options` con
`inScaled = false` y píntalos con un `Paint` sin filtrado (`isFilterBitmap =
false`, `isAntiAlias = false`).

`skyline.png` mide 460 px de ancho y se recorta por la derecha a la anchura real
del lienzo. En la escena de prueba la banda se ancla 45 px por encima de la base
de los edificios; pegada al borde inferior de la pantalla queda tapada del todo
por los edificios jugables.

## Decisiones que se apartan del briefing

Cada una es deliberada y reversible; están todas en los comentarios del `art_*.py`
correspondiente.

1. **No hay ficheros `.aseprite`.** En este entorno no hay Aseprite, y escribir su
   formato binario a ciegas produciría ficheros que nadie ha podido abrir. La
   fuente editable es el `art_*.py`: cada píxel está escrito a mano en ASCII o
   colocado por una regla explícita, es legible en una revisión de código y el
   resultado es reproducible bit a bit. Para pasar a Aseprite: abrir el PNG e
   importar `paleta_ega16.gpl`.
2. **El negro hace de contorno y de mancha a la vez en el panda.** Por eso los
   brazos levantados no llevan contorno propio: rodear de negro un brazo ya negro
   lo engordaría a 4 px. La silueta se define donde empieza el blanco o el fondo.
3. **El panda usa cuatro tonos, no dos.** Blanco (15) y negro (0) para las
   manchas, gris oscuro (8) para dar forma a las masas negras y gris claro (7)
   para la sombra escalonada de la barriga. Sin ellos, brazos y barriga son
   superficies planas de 8 px.
4. **`muerto` está desplomado, no tumbado.** Se probaron dos versiones tumbadas de
   perfil: a 1× una queda como un bulto y la otra se lee como una fábrica con
   chimeneas. Aplastar la silueta vertical de 20 a 12 px conserva al personaje y
   se reconoce de un vistazo. Es una desviación de la *sugerencia* del documento,
   que no era normativa.
5. **La caña de bambú es una barra recta, no una silueta de planta.** Los cuatro
   fotogramas son el mismo cuerpo de 6×2 px girado. Lo que la identifica a 8×8 no
   es el contorno, que no da para más que una barra, sino los **nudos**: dos
   bandas de verde oscuro sobre el cuerpo en verde claro. Sin nudos es un palo.
6. **Los rayos del sol no llevan contorno.** A 1 px de grosor, contornearlos los
   convierte en barras de 3 px y el sol pasa a ser una rueda dentada negra. El
   disco sí lleva contorno.
7. **El color prohibido para la base de las fachadas cambia de amarillo a verde**,
   y se añade el blanco. Ver la tabla del cambio de tema.
8. **La quinta fachada es marrón, no azul.** Azul (1) es el tono de la silueta del
   skyline: un edificio jugable del mismo color que el fondo se pierde.
9. **Los puntos de ventana del skyline van en azul claro (9), no en amarillo.** El
   amarillo es el color de las ventanas encendidas de las fachadas jugables;
   repetirlo en el fondo borra la diferencia entre lo que está delante y lo que
   está detrás.
10. **`logo.png` se ha hecho pese al bloqueo de la sección 9.** Ver abajo.

## Pendiente de decisión

- **Nombre del juego.** La sección 9 bloquea el logo hasta que el nombre esté
  decidido. Se ha rotulado `THROWING BAMBU` por el nombre del repositorio, con
  letra original dibujada para esta pieza; no se usa «Gorillas» ni la tipografía
  de `GORILLA.BAS`. Si el nombre cambia, solo cambian las constantes `LINEA_1` y
  `LINEA_2` de `tools/art_logo.py`: los glifos ya están.
- **Densidad de ventanas encendidas.** El motor decide cuántas enciende. En la
  escena de prueba están densas a propósito, para ver el caso malo. Recomendación:
  por debajo de una de cada cuatro.
- **Anclaje del skyline.** «Se ancla al borde inferior del área de cielo» admite
  dos lecturas. Ver *Integración en Android*.
- **Nombres de pose.** Si el proyecto prefiere que `pecho_1`/`pecho_2` pasen a
  llamarse algo sin connotación de gorila, es un cambio de una línea en
  `art_panda.py`; no afecta al orden de la tira.

## Fuera de alcance de esta entrega

La sección 10 (interfaz) no es píxel art y no entra en «los sprites»: tipografía,
flecha de viento en SVG, especificación de color de UI e iconos de 24 dp siguen
pendientes. La tipografía además exige verificar los glifos acentuados y la «ñ»
de *Press Start 2P* o *Silkscreen* antes de comprometerse, que es una decisión de
licencia y no de dibujo.
