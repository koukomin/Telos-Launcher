import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    // Mail and Activation carry matching-named notices. Full upstream licenses ship as app assets.
    packaging.resources.pickFirsts += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md")
    namespace = "de.mm20.launcher2.data.comms"
}

dependencies {
    implementation(libs.bundles.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)

    implementation(project(":core:ktx"))
    implementation(project(":core:base"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:permissions"))
    implementation(project(":services:comms"))

    implementation(libs.androidx.room)
    implementation(libs.androidx.roomruntime)
    ksp(libs.androidx.roomcompiler)
    implementation(libs.sqlcipher.android)

    // === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
    implementation(libs.bundles.ktor)
    // === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===
}
