import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.hidden.refine)
}

android {
    namespace = "rikka.shizuku.server"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    externalNativeBuild {
        cmake { path = file("jni/CMakeLists.txt") }
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            val roots = listOf(
                "src/main/java",
                "server/src/main/java",
                "starter/src/main/java",
                "common/src/main/java",
                "server-shared/src/main/java",
                "shared/src/main/java",
                "rish/src/main/java",
                "provider/src/main/java",
            )
            java.setSrcDirs(roots)
            kotlin.setSrcDirs(roots)
            aidl.setSrcDirs(listOf("aidl/src/main/aidl"))
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("dev.rikka.rikkax.parcelablelist:parcelablelist:2.0.1")
    implementation("dev.rikka.hidden:compat:4.4.0")
    compileOnly("dev.rikka.hidden:stub:4.4.0")
    compileOnly("dev.rikka.tools.refine:annotation:${libs.versions.hidden.refine.get()}")
    annotationProcessor("dev.rikka.tools.refine:annotation-processor:${libs.versions.hidden.refine.get()}")
    compileOnly(libs.androidx.core)
}
