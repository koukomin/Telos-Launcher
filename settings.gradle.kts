pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://raw.githubusercontent.com/guardianproject/gpmaven/master") {
            content { includeGroup("info.guardianproject") }
        }
        maven("https://jitpack.io") { content { includeGroup("com.github.TeamNewPipe"); includeGroup("cz.adaptech.tesseract4android") } }
        google()
        mavenCentral()
    }
}

// Dependabot maps plugin-classpath transitives to this file (guava 33.4.0-jre,
// freemarker 2.3.32, bcprov 1.80.2, jsoup 1.16.1, jose4j 0.9.5, jdom2 2.0.6,
// commons-lang3 3.16.0, httpclient 4.5.6). Do not force Jackson here: Dokka's
// BOM already selects 2.22.x and forcing jackson-annotations:2.22.4 fails
// resolution. Android app graphs keep guava -android in root subprojects.
val telosPluginCvePins = listOf(
    "com.google.guava:guava:33.7.2-jre",
    "org.jsoup:jsoup:1.23.2",
    "org.freemarker:freemarker:2.3.35",
    "org.bouncycastle:bcprov-jdk18on:1.86",
    "org.bouncycastle:bcpkix-jdk18on:1.86",
    "org.bouncycastle:bcutil-jdk18on:1.86",
    "org.jdom:jdom2:2.0.6.1",
    "org.apache.httpcomponents:httpclient:4.5.14",
)

gradle.beforeProject {
    buildscript.configurations.configureEach {
        resolutionStrategy {
            force(*telosPluginCvePins.toTypedArray())
            eachDependency {
                when {
                    requested.group == "com.google.guava" && requested.name == "guava" ->
                        useVersion("33.7.2-jre")
                    requested.group == "org.jsoup" && requested.name == "jsoup" ->
                        useVersion("1.23.2")
                    requested.group == "org.freemarker" && requested.name == "freemarker" ->
                        useVersion("2.3.35")
                    requested.group == "org.bouncycastle" ->
                        useVersion("1.86")
                    requested.group == "org.jdom" && requested.name == "jdom2" ->
                        useVersion("2.0.6.1")
                    requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" ->
                        useVersion("4.5.14")
                }
            }
        }
    }
}

include(":app:app")
include(":app:ui")

include(":core:base")
include(":core:crashreporter")
include(":core:compat")
include(":core:preferences")
include(":core:ktx")
include(":core:i18n")
include(":data:database")
include(":core:permissions")
include(":core:shared")

include(":data:appshortcuts")
include(":data:customattrs")
include(":data:applications")
include(":data:calendar")
include(":data:calculator")
include(":data:themes")
include(":data:contacts")
include(":data:currencies")
include(":data:files")
include(":data:unitconverter")
include(":data:websites")
include(":data:webappshortcuts")
include(":data:wikipedia")
include(":data:widgets")
include(":data:weather")
include(":data:notifications")
include(":data:search-actions")
include(":data:searchable")
include(":data:plugins")

include(":services:accounts")
include(":services:tags")
include(":services:search")
include(":services:badges")
include(":services:icons")
include(":services:backup")
include(":services:music")

include(":libs:material-color-utilities")
include(":libs:nextcloud")
include(":libs:owncloud")
include(":libs:webdav")
include(":libs:address-formatter")
include(":services:global-actions")
include(":services:widgets")
include(":services:favorites")

include(":plugins:sdk")
include(":data:locations")
include(":services:plugins")
include(":core:devicepose")
include(":core:profiles")
include(":data:i18n")
include(":services:feed")
include(":services:freeze")
include(":services:wallpapers")
include(":services:context-profiles")
include(":services:desktop-mode")
include(":services:applock")
include(":services:app-management")
include(":services:store")
include(":services:network")
include(":data:store")
include(":services:comms")
include(":data:comms")
include(":services:downloads")

include(":libs:appops-next")
include(":libs:shizuku-next")

include(":ui-kmp")

val isFdroidBuild = providers.gradleProperty("fdroidBuild").orNull?.toBoolean() == true
if (!isFdroidBuild) {
    include(":desktop-linux")
}
