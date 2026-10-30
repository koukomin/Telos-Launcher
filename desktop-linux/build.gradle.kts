import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
tasks.named<Copy>("processResources") {
    from(rootProject.file("core/i18n/src/main/res")) {
        include("values*/**/*.xml")
        into("telos-android-i18n")
    }
    from(project.file("session")) {
        into("telos-session")
    }
    doLast {
        val dest = destinationDir.resolve("telos-android-i18n")
        if (dest.isDirectory) {
            dest.resolve("index.txt").writeText(
                dest.walkTopDown().filter { it.isFile && it.extension == "xml" }
                    .map { it.relativeTo(dest).invariantSeparatorsPath }
                    .sorted()
                    .joinToString("\n"),
            )
        }
    }
}
// === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===

dependencies {
    implementation(project(":ui-kmp"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // === TELOS_PENDING_REVIEW_START: telos_elastic_sharded_pqc_vault ===
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    // === TELOS_PENDING_REVIEW_END: telos_elastic_sharded_pqc_vault ===
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)
    implementation(libs.zip4j)
    testImplementation("junit:junit:4.13.2")
}

// === TELOS_PENDING_REVIEW_START: fix_uberjar_signature ===
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.sig")
}

val composeJarsDir = layout.buildDirectory.dir("compose/jars")
tasks.matching { it.name == "packageUberJarForCurrentOS" }.configureEach {
    val jarsDir = composeJarsDir
    doLast {
        val dir = jarsDir.get().asFile
        if (!dir.isDirectory) return@doLast
        dir.listFiles()?.filter { it.extension.equals("jar", true) }?.forEach { jar ->
            val tmp = File(jar.parentFile, jar.name + ".unsigned")
            ZipInputStream(jar.inputStream().buffered()).use { zin ->
                ZipOutputStream(tmp.outputStream().buffered()).use { zout ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val skip = name.matches(Regex("META-INF/.*\\.(SF|RSA|DSA|sig)", RegexOption.IGNORE_CASE))
                        if (!skip) {
                            val copy = ZipEntry(name)
                            zout.putNextEntry(copy)
                            zin.copyTo(zout)
                            zout.closeEntry()
                        }
                        entry = zin.nextEntry
                    }
                }
            }
            tmp.copyTo(jar, overwrite = true)
            tmp.delete()
        }
        val sessionDest = dir.resolve("session")
        sessionDest.deleteRecursively()
        project.copy {
            from(project.file("session"))
            into(sessionDest)
        }
        sessionDest.resolve("telos-session").setExecutable(true)
        sessionDest.resolve("labwc/autostart").setExecutable(true)
        sessionDest.resolve("telos-notifyd").setExecutable(true)
        sessionDest.resolve("telos-pam-auth").setExecutable(true)
        sessionDest.resolve("telos-layer").setExecutable(true)
        sessionDest.resolve("telos-portal").setExecutable(true)
        sessionDest.resolve("telos-sni").setExecutable(true)
    }
}
// === TELOS_PENDING_REVIEW_END: fix_uberjar_signature ===

compose.desktop {
    application {
        mainClass = "com.telos.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "telos-desktop"
            packageVersion = "1.0.0"
            description = "Telos Linux Desktop Environment"
            vendor = "Telos"
            linux {
                debMaintainer = "telos@localhost"
                menuGroup = "System"
                appRelease = "1"
                appCategory = "Utility"
            }
        }
    }
}
