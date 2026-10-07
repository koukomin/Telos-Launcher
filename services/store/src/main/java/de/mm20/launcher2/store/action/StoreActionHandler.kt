package de.mm20.launcher2.store.action

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.store.installer.AppInstaller
import de.mm20.launcher2.store.installer.DownloadResult
import de.mm20.launcher2.store.installer.Downloader
import de.mm20.launcher2.store.installer.InstallResult
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface StoreAction {
    /** The install/update was handed off to the Play Store app; Telos has no further visibility. */
    data object LaunchedPlayStore : StoreAction
    data object Installing : StoreAction
    data object Installed : StoreAction
    data class Failed(val reason: String, val cause: Throwable? = null) : StoreAction
}

/**
 * Decides how to fulfill an install/update request for a [StoreItem], based on its [AppSource]:
 * - [AppSource.AffiliatePlayStore] never touches the network or [AppInstaller] directly - it
 *   launches the Play Store app with a `referrer` URI attached, so install attribution flows to
 *   whoever is credited for the referral.
 * - Every other source resolves a release (via [StoreFetcherRegistry] if the item doesn't already
 *   carry one), downloads it through [downloader], and installs it through [installer].
 */
class StoreActionHandler(
    private val context: Context,
    private val installer: AppInstaller,
    private val downloader: Downloader,
    private val fetcherRegistry: StoreFetcherRegistry,
    private val repository: de.mm20.launcher2.store.repository.StoreRepository,
) {
    suspend fun install(item: StoreItem): StoreAction {
        val source = item.source
        if (source is AppSource.AffiliatePlayStore) {
            return launchPlayStore(source)
        }

        val release = item.latestRelease ?: fetcherRegistry.fetchLatestRelease(source)
            ?: return StoreAction.Failed("Could not resolve a release for ${item.packageName}")

        val fileName = "${item.packageName}-${release.version}.apk"
        val download = downloader.download(release.downloadUrl, fileName)
        val apkFile = when (download) {
            is DownloadResult.Success -> download.file
            is DownloadResult.Failed -> {
                download.cause?.let { CrashReporter.logException(Exception(download.reason, it)) }
                return StoreAction.Failed(download.reason, download.cause)
            }
        }

        // The APK says which app it is: remember it for apps added from a web address, and refuse a
        // file that is for another app than the one tracked here
        val archive = context.packageManager.getPackageArchiveInfo(apkFile.path, 0)
        val detected = archive?.packageName
        val known = item.packageName != UNKNOWN_PACKAGE
        if (detected != null && known && detected != item.packageName) {
            apkFile.delete()
            return StoreAction.Failed("This file is for $detected, not for ${item.packageName}")
        }
        if (detected != null && !known) repository.updatePackageName(item.id, detected)
        val packageName = detected ?: item.packageName

        return when (val result = installer.install(apkFile, packageName)) {
            is InstallResult.Success -> StoreAction.Installed
            is InstallResult.Pending -> StoreAction.Installing
            is InstallResult.Failed -> {
                result.cause?.let { CrashReporter.logException(Exception(result.reason, it)) }
                StoreAction.Failed(result.reason, result.cause)
            }
        }
    }

    /** Asks Android to uninstall [packageName] (the system shows its confirmation). */
    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    companion object {
        /** Placeholder package name of an app added by web address, until its first download */
        const val UNKNOWN_PACKAGE = "unknown.package"
    }

    /**
     * `market://details?id=<pkg>&referrer=<referrer>` with `com.android.vending` targeted
     * explicitly - without `setPackage`, a bare `market://` intent can be picked up by any app
     * registered as a handler for it, which would silently drop the referral.
     */
    private suspend fun launchPlayStore(source: AppSource.AffiliatePlayStore): StoreAction =
        withContext(Dispatchers.Main) {
            val uri = Uri.parse("market://details")
                .buildUpon()
                .appendQueryParameter("id", source.packageName)
                .appendQueryParameter("referrer", source.referrer)
                .build()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                StoreAction.LaunchedPlayStore
            } catch (e: ActivityNotFoundException) {
                StoreAction.Failed("Play Store is not installed", e)
            }
        }
}
