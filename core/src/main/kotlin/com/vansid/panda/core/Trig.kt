package com.vansid.panda.core

/**
 * Sine and cosine tables per integer degree (§5).
 *
 * `Math.sin` does not guarantee bit-identical results across JVM implementations;
 * `StrictMath` does. Since the shot angle is an integer number of degrees, the table is
 * built once at class load and the simulation path never calls trigonometry again.
 */
internal val SIN = FloatArray(91) { StrictMath.sin(it * StrictMath.PI / 180.0).toFloat() }

internal val COS = FloatArray(91) { StrictMath.cos(it * StrictMath.PI / 180.0).toFloat() }
