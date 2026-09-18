import java.util.Properties

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

/**
 * The version is the git tag, and only the git tag. A literal here would be a second copy
 * of what the tag already says, free to drift from it — and Play never forgets: the
 * `versionCode` of an upload must be higher than every code uploaded before it, for the
 * life of the app. A number that can be derived cannot be forgotten.
 *
 *     v0.2.0                     0.2.0                code 200
 *     v1.4.3                     1.4.3                code 10403
 *     five commits past v0.2.0   0.2.0-5-gabc1234     code 200
 *     no tag reachable           0.0.0-dev+abc1234    code 1
 *
 * Only the first two are releases. The other two name a build sitting on a desk, and
 * repeating the tag's code for them is deliberate: nothing but a tag may reach Play.
 *
 * `major * 10_000 + minor * 100 + patch` stays readable in Play Console — 10403 is plainly
 * 1.4.3 — at the price of capping minor and patch at 99. Should either ever get there, the
 * multipliers can grow, because a larger code is still a larger code.
 */
data class AppVersion(
    val name: String,
    val code: Int,
)

/** One git command, its output, or null if git itself is missing or the command failed. */
fun git(vararg arguments: String): String? =
    runCatching {
        val run =
            providers.exec {
                workingDir = rootProject.projectDir
                executable = "git"
                args = arguments.toList()
                isIgnoreExitValue = true
            }
        val output =
            run.standardOutput.asText
                .get()
                .trim()
        output.takeIf { run.result.get().exitValue == 0 }
    }.getOrNull()

/**
 * `--match` keeps this on release tags: a tag put on a branch for any other reason must
 * not become a version. `--always` makes a repository with no tag at all report its commit
 * rather than fail, which is what a branch before its first release is.
 *
 * The dirty mark is asked of `git status` and not of `describe --dirty`, which reads the
 * index's cached file stats: those go stale and call a clean tree dirty. Since the release
 * workflow refuses to publish when the tag and the built version disagree, a false mark
 * there is not a cosmetic problem — it is a red release on a good commit.
 */
fun gitDescribe(): String? {
    val described = git("describe", "--tags", "--match", "v[0-9]*", "--always")
    if (described.isNullOrEmpty()) return null
    val dirty = git("status", "--porcelain")?.isNotEmpty() == true
    return if (dirty) "$described-dirty" else described
}

fun appVersion(): AppVersion {
    val described = gitDescribe() ?: return AppVersion("0.0.0-dev", 1)
    // A commit with no release tag behind it — a branch before its first one. `--always`
    // reports a hex commit, which cannot begin with a `v`, so the two never collide.
    if (!described.startsWith("v")) return AppVersion("0.0.0-dev+$described", 1)
    val release = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:-\d+-g[0-9a-f]+)?(?:-dirty)?$""")
    // Loudly, on purpose. A tag `v2.0.0-beta1` that quietly became "0.0.0-dev, code 1"
    // would build, upload, and be rejected by Play for a code it has long left behind —
    // or worse, be accepted and pin the app to 1 forever.
    val tagged =
        release.matchEntire(described)
            ?: error("Cannot version `$described`: a release tag reads vMAJOR.MINOR.PATCH and nothing else.")
    val (major, minor, patch) = tagged.groupValues.drop(1).map(String::toInt)
    return AppVersion(described.removePrefix("v"), major * 10_000 + minor * 100 + patch)
}

val appVersion = appVersion()

/**
 * What CI reads instead of grepping this file for a literal. The shape is `key=value` per
 * line so the workflow can append it straight to `$GITHUB_OUTPUT`.
 */
tasks.register("printVersion") {
    val name = appVersion.name
    val code = appVersion.code
    doLast {
        println("versionName=$name")
        println("versionCode=$code")
    }
}

/**
 * The *upload* key — not the key that signs what people install. Play App Signing, which
 * every app created since 2021 must use, holds that one; this key only proves to Play that
 * an upload is ours. Google can reset it if it is ever lost, which is why it is the only
 * one we are allowed to hold: losing the upload key is a support ticket, losing an app
 * signing key would be the end of the app.
 *
 * CI passes the four values in the environment, out of GitHub secrets. A machine building
 * a signed release locally puts the same four in `keystore.properties`, which .gitignore
 * keeps out of the repository. With neither, the release build still runs and comes out
 * unsigned: that is what lets anyone exercise R8 and resource shrinking, and it is why the
 * release workflow verifies the signature instead of assuming it.
 */
data class UploadKey(
    val store: File,
    val storePassword: String,
    val alias: String,
    val keyPassword: String,
)

fun uploadKey(): UploadKey? {
    val file = rootProject.file("keystore.properties")
    val properties = Properties()
    if (file.isFile) file.inputStream().use(properties::load)

    fun setting(
        variable: String,
        property: String,
    ): String? =
        providers.environmentVariable(variable).orNull?.takeIf { it.isNotBlank() }
            ?: properties.getProperty(property)?.takeIf { it.isNotBlank() }

    val path = setting("UPLOAD_KEYSTORE_FILE", "storeFile") ?: return null
    val store = rootProject.file(path)
    // Past this point the key was meant to be used, so a half-configured one is an error
    // and not a quiet fallback to an unsigned build that nobody would notice until Play
    // refused it.
    if (!store.isFile) error("Upload keystore configured but missing: no file at $store.")
    return UploadKey(
        store = store,
        storePassword =
            setting("UPLOAD_KEYSTORE_PASSWORD", "storePassword")
                ?: error("Upload keystore configured but UPLOAD_KEYSTORE_PASSWORD / storePassword is not set."),
        alias =
            setting("UPLOAD_KEY_ALIAS", "keyAlias")
                ?: error("Upload keystore configured but UPLOAD_KEY_ALIAS / keyAlias is not set."),
        keyPassword =
            setting("UPLOAD_KEY_PASSWORD", "keyPassword")
                ?: error("Upload keystore configured but UPLOAD_KEY_PASSWORD / keyPassword is not set."),
    )
}

android {
    namespace = "com.vansid.panda.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vansid.panda"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersion.code
        versionName = appVersion.name
    }

    signingConfigs {
        uploadKey()?.let { key ->
            create("upload") {
                storeFile = key.store
                storePassword = key.storePassword
                keyAlias = key.alias
                keyPassword = key.keyPassword
            }
        }
    }

    buildTypes {
        release {
            // Null when no key is configured, which builds an unsigned release. Play
            // rejects that, so `release.yml` refuses to publish one.
            signingConfig = signingConfigs.findByName("upload")
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
