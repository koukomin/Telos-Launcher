package de.mm20.launcher2.store.repository

import de.mm20.launcher2.store.model.StoreItem
import kotlinx.coroutines.flow.Flow

/** Reactive read access to tracked [StoreItem]s, for `:app:ui`'s Store screens. */
interface StoreRepository {
    fun observeItems(): Flow<List<StoreItem>>
    fun observeItem(id: String): Flow<StoreItem?>
    // === TELOS_PENDING_REVIEW_START: telos_store_ui ===
    suspend fun insertItem(item: StoreItem)
    // === TELOS_PENDING_REVIEW_END: telos_store_ui ===
}
