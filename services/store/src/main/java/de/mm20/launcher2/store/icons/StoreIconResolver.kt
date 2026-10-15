package de.mm20.launcher2.store.icons

import de.mm20.launcher2.store.model.AppSource

/** Published artwork for a source; never downloads an APK just to extract its icon. */
interface StoreIconResolver {
    suspend fun candidates(source: AppSource, packageName: String): List<String>
}
