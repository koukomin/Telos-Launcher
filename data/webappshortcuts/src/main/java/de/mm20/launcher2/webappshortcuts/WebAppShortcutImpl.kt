package de.mm20.launcher2.webappshortcuts

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import java.util.concurrent.ExecutionException

internal data class WebAppShortcutImpl(
    val id: String,
    override val label: String,
    override val url: String,
    override val iconUri: String?,
    override val faviconUrl: String?,
    override val color: Int?,
    override val labelOverride: String? = null,
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

    private fun getLaunchIntent(): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = url.toUri()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(), options)
    }

    override fun getSerializer(): SearchableSerializer {
        return WebAppShortcutSerializer()
    }

    companion object {
        const val Domain = "webappshortcut"
    }
}
