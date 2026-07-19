package de.mm20.launcher2.webappshortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil.imageLoader
import coil.request.ImageRequest
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.webapp.WebAppLaunchContract
import java.util.concurrent.ExecutionException

internal data class WebAppShortcutImpl(
    val id: String,
    override val label: String,
    override val url: String,
    override val iconUri: String?,
    override val faviconUrl: String?,
    override val color: Int?,
    override val rendererPackage: String? = null,
    override val labelOverride: String? = null,
    override val showInGrid: Boolean = true,
    override val showInPanel: Boolean = false,
    override val order: Int = 0,
    override val iconSource: WebAppShortcut.IconSource = WebAppShortcut.IconSource.Website,
) : WebAppShortcut {

    override val domain: String = Domain

    override val key = "$domain://$id"

    override fun overrideLabel(label: String): WebAppShortcut {
        return this.copy(labelOverride = label)
    }

    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        if (iconSource == WebAppShortcut.IconSource.System) {
            // Try to find a matching system icon by label
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val activities = pm.queryIntentActivities(intent, 0)
            val match = activities.find { it.loadLabel(pm).toString().equals(label, ignoreCase = true) }
            if (match != null) {
                val icon = match.loadIcon(pm)
                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(icon = icon, scale = 1f),
                    backgroundLayer = TransparentLayer
                )
            }
        }
        val data = iconUri ?: faviconUrl ?: return null
        try {
            val request = ImageRequest.Builder(context)
                .data(data)
                .size(size)
                .allowHardware(false)
                .build()
            val icon = context.imageLoader.execute(request).drawable ?: return null

            return StaticLauncherIcon(
                foregroundLayer = StaticIconLayer(
                    icon = icon,
                    scale = 1f,
                ),
                backgroundLayer = TransparentLayer
            )
        } catch (e: ExecutionException) {
            return null
        }
    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val color = color ?: 0xFF4285F4.toInt()
        if (label.isNotBlank()) {
            return StaticLauncherIcon(
                foregroundLayer = TextLayer(text = label[0].toString(), color = color),
                backgroundLayer = ColorLayer(color)
            )
        }

        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, R.drawable.ic_web_app_shortcut)!!,
                scale = 0.5f,
                color = color,
            ),
            backgroundLayer = ColorLayer(color)
        )
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        val renderer = rendererPackage
        if (renderer != null &&
            CustomTabsBrowsers.isInstalled(context, renderer) &&
            CustomTabsBrowsers.isCustomTabsSupported(context, renderer)
        ) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.intent.setPackage(renderer)
                customTabsIntent.launchUrl(context, url.toUri())
                return true
            } catch (e: ActivityNotFoundException) {
                // Fall through to the embedded WebView below.
            }
        }
        return openInWebView(context, options)
    }

    private fun openInWebView(context: Context, options: Bundle?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(context.packageName, WebAppLaunchContract.ACTIVITY_CLASS_NAME)
            putExtra(WebAppLaunchContract.EXTRA_URL, url)
            putExtra(WebAppLaunchContract.EXTRA_LABEL, labelOverride ?: label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return context.tryStartActivity(intent, options)
    }

    override fun getSerializer(): SearchableSerializer {
        return WebAppShortcutSerializer()
    }

    companion object {
        const val Domain = "webappshortcut"
    }
}
