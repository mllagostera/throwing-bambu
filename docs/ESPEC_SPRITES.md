# Gorilas Android — Briefing de arte

Documento para producción de sprites. Todas las medidas están en **píxeles lógicos a 1×**. El juego escala por enteros con vecino más próximo en tiempo de ejecución, así que **no se entrega arte a 2× ni 3×**: entregarlo escalado produciría un doble escalado y destruiría la nitidez.

---

## 0. Restricciones técnicas — leer antes de dibujar

1. **Píxel art estricto.** Ningún antialias, ningún degradado suave, ninguna sombra difusa.
2. **Alfa binario.** Cada píxel es totalmente opaco o totalmente transparente. Nada de 50 % de opacidad: la máscara de colisión y el escalado por enteros no lo toleran.
3. **Paleta cerrada** (§1). Ningún color fuera de ella. Si una pieza necesita un color nuevo, se discute y se añade al documento; no se añade sobre la marcha.
4. **Celda uniforme por animación.** Todos los fotogramas de un mismo sprite comparten dimensiones exactas. Si una pose se sale, se agranda la celda de toda la animación, no de un fotograma.
5. **Formato de entrega:** PNG-8 con paleta indexada + transparencia, o PNG-24 con alfa binario. Más el `.aseprite` fuente de cada pieza.

---

## 1. Paleta

EGA de 16 colores. Es la paleta del original y es lo que da la lectura retro sin caer en la parodia.

| # | Nombre | Hex |
|---|---|---|
| 0 | Negro | `#000000` |
| 1 | Azul | `#0000AA` |
| 2 | Verde | `#00AA00` |
| 3 | Cian | `#00AAAA` |
| 4 | Rojo | `#AA0000` |
| 5 | Magenta | `#AA00AA` |
| 6 | Marrón | `#AA5500` |
| 7 | Gris claro | `#AAAAAA` |
| 8 | Gris oscuro | `#555555` |
| 9 | Azul claro | `#5555FF` |
| 10 | Verde claro | `#55FF55` |
| 11 | Cian claro | `#55FFFF` |
| 12 | Rojo claro | `#FF5555` |
| 13 | Magenta claro | `#FF55FF` |
| 14 | Amarillo | `#FFFF55` |
| 15 | Blanco | `#FFFFFF` |

**Entregable 0:** `paleta_gorilas.gpl` + `paleta_gorilas.aseprite` con estos 16 colores en este orden. Todo lo demás se dibuja con ella cargada como paleta indexada.

Con 16 colores, la técnica de volumen es el **dithering**, no el degradado. Patrón de tablero (checkerboard) y escalera (chequer 2:1), nunca ruido aleatorio.

---

## 2. Inventario y entregables

Cada animación se entrega como **una tira horizontal** en un único PNG. Nada de atlas mixto: simplifica la producción y el consumo.

| Fichero | Lienzo total | Celda | Nº fotogramas |
|---|---|---|---|
| `gorila.png` | 144 × 24 | 24 × 24 | 6 |
| `banana.png` | 32 × 8 | 8 × 8 | 4 |
| `boom.png` | 256 × 32 | 32 × 32 | 8 |
| `sol.png` | 40 × 20 | 20 × 20 | 2 |
| `skyline.png` | 460 × 80 | — | 1 |
| `fachadas.png` | 80 × 16 | 16 × 16 | 5 |
| `logo.png` | 200 × 60 | — | 1 |

---

## 3. `gorila.png` — 6 fotogramas de 24 × 24

**Pivote: (12, 24)** — centro horizontal, borde inferior. Ese punto se apoya exactamente en el píxel del tejado.

El cuerpo real debe ocupar **16 px de ancho × 20 px de alto**, centrado horizontalmente y apoyado en el borde inferior de la celda. Los 4 px sobrantes arriba y los 4 px a cada lado existen **solo** para absorber los brazos levantados sin mover el pivote. No los uses para agrandar el cuerpo: el motor colisiona contra un rectángulo de 16 × 20 y un gorila más grande que su caja de colisión se siente injusto.

| # | Nombre | Descripción |
|---|---|---|
| 0 | `idle` | Pose de reposo. Brazos abajo pegados al cuerpo. Es el fotograma que se ve el 95 % del tiempo: aquí se juega la identidad del personaje. |
| 1 | `brazo_izq` | Brazo izquierdo extendido hacia arriba. Se usa mientras el proyectil viaja hacia la izquierda. |
| 2 | `brazo_der` | Brazo derecho extendido hacia arriba. Espejo del anterior, pero **dibujado a mano**: un volteo automático delata la simetría y queda muerto. |
| 3 | `pecho_1` | Golpe en el pecho, puños arriba. Celebración de victoria. |
| 4 | `pecho_2` | Golpe en el pecho, puños contra el torso. Se alterna con el 3 a 150 ms. |
| 5 | `muerto` | Derrotado. Sugerencia: tumbado, brazos extendidos, dentro de la misma celda y apoyado en el mismo borde inferior. |

Notas de estilo: silueta ancha de hombros y cabeza pequeña, que es lo que hace legible a un gorila a 20 px de alto. Dos tonos de cuerpo (7 y 8 de la paleta) más negro para el contorno interno. La cara necesita como máximo 3 píxeles de información; no intentes más.

---

## 4. `banana.png` — 4 fotogramas de 8 × 8

**Pivote: (4, 4)**, centro geométrico.

Rotación en 4 pasos de 90°: horizontal, diagonal ascendente, vertical, diagonal descendente. El motor **no rota el sprite**: recorre los fotogramas en bucle a 80 ms. Por eso los cuatro deben leerse como el mismo objeto girando, no como cuatro plátanos distintos.

Amarillo (14) como cuerpo, marrón (6) o negro (0) en las puntas. Nada más cabe en 8 × 8.

---

## 5. `boom.png` — 8 fotogramas de 32 × 32

**Pivote: (16, 16)**, centro geométrico, que se alinea con el punto exacto de impacto.

Esta pieza tiene una restricción dura: el motor borra del terreno un **círculo de radio 12** centrado en el pivote, y lo hace en el **fotograma 3**.

- Fotogramas 0–2: expansión. Radio visible creciente de ~4 a ~12 px.
- **Fotograma 3: pico.** Radio visible **15 px** — debe sobrepasar el cráter en unos 3 px. Si el fuego es más pequeño que el agujero, el impacto se percibe como flojo y falso.
- Fotogramas 4–7: disipación. El fuego se descompone en fragmentos que se apagan, no un círculo que se encoge uniformemente.

Rampa de color: blanco (15) → amarillo (14) → rojo claro (12) → rojo (4) → transparente. Las transiciones entre tonos se resuelven con dithering, no con tonos intermedios inventados.

---

## 6. `sol.png` — 2 fotogramas de 20 × 20

**Pivote: (10, 10)**.

| # | Nombre | Descripción |
|---|---|---|
| 0 | `normal` | Sol sonriente con rayos. Amarillo (14) con contorno. |
| 1 | `ouch` | Boca abierta en «O», ojos como puntos, rayos ligeramente retraídos. |

El sol cambia a `ouch` durante 1 s cuando un plátano lo atraviesa, y el plátano **no se detiene**. Es un guiño del original y una de las cosas que la gente recuerda del juego; merece los 20 minutos que cuesta.

---

## 7. `fachadas.png` — 5 celdas de 16 × 16

No son sprites: son **muestras de color** que el motor lee para pintar edificios generados proceduralmente.

Cada celda de 16 × 16 define una variante de fachada y contiene, en posiciones fijas:

- **Píxel (0, 0):** color base de la fachada.
- **Píxel (1, 0):** color de ventana **encendida**.
- **Píxel (2, 0):** color de ventana **apagada**.
- **Píxel (3, 0):** color de contorno/borde del edificio.
- Resto de la celda: libre, ignorado por el motor. Úsalo para previsualizar cómo queda la combinación.

Cinco variantes con buen contraste entre sí. Referencia del original: fachadas en cian, rojo, gris y magenta oscuros, con ventanas en amarillo (encendida) y azul oscuro (apagada).

**Restricción de legibilidad:** el plátano es amarillo. Ninguna fachada puede usar amarillo como color base, o el proyectil desaparecerá al pasar por delante. La ventana encendida sí puede ser amarilla porque mide 3 × 4 px.

---

## 8. `skyline.png` — 460 × 80

Silueta urbana de fondo, detrás de los edificios jugables. **Una sola capa, sin paralaje.**

- Se recorta a la anchura real del lienzo (320–460). El diseño debe funcionar recortado por la derecha en cualquier punto: **nada de composición centrada ni elementos únicos memorables** que a veces salgan y a veces no.
- Silueta plana en un solo tono oscuro (1 u 8) con puntos de ventana dispersos. Nada de volumen.
- La banda se ancla al borde inferior del área de cielo; los 60 px superiores del lienzo deben quedar razonablemente despejados para el sol y las trayectorias altas.

El degradado de cielo lo genera el motor por código; **no lo dibujes**. Entrega en su lugar un `cielo.txt` con dos colores hex: arriba y abajo.

---

## 9. `logo.png` — 200 × 60

Título del juego para la pantalla de menú. Píxel art, misma paleta.

**No uses el nombre «Gorillas» ni la tipografía del original.** El código y los recursos de `GORILLA.BAS` son de Microsoft. El nombre definitivo se decide antes de esta pieza; hasta entonces, no la empieces.

---

## 10. Interfaz — fuera del lienzo lógico

La UI **no** es píxel art escalado. Se dibuja en dp nativos con Compose, porque texto a 200 px de alto es ilegible en una pantalla de 1080p.

Lo que sí se necesita de Design aquí:

- **Tipografía:** una fuente de píxel legible a 14 sp con soporte de acentos y «ñ». Recomendadas por licencia libre y cobertura Latin-1: *Press Start 2P* (OFL) o *Silkscreen* (OFL). Verificar los glifos acentuados antes de comprometerse.
- **Flecha de viento:** SVG vectorial, no píxel art. Escala horizontalmente según `|wind|` (0–10). Entregar el trazo base y el color.
- **Especificación de color de UI:** fondo de paneles, color de acento de los sliders, color de texto activo e inactivo. Deriva de la paleta EGA pero puede desaturarse para no competir con el juego.
- **Iconos:** silenciar, ajustes, atrás, Bluetooth. 24 dp, trazo de 2 dp.

---

## 11. Orden de producción recomendado

1. Paleta (`paleta_gorilas.gpl`) — desbloquea todo lo demás.
2. `fachadas.png` + `cielo.txt` — permiten a desarrollo dejar de usar rectángulos.
3. `gorila.png` fotogramas 0, 1, 2 — el juego ya es presentable con solo estos tres.
4. `banana.png` + `boom.png` — el par que más cambia la sensación de impacto.
5. `sol.png`, `gorila.png` fotogramas 3–5.
6. `skyline.png`.
7. Tipografía, iconos, flecha de viento.
8. `logo.png`.

El punto 3 es la entrega mínima que desbloquea el hito M4 de desarrollo. Todo lo anterior a él es bloqueante; todo lo posterior es incremental.

---

## 12. Criterios de aceptación

Una pieza se da por buena cuando:

- Usa exclusivamente colores de la paleta (verificable con un contador de colores únicos ≤ 16).
- No tiene ningún píxel con alfa entre 1 y 254.
- Todos los fotogramas de la tira tienen exactamente la dimensión de celda declarada.
- El pivote declarado cae donde dice este documento (verificar superponiendo una guía).
- Se lee correctamente al 100 % de zoom en una captura real del juego, no solo ampliada en el editor.

Ese último punto es el que más piezas tumba. Revisa siempre a 1× antes de dar algo por cerrado.
