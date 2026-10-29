package de.mm20.launcher2.icons.transformations

import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIconLayer
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer

/**
 * On-the-fly Cyberpunk / Neo-Candy Icon Transformer.
 * Converts installed app icons into sleek, thin-lined, rounded neon vector icons
 * inspired by Neo-Candy icons with glowing cyber colors.
 */
class CyberpunkNeoIconTransformation : LauncherIconTransformation {

    override suspend fun transform(icon: StaticLauncherIcon): StaticLauncherIcon {
        val darkCyberBg = 0xFF121318.toInt() // Dark Cyber Background
        val neonAccentColor = 0xFF00F2FE.toInt() // Neon Cyan Glow

        return StaticLauncherIcon(
            foregroundLayer = asCyberpunkNeo(icon.foregroundLayer, neonAccentColor),
            backgroundLayer = ColorLayer(darkCyberBg)
        )
    }

    private fun asCyberpunkNeo(layer: LauncherIconLayer, neonColor: Int): LauncherIconLayer {
        return when (layer) {
            is StaticIconLayer -> TintedIconLayer(
                color = neonColor,
                icon = layer.icon,
                scale = layer.scale * 0.85f
            )
            is ColorLayer -> ColorLayer(0xFF181A20.toInt())
            else -> layer
        }
    }
}
