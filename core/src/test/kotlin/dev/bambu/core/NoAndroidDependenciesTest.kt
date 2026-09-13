package dev.bambu.core

import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Golden rule §0.1: `:core` imports nothing from Android.
 *
 * A direct `import android.*` no longer compiles, because the SDK is not on the
 * classpath. What this test covers is the indirect case: a dependency added to `:core`
 * that drags Android classes in (for example `kotlinx-coroutines-android` instead of
 * `-core`). That mistake compiles fine and only shows up when the module stops being
 * testable on a plain JVM.
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
            fail("The :core classpath contains Android classes, which breaks golden rule §0.1: $offenders")
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
            fail("Classes in :core referencing Android types: $offenders")
        }
    }

    /**
     * A1 (§5): the simulation path does not call trigonometry.
     *
     * `Math.sin` is not guaranteed to give bit-identical results across JVM
     * implementations, and a one-ULP divergence in the launch angle is amplified over
     * the flight until it changes who wins the round. Only `Trig.kt` (tables built with
     * `StrictMath`) and `Rng.kt` (Box-Muller, outside the simulation path) may use it.
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
            fail("Trigonometry outside Trig.kt/Rng.kt: $offenders. Use the SIN/COS tables.")
        }
    }

    private fun File.containsAndroidClasses(): Boolean =
        ZipFile(this).use { zip ->
            zip.entries().asSequence().any { entry ->
                entry.name.startsWith("android/") || entry.name.startsWith("androidx/")
            }
        }

    /**
     * Looks for the sequence in the constant pool without parsing the .class file.
     * ISO-8859-1 maps every byte to a char one to one, so searching for an ASCII string
     * over the dump is exact.
     */
    private fun ByteArray.containsUtf8Constant(needle: String): Boolean =
        String(this, Charsets.ISO_8859_1).contains(needle)
}
