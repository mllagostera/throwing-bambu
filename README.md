# Throwing Bambu

Juego de artillería por turnos para Android: dos gorilas, un skyline destructible y viento. Inspirado en las mecánicas del clásico género de artillería; **código y recursos originales**, sin nada portado de `GORILLA.BAS` (ver §18 de la especificación).

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/ESPEC_DESARROLLO.md`](docs/ESPEC_DESARROLLO.md) | **Contrato** técnico: constantes, firmas, protocolo, tests obligatorios. Se cambia aquí antes que en el código. |
| [`docs/PLAN_DESARROLLO.md`](docs/PLAN_DESARROLLO.md) | **Plan de ejecución**: decisiones previas, desglose en tareas, hitos M0–M7, trazabilidad de tests, riesgos. |

## Estado

Fase de planificación. El esqueleto Gradle (M0) es el siguiente paso; antes hay que cerrar las decisiones D-01 a D-04 del plan.

## Arquitectura prevista

```
core/       Kotlin JVM puro — física, RNG determinista, escenario, IA, motor de partida. Sin Android.
transport/  Android — Nearby Connections, RFCOMM y loopback tras una interfaz común.
app/        Android — Compose, render de píxel entero, audio, navegación.
```

Tres modos (un jugador, dos en local, Bluetooth) comparten un único bucle de partida; solo cambia el origen de cada disparo.
