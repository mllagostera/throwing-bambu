// Every plugin is declared here with `apply false` and applied per module. This is not
// decorative: ktlint and detekt inspect the Kotlin and AGP extensions, and they only see
// them if those plugins share the root project's classloader.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    // No kotlin-android: since AGP 9 the Android plugins compile Kotlin themselves.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Read here, not inside `subprojects`: there the receiver is the subproject, which has no
// `libs` extension of its own.
val ktlintEngine = libs.versions.ktlintEngine

// The root project has no Kotlin code; applying ktlint or detekt to it only causes trouble.
subprojects {
    // The ids repeat the catalog aliases; the version still comes from there, via the
    // `plugins` block above.
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintEngine)
        filter { exclude("**/build/**") }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
    }
}
