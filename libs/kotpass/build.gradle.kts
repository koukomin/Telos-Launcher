plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    implementation(libs.kotpass) { isTransitive = false }
    shadow(libs.kotlin.stdlib)
    shadow(libs.okio)
}

tasks.shadowJar {
    filesMatching("META-INF/*.kotlin_module") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    // Keep Kotpass's bundled implementations and bytecode references together,
    // while letting the rest of Telos use its own Apache Commons Lang dependency.
    relocate("org.apache.commons.lang3", "de.mm20.launcher2.keepasstl.kotpass.internal.lang3")
}
