package de.mm20.launcher2.applications

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import de.mm20.launcher2.compat.PackageManagerCompat
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.StoreLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LauncherApp(
    override val componentName: ComponentName,
    override val label: String,
    override val user: UserHandle,
    private val launcherActivityInfo: LauncherActivityInfo?,
    private val applicationInfo: ApplicationInfo,
    override val versionName: String?,
    override val isSuspended: Boolean = false,
    internal val userSerialNumber: Long,
    override val labelOverride: String? = null,
    override val score: ResultScore = ResultScore.Unspecified,
    /**
     * Set only for entries discovered through the disabled-components fallback scan (frozen
     * apps): [launcherActivityInfo] can't be resolved for a disabled component, but this
     * [ActivityInfo] - obtained via a MATCH_DISABLED_COMPONENTS-aware query - can still resolve
     * an icon for it.
     */
    private val disabledActivityInfo: ActivityInfo? = null,
) : Application {

    /**
     * Cached result of the normalized label.
     * First string is the normalizer ID
     * Second string is the normalized label
     */
    internal var cachedNormalizerResult: Pair<String, String>? = null


    constructor(
        context: Context,
        launcherActivityInfo: LauncherActivityInfo,
        score: ResultScore = ResultScore.Unspecified,
    ) : this(
        componentName = launcherActivityInfo.componentName,
        label = launcherActivityInfo.label.toString(),
        user = launcherActivityInfo.user,
        launcherActivityInfo = launcherActivityInfo,
        applicationInfo = launcherActivityInfo.applicationInfo,
        versionName = getPackageVersionName(
            context,
            launcherActivityInfo.applicationInfo.packageName
        ),
        isSuspended = launcherActivityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
        userSerialNumber = launcherActivityInfo.user.getSerialNumber(context),
        score = score,
    )

    private val isMainProfile = user == Process.myUserHandle()

    private val isSystemApp: Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

    override val canUninstall: Boolean
        get() = !isSystemApp && isMainProfile

    override val domain: String = Domain
    override val preferDetailsOverLaunch: Boolean = false

    override fun overrideLabel(label: String): LauncherApp {
        return this.copy(labelOverride = label)
    }

    override val key: String
        // For backwards compatibility, user serial number is not included in main profile
        get() = if (isMainProfile) "${domain}://${componentName.packageName}:${componentName.className}"
        else "${domain}://${componentName.packageName}:${componentName.className}:${userSerialNumber}_${user.hashCode()}"


    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        try {
            // === TELOS_PENDING_REVIEW_START: dual_apps_and_multi_user_fix ===
            var icon =
                withContext(Dispatchers.IO) {
                    if (launcherActivityInfo != null) {
                        launcherActivityInfo.getIcon(0)
                    } else if (disabledActivityInfo != null) {
                        disabledActivityInfo.loadIcon(context.packageManager)
                    } else if (componentName.className.isNotEmpty()) {
                        context.packageManager.getActivityIcon(componentName)
                    } else {
                        applicationInfo.loadIcon(context.packageManager)
                    }
                } ?: return null

            if (!isMainProfile) {
                icon = context.packageManager.getUserBadgedIcon(icon, user)
            }
            // === TELOS_PENDING_REVIEW_END: dual_apps_and_multi_user_fix ===

            if (icon is AdaptiveIconDrawable) {
                if (themed && isAtLeastApiLevel(33) && icon.monochrome != null) {
                    return StaticLauncherIcon(
                        foregroundLayer = TintedIconLayer(
                            scale = 1.5f,
                            icon = icon.monochrome!!,
                        ),
                        backgroundLayer = ColorLayer()
                    )
                }
                return StaticLauncherIcon(
                    foregroundLayer = icon.foreground?.let {
                        StaticIconLayer(
                            icon = it,
                            scale = 1.5f,
                        )
                    } ?: TransparentLayer,
                    backgroundLayer = icon.background?.let {
                        StaticIconLayer(
                            icon = it,
                            scale = 1.5f,
                        )
                    } ?: TransparentLayer,
                )
            } else {
                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = icon,
                        scale = 1f,
                    ),
                    backgroundLayer = TransparentLayer
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        if (isAtLeastApiLevel(31)) {
            options?.putInt("android.activity.splashScreenStyle", 1)
        }
        try {
            launcherApps.startMainActivity(
                componentName,
                user,
                null,
                options
            )
        } catch (e: SecurityException) {
            Log.e("MM20", "Could not launch app", e)
            return false
        } catch (e: ActivityNotFoundException) {
            Log.e("MM20", "Could not launch app", e)
            return false
        }
        return true
    }

    override fun getStoreDetails(context: Context): StoreLink? {
        val pm = context.packageManager
        return try {
            val installSourceInfo =
                PackageManagerCompat.getInstallSource(pm, componentName.packageName)
            getStoreLinkForInstaller(
                installSourceInfo.initiatingPackageName,
                componentName.packageName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun uninstall(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:${componentName.packageName}")
        context.startActivity(intent)
    }

    override fun openAppDetails(context: Context) {
        if (componentName.className.isNotEmpty()) {
            val launcherApps = context.getSystemService<LauncherApps>()!!

            launcherApps.startAppDetailsActivity(
                componentName,
                user,
                null,
                null
            )
        } else {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${componentName.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override val canShareApk: Boolean = true
    override suspend fun shareApkFile(context: Context) {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        // an app made of several APK files (splits) is shared as one .apks archive with all parts, a plain app as its .apk
        val result: Pair<java.io.File, String>? = withContext(Dispatchers.IO) {
            try {
                val info = launcherApps.getApplicationInfo(componentName.packageName, 0, user)
                val parts = (listOf(info.publicSourceDir) + (info.splitPublicSourceDirs?.toList() ?: emptyList()))
                    .filterNotNull().map { java.io.File(it) }.filter { it.isFile && it.canRead() }
                if (parts.isEmpty()) return@withContext null
                val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                val base = "${componentName.packageName}-${versionName}".replace(Regex("[^A-Za-z0-9._-]"), "_")
                if (parts.size == 1) {
                    val copy = java.io.File(dir, "$base.apk")
                    parts[0].copyTo(copy, true)
                    copy to "application/vnd.android.package-archive"
                } else {
                    val copy = java.io.File(dir, "$base.apks")
                    java.util.zip.ZipOutputStream(copy.outputStream().buffered()).use { zip ->
                        parts.forEach { f ->
                            zip.putNextEntry(java.util.zip.ZipEntry(f.name))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    copy to "application/zip"
                }
            } catch (e: Exception) {
                null
            }
        }
        if (result == null) return
        val (fileCopy, mime) = result
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            fileCopy
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.clipData = android.content.ClipData.newRawUri(null, uri)
        shareIntent.type = mime
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    override fun getActivityInfo(context: Context): ActivityInfo? {
        if (isAtLeastApiLevel(31)) {
            return launcherActivityInfo?.activityInfo
        }
        return super.getActivityInfo(context)
    }

    companion object {
        private fun getStoreLinkForInstaller(
            installerPackage: String?,
            packageName: String?
        ): StoreLink? {
            if (packageName == null) return null
            return when (installerPackage) {
                "de.amazon.mShop.android", "com.amazon.venezia" -> {
                    StoreLink(
                        "Amazon App Shop",
                        "http://www.amazon.com/gp/mas/dl/android?p=${packageName}"
                    )
                }

                "com.android.vending" -> {
                    StoreLink(
                        "Google Play Store",
                        "https://play.google.com/store/apps/details?id=${packageName}"
                    )
                }

                "org.fdroid.fdroid", "com.aurora.adroid" -> {
                    StoreLink(
                        "F-Droid",
                        "https://f-droid.org/packages/${packageName}"
                    )
                }

                else -> null
            }
        }

        fun getPackageVersionName(context: Context, packageName: String): String? {
            return try {
                context.packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        // === TELOS_PENDING_REVIEW_START: dual_apps_and_multi_user_fix ===
        fun isSuspended(context: Context, packageName: String, user: UserHandle = Process.myUserHandle()): Boolean {
            return try {
                val launcherApps = context.getSystemService<LauncherApps>()
                if (launcherApps != null) {
                    val infoList = launcherApps.getActivityList(packageName, user)
                    if (infoList.isNotEmpty()) {
                        return (infoList[0].applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
                    }
                }
                context.packageManager.getApplicationInfo(
                    packageName,
                    0
                ).flags and ApplicationInfo.FLAG_SUSPENDED != 0
            } catch (e: Exception) {
                false
            }
        }
        // === TELOS_PENDING_REVIEW_END: dual_apps_and_multi_user_fix ===

        const val Domain = "app"
    }

    override fun getSerializer(): SearchableSerializer {
        return LauncherAppSerializer()
    }
}