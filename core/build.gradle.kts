plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Regla de oro §0.1: este módulo es Kotlin JVM puro. No hay SDK de Android en el
// classpath, así que un `import android.*` ni siquiera compila. El test de
// arquitectura NoAndroidDependenciesTest cubre el caso indirecto (dependencia
// transitiva que arrastre clases de Android).

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // Los tests de evaluación de la IA (M3) son largos y no entran en el CI por defecto.
    if (!project.hasProperty("runSlowTests")) {
        exclude("**/slow/**")
    }
}
