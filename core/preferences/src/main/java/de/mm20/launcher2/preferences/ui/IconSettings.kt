package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class IconSettingsData(
    val themedIcons: Boolean,
    val forceThemed: Boolean,
    val adaptify: Boolean,
    val iconPack: String?,
    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
    val fallbackIconPacks: List<String>,
    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
)

class IconSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) : Flow<IconSettingsData> by (
        launcherDataStore.data.map {
            IconSettingsData(
                themedIcons = it.icons.iconsThemed,
                forceThemed = it.icons.iconsForceThemed,
                adaptify = it.icons.iconsAdaptify,
                iconPack = it.icons.iconsPack,
                // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
                fallbackIconPacks = it.icons.fallbackIconPacks,
                // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
            )
        }
        ) {

    fun setAdaptifyLegacyIcons(adaptify: Boolean) {
        launcherDataStore.update {
            it.copy(icons = it.icons.copy(iconsAdaptify = adaptify))
        }
    }

    fun setThemedIcons(themedIcons: Boolean) {
        launcherDataStore.update {
            it.copy(icons = it.icons.copy(iconsThemed = themedIcons))
        }
    }

    fun setForceThemedIcons(forceThemed: Boolean) {
        launcherDataStore.update {
            it.copy(icons = it.icons.copy(iconsForceThemed = forceThemed))
        }
    }

    fun setIconPack(iconPack: String?) {
        launcherDataStore.update {
            it.copy(icons = it.icons.copy(iconsPack = iconPack))
        }
    }

    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
    fun setFallbackIconPacks(fallbackPacks: List<String>) {
        launcherDataStore.update {
            it.copy(icons = it.icons.copy(fallbackIconPacks = fallbackPacks))
        }
    }
    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===

    fun setIconPackThemed(iconPackThemed: Boolean) {
        launcherDataStore.update {
            it.copy(icons = it.icons.copy(iconsPackThemed = iconPackThemed))
        }
    }


}