import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    namespace = "dev.izumi.appopsnext"
    compileSdk { version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = libs.versions.compileSdkMinor.get().toInt() } }
    // The host keeps API 26; entry into this independently guarded component requires API 35.
    defaultConfig {
        minSdk = 35
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"${System.getenv("VERSION_NAME_OVERRIDE") ?: "1.42.15"}\"")
        buildConfigField("int", "VERSION_CODE", System.getenv("VERSION_CODE_OVERRIDE") ?: "2026102700")
        buildConfigField("String", "SHIZUKU_API_VERSION", "\"13.1.5\"")
    }
    buildFeatures { aidl = true; compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

}

// A generated asset must expose its producing task to every AGP consumer,
// including lint analysis, instead of registering the output as a static folder.
abstract class NativeDaemonAssetTask : Exec() {
    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty
}

val buildNativeDaemon by tasks.registering(NativeDaemonAssetTask::class) {
    workingDir = file("daemon")
    inputs.files(fileTree("daemon") { include("**/*.go", "go.mod") })
    assetsDirectory.set(layout.buildDirectory.dir("native-daemon/main"))
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")
    val executable = assetsDirectory.file("arm64-v8a/appopsnextd").get().asFile
    commandLine("go", "build", "-trimpath", "-buildvcs=false", "-buildmode=pie", "-ldflags=-s -w -buildid=", "-o", executable.absolutePath, "./cmd/appopsnextd")
    val outputDirectory = executable.parentFile
    val buildForArm64 = providers.gradleProperty("telos.abi").orNull != "armeabi-v7a"
    doFirst { outputDirectory.mkdirs() }
    onlyIf { buildForArm64 }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        if (providers.gradleProperty("telos.abi").orNull != "armeabi-v7a") {
            variant.sources.assets?.addGeneratedSourceDirectory(buildNativeDaemon, NativeDaemonAssetTask::assetsDirectory)
        }
    }
}

dependencies {
    implementation(project(":core:ktx"))
    implementation(libs.androidx.activitycompose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.uitooling)
    implementation(libs.bundles.androidx.lifecycle)
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
}
