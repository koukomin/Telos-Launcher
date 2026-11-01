import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.dokka) apply true
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "com.fasterxml.jackson.core" &&
                    requested.name in setOf("jackson-core", "jackson-databind", "jackson-annotations") ->
                    useVersion(libs.versions.jacksonCore.get())
                requested.group == "com.fasterxml.jackson.dataformat" &&
                    requested.name == "jackson-dataformat-yaml" ->
                    useVersion(libs.versions.jacksonDataformatYaml.get())
                requested.group == "org.jsoup" && requested.name == "jsoup" ->
                    useVersion(libs.versions.jsoup.get())
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" ->
                    useVersion(libs.versions.jose4j.get())
                requested.group == "org.jdom" && requested.name == "jdom2" ->
                    useVersion(libs.versions.jdom2.get())
                requested.group == "org.freemarker" && requested.name == "freemarker" ->
                    useVersion(libs.versions.freemarker.get())
                requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" ->
                    useVersion(libs.versions.httpclient.get())
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" ->
                    useVersion(libs.versions.commonsLang3.get())
                requested.group == "com.google.guava" && requested.name == "guava" -> {
                    val suffix = if (requested.version.orEmpty().endsWith("-jre")) "-jre" else "-android"
                    useVersion(libs.versions.guava.get() + suffix)
                }
                requested.group == "org.bouncycastle" ->
                    useVersion(libs.versions.bouncycastle.get())
            }
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions.freeCompilerArgs.addAll(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=StrongSkipping",
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=OptimizeNonSkippingGroups",
            )
        }
    }
    
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

dependencies {
    dokka(project(":plugins:sdk"))
    dokka(project(":core:shared"))
}