package dev.bambu.core

/**
 * Tablas de seno y coseno por grado entero (§5).
 *
 * `Math.sin` no garantiza resultados bit a bit idénticos entre implementaciones de JVM;
 * `StrictMath` sí. Como el ángulo de disparo es un entero de grados, la tabla se calcula
 * una vez al cargar la clase y la ruta de simulación no vuelve a llamar a trigonometría.
 */
internal val SIN = FloatArray(91) { StrictMath.sin(it * StrictMath.PI / 180.0).toFloat() }

internal val COS = FloatArray(91) { StrictMath.cos(it * StrictMath.PI / 180.0).toFloat() }
