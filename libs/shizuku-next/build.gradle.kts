import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.hidden.refine)
}

val telosNdkVersion = "27.0.12077973"

android {
    namespace = "rikka.shizuku.server"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake {
                targets("libshizuku.so", "adb", "rish")
                // app_process must not resolve a different system C++ runtime.
                // JNI libraries exchange only Java values, never C++ objects.
                arguments("-DANDROID_STL=c++_static", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }
    buildFeatures {
        prefab = true
        aidl = false
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    ndkVersion = telosNdkVersion
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
                "rish/src/main/java",
            )
            java.setSrcDirs(roots)
            kotlin.setSrcDirs(roots)
        }
    }
}

dependencies {
    // Current Apache-2.0 BoringSSL; non-LTO archive works with the pinned NDK.
    implementation("io.github.vvb2060.ndk:boringssl:20251124")
    implementation(libs.kotlin.stdlib)
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("dev.rikka.rikkax.parcelablelist:parcelablelist:2.0.1")
    implementation("dev.rikka.hidden:compat:4.4.0")
    compileOnly("dev.rikka.hidden:stub:4.4.0")
    compileOnly("dev.rikka.tools.refine:annotation:${libs.versions.hidden.refine.get()}")
    annotationProcessor("dev.rikka.tools.refine:annotation-processor:${libs.versions.hidden.refine.get()}")
    compileOnly(libs.androidx.core)
    // Client types (BinderContainer, ShizukuApiConstants) come from Maven api/provider.
    compileOnly(libs.shizuku.api)
    compileOnly(libs.shizuku.provider)
    compileOnly(libs.shizuku.aidl)
}
