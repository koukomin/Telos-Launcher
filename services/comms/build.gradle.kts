import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.serialization)
}

android {
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // The native SIP engine is only built when the baresip static libraries are present
        // (downloaded by CI from the "sip-libs" release, or built locally with sip-native/Makefile).
        if (rootProject.file("sip-native/distribution/baresip").exists()) {
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            // The static libraries and the AAudio calls need API 28; older devices fall back to no SIP
            externalNativeBuild { cmake { arguments += "-DANDROID_PLATFORM=android-28" } }
        }
    }

    if (rootProject.file("sip-native/distribution/baresip").exists()) {
        externalNativeBuild {
            cmake { path = file("src/main/cpp/CMakeLists.txt") }
        }
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
    namespace = "de.mm20.launcher2.comms"
}

dependencies {
    // Torrent streaming (MIT): Java API and the native libraries for 64 and 32 bit ARM
    implementation(libs.libtorrent4j)
    implementation(libs.libtorrent4j.arm64)
    implementation(libs.libtorrent4j.arm)
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)

    implementation(project(":core:ktx"))
    implementation(project(":core:base"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:preferences"))
    implementation(libs.androidx.biometric)
    implementation(libs.shizuku.api)
    implementation(libs.taglib)
    
    // === TELOS_PENDING_REVIEW_START: sms_and_radio_engine ===
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    // === TELOS_PENDING_REVIEW_END: sms_and_radio_engine ===
}
