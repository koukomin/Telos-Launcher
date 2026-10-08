import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.serialization)
}

// The yt-dlp runtime (Python + yt-dlp + FFmpeg, youtubedl-android) adds roughly 80 MB per ABI to the APK, so it is
// optional: build with -Ptelos.media=true (or telos.media=true in gradle.properties) to include it.
val mediaRuntime = (findProperty("telos.media") as String?)?.toBoolean() == true

android {
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    namespace = "de.mm20.launcher2.downloads"

    sourceSets {
        getByName("main") {
            val dir = if (mediaRuntime) "src/mediaOn/java" else "src/mediaOff/java"
            java.directories.add(dir)
            kotlin.directories.add(dir)
        }
    }
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.koin.android)
    implementation(libs.okhttp)
    // The torrent engine runs in the one shared libtorrent session (TorrentSession in :services:comms)
    implementation(libs.libtorrent4j)
    implementation(project(":services:comms"))

    if (mediaRuntime) {
        implementation(libs.youtubedl.library)
        implementation(libs.youtubedl.ffmpeg)
    }

    implementation(project(":core:base"))
    implementation(project(":core:i18n"))

    testImplementation(libs.bundles.tests)
}
