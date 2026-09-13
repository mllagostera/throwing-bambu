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
     * Busca la secuencia en el pool de constantes sin parsear el .class.
     * ISO-8859-1 mapea cada byte a un char 1:1, así que la búsqueda de una cadena
     * ASCII sobre el volcado es exacta.
     */
    private fun ByteArray.containsUtf8Constant(needle: String): Boolean =
        String(this, Charsets.ISO_8859_1).contains(needle)
}
