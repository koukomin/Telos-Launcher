import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

androidComponents {
    onVariants(selector().all()) { variant ->
        if (providers.gradleProperty("telos.abi").orNull != "armeabi-v7a") {
            variant.sources.assets?.addStaticSourceDirectory(layout.buildDirectory.dir("native-daemon/main").get().asFile.absolutePath)
        }
    }
}

// Upstream's bounded, private-pipe backend works around OEM restrictions on app_process.
// Keep it source-built, and ship it only for ARM64; other ABIs use the UserService fallback.
val nativeOutput = layout.buildDirectory.file("native-daemon/main/arm64-v8a/appopsnextd")
val buildNativeDaemon by tasks.registering(Exec::class) {
    workingDir = file("daemon")
    inputs.files(fileTree("daemon") { include("**/*.go", "go.mod") })
    outputs.file(nativeOutput)
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")
    commandLine("go", "build", "-trimpath", "-buildvcs=false", "-buildmode=pie", "-ldflags=-s -w -buildid=", "-o", nativeOutput.get().asFile.absolutePath, "./cmd/appopsnextd")
    val outputDirectory = nativeOutput.get().asFile.parentFile
    val buildForArm64 = providers.gradleProperty("telos.abi").orNull != "armeabi-v7a"
    doFirst { outputDirectory.mkdirs() }
    onlyIf { buildForArm64 }
}
tasks.configureEach {
    // Assets are also read directly by release lint model writers. Ordering only the
    // merge task leaves those readers without a producer dependency in Gradle 9.
    if ((name.startsWith("merge") && name.endsWith("Assets")) ||
        (name.startsWith("generate") && name.contains("Lint") && name.endsWith("Model"))) {
        dependsOn(buildNativeDaemon)
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
