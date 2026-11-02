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
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                targets("libshizuku.so")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }
    buildFeatures {
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
    ndkVersion = "27.0.12077973"
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
            // AGP packages only CMake SHARED libs. The starter is add_executable
            // named libshizuku.so so adb/root can exec it; copy it into jniLibs.
            jniLibs.srcDir(layout.buildDirectory.dir("packagedShizukuJni"))
        }
    }
}

val packageShizukuStarter by tasks.registering {
    description = "Copy CMake starter executable into jniLibs as libshizuku.so"
    dependsOn(tasks.matching { it.name.startsWith("buildCMake") })
    val destDir = layout.buildDirectory.dir("packagedShizukuJni")
    outputs.dir(destDir)
    doLast {
        val abis = setOf("arm64-v8a", "armeabi-v7a")
        val destRoot = destDir.get().asFile
        val roots = listOf(layout.buildDirectory.get().asFile.resolve("intermediates/cxx"), file(".cxx"))
        var copied = 0
        roots.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().maxDepth(16).forEach { so ->
                if (so.isFile && so.name == "libshizuku.so" && so.parentFile.name in abis) {
                    val dest = destRoot.resolve(so.parentFile.name).resolve("libshizuku.so")
                    dest.parentFile.mkdirs()
                    so.copyTo(dest, overwrite = true)
                    copied++
                }
            }
        }
        check(copied > 0) {
            "libshizuku.so was not built. CMake starter output missing under ${roots.joinToString()}"
        }
    }
}

tasks.configureEach {
    val n = name
    if (n.startsWith("merge") && (n.contains("NativeLibs") || n.contains("JniLibFolders"))) {
        dependsOn(packageShizukuStarter)
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
    // Client types (BinderContainer, ShizukuApiConstants) come from Maven api/provider.
    compileOnly(libs.shizuku.api)
    compileOnly(libs.shizuku.provider)
    compileOnly(libs.shizuku.aidl)
}
