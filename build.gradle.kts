// Todos los plugins se declaran aquí con `apply false` y se aplican en cada módulo.
// No es decorativo: ktlint y detekt inspeccionan las extensiones de Kotlin y de AGP,
// y solo las ven si esos plugins comparten el classloader del proyecto raíz.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// El proyecto raíz no tiene código Kotlin; aplicarle ktlint o detekt solo da problemas.
subprojects {
    // Los ids repiten los alias del catálogo; la versión sigue saliendo de allí,
    // del bloque `plugins` de arriba.
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        filter { exclude("**/build/**") }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
    }
}
