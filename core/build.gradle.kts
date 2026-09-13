plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Golden rule §0.1: this module is plain Kotlin JVM. There is no Android SDK on the
// classpath, so an `import android.*` does not even compile. The architecture test
// NoAndroidDependenciesTest covers the indirect case (a transitive dependency that
// drags Android classes in).

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
    // The AI evaluation tests (M3) are long and stay out of CI by default.
    if (!project.hasProperty("runSlowTests")) {
        exclude("**/slow/**")
    }
}
