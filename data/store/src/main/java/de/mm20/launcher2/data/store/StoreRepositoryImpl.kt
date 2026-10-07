package de.mm20.launcher2.data.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.mm20.launcher2.data.store.installer.resolveInstalledInfo
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.StoreItemEntity
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.repository.StoreRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class StoreRepositoryImpl(private val context: Context) : StoreRepository {

    private val dao = AppDatabase.getInstance(context).storeItemDao()

    /** Emits whenever an app is installed, updated or removed, so the Store shows what is really installed */
    private val packageChanges: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.applicationContext.registerReceiver(receiver, filter)
        awaitClose { runCatching { context.applicationContext.unregisterReceiver(receiver) } }
    }.onStart { emit(Unit) }

    private fun live(entity: StoreItemEntity): StoreItem {
        val info = resolveInstalledInfo(context, entity.packageName)
        return StoreItemMapper.toDomain(entity, installedCode = info?.versionCode, installedName = info?.versionName)
    }

    override fun observeItems(): Flow<List<StoreItem>> =
        combine(dao.observeAll(), packageChanges) { entities, _ -> entities.map(::live) }

    override fun observeItem(id: String): Flow<StoreItem?> =
        combine(dao.observe(id), packageChanges) { entity, _ -> entity?.let(::live) }

    override suspend fun insertItem(item: StoreItem) {
        dao.insert(StoreItemMapper.toEntity(item))
    }

    override suspend fun getAll(): List<StoreItem> = dao.getAll().map(::live)

    override suspend fun deleteItem(id: String) {
        dao.deleteById(id)
    }

    override suspend fun updatePackageName(id: String, packageName: String) {
        dao.updatePackageName(id, packageName)
    }
}
