plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The sprites are build output of `tools/gen_sprites.py` (see AGENTS.md), so they are
 * packaged straight from `art/sprites` instead of being copied into the repository. A
 * second copy would drift the moment somebody regenerates the art.
 */
abstract class SyncSpritesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val spriteDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val target = outputDir.get().asFile.resolve("sprites")
        target.deleteRecursively()
        target.mkdirs()
        spriteDir
            .get()
            .asFile
            .listFiles()
            ?.filter { it.isFile && it.extension == "png" }
            ?.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

val syncSprites =
    tasks.register<SyncSpritesTask>("syncSprites") {
        spriteDir.set(rootProject.layout.projectDirectory.dir("art/sprites"))
        outputDir.set(layout.buildDirectory.dir("generated/spriteAssets"))
    }

android {
    namespace = "dev.bambu.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bambu.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":transport"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

/**
 * Registering the directory through the variant API — rather than adding it to
 * `sourceSets` and wiring task dependencies by name — is what makes every consumer pick
 * it up: merging, packaging and lint alike. Wiring it by hand missed lint, and Gradle
 * rightly refused to build.
 */
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncSprites, SyncSpritesTask::outputDir)
    }
}
