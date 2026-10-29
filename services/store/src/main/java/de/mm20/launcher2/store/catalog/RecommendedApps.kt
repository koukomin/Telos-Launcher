package de.mm20.launcher2.store.catalog

import de.mm20.launcher2.store.model.AppSource

/** The groups of the "Recommended" tab of the Store */
enum class RecommendedCategory { Stores, Tools, Browsers, Media }

/**
 * An app the Store suggests (free and open source apps, with one exception noted below) with the
 * place its releases are published, the way Obtainium's app list does it. The Store does not host or
 * change anything: "Add" puts the source into the tracked apps, where it is installed and updated like any
 * other app. [packageName] may be empty when it is not known for sure; it is read from the APK when
 * the app is installed.
 */
data class RecommendedApp(
    val name: String,
    val category: RecommendedCategory,
    val source: AppSource,
    val packageName: String = "",
)

object RecommendedApps {

    val all: List<RecommendedApp> = listOf(
        // ---- Stores ----
        RecommendedApp("Aurora Store", RecommendedCategory.Stores, AppSource.GitLab(path = "AuroraOSS/AuroraStore"), "com.aurora.store"),
        RecommendedApp(
            "Obtainium", RecommendedCategory.Stores,
            // the F-Droid flavor of its release has "fdroid" in the file name
            AppSource.GitHub("ImranR98", "Obtainium", assetNameRegex = "^(?!.*fdroid).*release\\.apk$"),
            "dev.imranr.obtainium",
        ),
        RecommendedApp("Accrescent", RecommendedCategory.Stores, AppSource.GitHub("accrescent", "accrescent"), "app.accrescent.client"),
        RecommendedApp("Orion Store", RecommendedCategory.Stores, AppSource.GitHub("Orion-Store", "Orion"), "com.orion.store"),
        RecommendedApp("F-Droid", RecommendedCategory.Stores, AppSource.FDroid("org.fdroid.fdroid"), "org.fdroid.fdroid"),
        RecommendedApp("Droid-ify", RecommendedCategory.Stores, AppSource.FDroid("com.looker.droidify"), "com.looker.droidify"),

        // ---- Tools ----
        RecommendedApp(
            "Termux", RecommendedCategory.Tools,
            AppSource.GitHub("termux", "termux-app", assetNameRegex = "^termux-app_.*\\+github-debug_(arm64-v8a|armeabi-v7a|x86_64|x86|universal)\\.apk$"),
            "com.termux",
        ),
        RecommendedApp("Shizuku", RecommendedCategory.Tools, AppSource.GitHub("RikkaApps", "Shizuku"), "moe.shizuku.privileged.api"),
        RecommendedApp("Shizuku Next", RecommendedCategory.Tools, AppSource.GitHub("rushiranpise", "Shizuku-Next")),
        RecommendedApp("Morphe Manager", RecommendedCategory.Tools, AppSource.GitHub("MorpheApp", "morphe-manager")),
        RecommendedApp("Morphe microG", RecommendedCategory.Tools, AppSource.GitHub("MorpheApp", "MicroG-RE", assetNameRegex = "^microg-[0-9.]+\\.apk$")),
        RecommendedApp("SD Maid SE", RecommendedCategory.Tools, AppSource.GitHub("d4rken-org", "sdmaid-se"), "eu.darken.sdmse"),

        // ---- Browsers ----
        RecommendedApp("IronFox", RecommendedCategory.Browsers, AppSource.GitLab(path = "ironfox-oss/IronFox"), "org.ironfoxoss.ironfox"),
        RecommendedApp(
            "Cromite", RecommendedCategory.Browsers,
            AppSource.GitHub("uazo", "cromite", assetNameRegex = "^(arm64|arm)_ChromePublic\\.apk$"),
            "org.cromite.cromite",
        ),
        RecommendedApp("Titanium Browser", RecommendedCategory.Browsers, AppSource.GitHub("jqssun", "android-titanium-browser")),
        RecommendedApp("Iceraven", RecommendedCategory.Browsers, AppSource.GitHub("fork-maintainers", "iceraven-browser"), "io.github.forkmaintainers.iceraven"),
        // the Tor Project publishes the APKs in one folder per version (the Store looks into the newest folder)
        RecommendedApp("Tor Browser", RecommendedCategory.Browsers, AppSource.Html("https://download.torproject.org/tor-browser-for-android/"), "org.torproject.torbrowser"),
        RecommendedApp("Waterfox", RecommendedCategory.Browsers, AppSource.GitHub("BrowserWorks", "waterfox")),

        // ---- Media ----
        RecommendedApp("Nova Video Player", RecommendedCategory.Media, AppSource.GitHub("nova-video-player", "aos-AVP"), "org.courville.nova"),
        RecommendedApp("Transistor", RecommendedCategory.Media, AppSource.Gitea(host = "codeberg.org", owner = "y20k", repo = "transistor"), "org.y20k.transistor"),
        RecommendedApp("VLC", RecommendedCategory.Media, AppSource.FDroid("org.videolan.vlc"), "org.videolan.vlc"),
        RecommendedApp("BoomingMusic", RecommendedCategory.Media, AppSource.GitHub("mardous", "BoomingMusic", assetNameRegex = "github-universal\\.apk$")),
    )

    fun forCategory(category: RecommendedCategory): List<RecommendedApp> = all.filter { it.category == category }
}
