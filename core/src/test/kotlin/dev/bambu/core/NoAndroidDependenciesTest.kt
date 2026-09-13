package dev.bambu.core

import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Regla de oro §0.1: `:core` no importa nada de Android.
 *
 * Un `import android.*` directo ya no compila, porque el SDK no está en el classpath.
 * Lo que este test cubre es el caso indirecto: una dependencia añadida a `:core` que
 * arrastre clases de Android (por ejemplo `kotlinx-coroutines-android` en lugar de
 * `-core`). Ese error compila sin problema y solo se nota cuando el módulo deja de ser
 * testeable en JVM pura.
 */
class NoAndroidDependenciesTest {
    @Test
    fun coreClasspathHasNoAndroidClasses() {
        val offenders =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter { it.isFile && it.name.endsWith(".jar") }
                .filter { jar -> jar.containsAndroidClasses() }
                .map { it.name }

        if (offenders.isNotEmpty()) {
            fail(
                "El classpath de :core contiene clases de Android, lo que rompe la regla " +
                    "de oro §0.1. Artefactos culpables: $offenders",
            )
        }
    }

    @Test
    fun coreCompiledClassesReferenceNoAndroidTypes() {
        val classesDir = File("build/classes/kotlin/main")
        if (!classesDir.isDirectory) return

        val offenders =
            classesDir
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .filter { it.readBytes().containsUtf8Constant("android/") }
                .map { it.relativeTo(classesDir).path }
                .toList()

        if (offenders.isNotEmpty()) {
            fail("Clases de :core que referencian tipos de Android: $offenders")
        }
    }

    private fun File.containsAndroidClasses(): Boolean =
        ZipFile(this).use { zip ->
            zip.entries().asSequence().any { entry ->
                entry.name.startsWith("android/") || entry.name.startsWith("androidx/")
            }
        }

    /**
     * A1 (§5): la ruta de simulación no llama a trigonometría.
     *
     * `Math.sin` no garantiza el mismo resultado bit a bit entre implementaciones de JVM,
     * y una divergencia de un ULP en el ángulo de salida se amplifica a lo largo del vuelo
     * hasta cambiar el ganador de la ronda. Solo `Trig.kt` (tablas precalculadas con
     * `StrictMath`) y `Rng.kt` (Box-Muller, fuera de la ruta de simulación) pueden usarla.
     */
    @Test
    fun simulationPathDoesNotCallTrigonometry() {
        val sources = File("src/main/kotlin")
        if (!sources.isDirectory) return

        val allowed = setOf("Trig.kt", "Rng.kt")
        val call = Regex("""\b(sin|cos|tan)\s*\(""")

        val offenders =
            sources
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") && it.name !in allowed }
                .filter { file ->
                    file.readLines().any { line ->
                        val code = line.substringBefore("//")
                        call.containsMatchIn(code)
                    }
                }.map { it.name }
                .toList()

        if (offenders.isNotEmpty()) {
            fail("Trigonometría fuera de Trig.kt/Rng.kt: $offenders. Usa las tablas SIN/COS.")
        }
    }

    /**
     * Busca la secuencia en el pool de constantes sin parsear el .class.
     * ISO-8859-1 mapea cada byte a un char 1:1, así que la búsqueda de una cadena
     * ASCII sobre el volcado es exacta.
     */
    private fun ByteArray.containsUtf8Constant(needle: String): Boolean =
        String(this, Charsets.ISO_8859_1).contains(needle)
}
