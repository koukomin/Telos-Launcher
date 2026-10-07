package de.mm20.launcher2.store.repository

import de.mm20.launcher2.store.model.StoreItem
import kotlinx.coroutines.flow.Flow

/** Reactive read access to tracked [StoreItem]s, for `:app:ui`'s Store screens. */
interface StoreRepository {
    /** The tracked apps, with what is installed read live from the package manager */
    fun observeItems(): Flow<List<StoreItem>>
    fun observeItem(id: String): Flow<StoreItem?>
    suspend fun insertItem(item: StoreItem)
    suspend fun getAll(): List<StoreItem>
    suspend fun deleteItem(id: String)
    /** Called once the package name of an app is known (read from its APK) */
    suspend fun updatePackageName(id: String, packageName: String)
}
