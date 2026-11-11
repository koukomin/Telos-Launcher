plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "de.mm20.launcher2.keepasstl"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    flavorDimensions += "variant"
    productFlavors {
        create("default") {
            dimension = "variant"
        }
        create("fdroid") {
            dimension = "variant"
        }
    }
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.koin.android)
    implementation(libs.kotpass)
    implementation(project(":core:base"))
    implementation(project(":core:ktx"))
    implementation(project(":core:preferences"))
    implementation(project(":core:i18n"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
