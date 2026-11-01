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
    namespace = "de.mm20.launcher2.data.store"
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "128m"
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}

dependencies {
    testImplementation(libs.bundles.tests)
    testImplementation("org.json:json:20260814")
    testImplementation("io.ktor:ktor-client-mock:3.6.1")
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.bundles.ktor)

    implementation(libs.koin.android)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)

    implementation(project(":core:i18n"))
    implementation(project(":core:ktx"))
    implementation(project(":core:base"))
    implementation(project(":core:preferences"))
    implementation(project(":core:crashreporter"))
    implementation(project(":data:database"))
    implementation(project(":services:store"))
    implementation(project(":services:freeze"))
    implementation(project(":services:app-management"))
}
