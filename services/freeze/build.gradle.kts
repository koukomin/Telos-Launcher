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
    namespace = "de.mm20.launcher2.freeze"
}

dependencies {
    testImplementation(libs.bundles.tests)
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)

    // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
    implementation("io.github.iamr0s:Dhizuku-API:2.7.0")
    // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)

    implementation(project(":core:ktx"))
    implementation(project(":core:base"))
    implementation(project(":core:preferences"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:permissions"))
    implementation(project(":core:i18n"))
    implementation(project(":data:notifications"))
    implementation(project(":services:context-profiles"))
    implementation(project(":services:network"))
}
