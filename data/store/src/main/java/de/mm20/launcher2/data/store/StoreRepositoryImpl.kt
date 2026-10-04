package de.mm20.launcher2.data.store

import android.content.Context
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StoreRepositoryImpl(context: Context) : StoreRepository {

    private val dao = AppDatabase.getInstance(context).storeItemDao()

    override fun observeItems(): Flow<List<StoreItem>> =
        dao.observeAll().map { entities -> entities.map(StoreItemMapper::toDomain) }

    override fun observeItem(id: String): Flow<StoreItem?> =
        dao.observe(id).map { entity -> entity?.let(StoreItemMapper::toDomain) }

    // === TELOS_PENDING_REVIEW_START: telos_store_ui ===
    override suspend fun insertItem(item: StoreItem) {
        dao.insert(StoreItemMapper.toEntity(item))
    }
    // === TELOS_PENDING_REVIEW_END: telos_store_ui ===
}
