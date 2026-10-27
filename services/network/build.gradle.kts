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
    namespace = "de.mm20.launcher2.network"

}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.koin.android)
    implementation(libs.androidx.work)
    implementation(libs.okhttp)
    implementation(libs.shizuku.api)
    implementation("info.guardianproject:tor-android:0.4.9.14")

    // The Go network engine of Rethink DNS (MPL-2.0), used unmodified from Maven Central.
    // It is published as an AAR but its POM says "jar", hence the explicit @aar.
    // This is the exact version Rethink pins in its gradle.properties (firestackCommit).
    api("com.celzero:firestack:${libs.versions.firestack.get()}@aar")

    implementation(project(":core:ktx"))
    implementation(project(":core:i18n"))
    implementation(project(":core:base"))

    testImplementation(libs.bundles.tests)
}
