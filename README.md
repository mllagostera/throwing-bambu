# Throwing Bambu

Juego de artillería por turnos para Android: dos gorilas, un skyline destructible y viento. Inspirado en las mecánicas del clásico género de artillería; **código y recursos originales**, sin nada portado de `GORILLA.BAS` (ver §18 de la especificación).

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/ESPEC_DESARROLLO.md`](docs/ESPEC_DESARROLLO.md) | **Contrato** técnico: constantes, firmas, protocolo, tests obligatorios. Se cambia aquí antes que en el código. |
| [`docs/PLAN_DESARROLLO.md`](docs/PLAN_DESARROLLO.md) | **Plan de ejecución**: decisiones previas, desglose en tareas, hitos M0–M7, trazabilidad de tests, riesgos. |

## Estado

**M1 completado**: núcleo determinista en `:core` — RNG xorshift64\*, tablas trigonométricas, generación de escenario, física con muestreo de segmento y 34 tests, incluidos los nueve obligatorios del §15. Siguiente hito: **M2** (render geométrico y partida local a dos jugadores).

## Construir y probar

```bash
./gradlew build test        # build completo (requiere SDK de Android)
./gradlew ktlintCheck detekt
./gradlew ktlintFormat      # autocorrección de estilo
```

`:core` es Kotlin JVM puro y **no necesita el SDK de Android**. Para trabajar solo el núcleo —física, RNG, escenario, IA— en una máquina sin SDK:

```bash
./gradlew -PcoreOnly :core:test
```

Requisitos: JDK 17 o superior. La versión de Gradle la fija el wrapper.

## APK

Cada push genera un APK de depuración, **solo si los tests y el análisis estático pasan**. Se descarga desde la pestaña *Actions* → la ejecución correspondiente → artefacto **`apk-debug`**, con el nombre `throwing-bambu-<versión>-debug-<sha>.apk`. Se conserva 14 días.

Va firmado con la clave de depuración de Android, así que se instala tal cual:

```bash
adb install throwing-bambu-0.1.0-debug-<sha>.apk
```

En el móvil hay que permitir la instalación de orígenes desconocidos para la app desde la que se abra el fichero.

Para generarlo en local:

```bash
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

El APK de **release** requiere un keystore propio y no se genera todavía: entra en M7 junto con R8 y la configuración de firma (T-52 del plan).

## Arquitectura prevista

```
core/       Kotlin JVM puro — física, RNG determinista, escenario, IA, motor de partida. Sin Android.
transport/  Android — Nearby Connections, RFCOMM y loopback tras una interfaz común.
app/        Android — Compose, render de píxel entero, audio, navegación.
```

Tres modos (un jugador, dos en local, Bluetooth) comparten un único bucle de partida; solo cambia el origen de cada disparo.

## Legal

Código y recursos originales, bajo licencia MIT (ver [`LICENSE`](LICENSE)). Este proyecto **no** porta código de `GORILLA.BAS` ni reutiliza sus recursos, que son propiedad de Microsoft: las mecánicas de juego no son protegibles, pero el código y los assets sí. El título es propio.

> La elección de MIT es una asunción de M0; si prefieres otra licencia, es el momento de cambiarla — antes de que haya contribuciones externas.
