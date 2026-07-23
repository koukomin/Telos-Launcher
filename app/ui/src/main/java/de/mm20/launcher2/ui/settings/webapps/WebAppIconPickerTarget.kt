package de.mm20.launcher2.ui.settings.webapps

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer

/**
 * Placeholder [SavableSearchable] used only to satisfy [de.mm20.launcher2.ui.common.IconPicker]'s
 * API (and [de.mm20.launcher2.icons.IconService.resolveCustomIcon]'s generic resolution path) when
 * picking an icon-pack icon for a web app shortcut. A web app shortcut being created doesn't have
 * a persisted identity yet to pass in, and even when editing an existing one, its real identity
 * isn't actually relevant here: resolving an explicit user-picked [de.mm20.launcher2.data.customattrs.CustomIcon]
 * is driven by that CustomIcon's own embedded data (icon pack package/drawable ref), not by the
 * searchable it's being resolved "for" - so one shared stand-in works for every shortcut.
 */
internal object WebAppIconPickerTarget : SavableSearchable {
    override val key = "webappshortcut://icon-picker-target"
    override val label = ""
    override val preferDetailsOverLaunch = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?) = false

    override fun getPlaceholderIcon(context: Context) = StaticLauncherIcon(
        foregroundLayer = ColorLayer(Color.TRANSPARENT),
        backgroundLayer = ColorLayer(Color.TRANSPARENT),
    )

    override val domain = "webappshortcut"

    override fun getSerializer(): SearchableSerializer =
        throw UnsupportedOperationException("WebAppIconPickerTarget is a placeholder and is never persisted")
}
