package de.mm20.launcher2.data.store.updater

import de.mm20.launcher2.store.action.StoreActionHandler
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.options.StoreOptions
import de.mm20.launcher2.store.parser.StoreUrlParser
import de.mm20.launcher2.store.repository.StoreRepository
import de.mm20.launcher2.store.transfer.ObtainiumTransfer
import de.mm20.launcher2.store.updater.ImportSummary
import de.mm20.launcher2.store.updater.StoreTools
import java.util.UUID

class StoreToolsImpl(
    private val registry: StoreFetcherRegistry,
    private val repository: StoreRepository,
    private val options: StoreOptions,
) : StoreTools {

    override suspend fun exportObtainium(): String =
        ObtainiumTransfer.export(repository.getAll()) { options.item(it) }

    override suspend fun importObtainium(json: String): ImportSummary {
        val (apps, unsupported) = ObtainiumTransfer.parse(json)
        val existing = repository.getAll()
        val known = existing.map { StoreUrlParser.toUrl(it.source).lowercase() }.toMutableSet()
        var added = 0
        var skipped = 0
        for (app in apps) {
            val url = StoreUrlParser.toUrl(app.source).lowercase()
            if (!known.add(url)) {
                skipped++
                continue
            }
            val item = StoreItem(
                id = UUID.randomUUID().toString(),
                packageName = app.packageName,
                displayName = app.name,
                source = app.source,
            )
            repository.insertItem(item)
            options.update(item.id) { app.options }
            added++
        }
        return ImportSummary(added, skipped, unsupported)
    }

    override suspend fun findSourceForInstalled(packageName: String): AppSource? {
        val fdroid = AppSource.FDroid(packageName)
        if (registry.resolveLatestRelease(fdroid).isSuccess) return fdroid
        val izzy = AppSource.FDroid(packageName, StoreUrlParser.IZZY_REPO)
        if (registry.resolveLatestRelease(izzy).isSuccess) return izzy
        return null
    }

    override suspend fun addFromUrl(url: String, packageName: String): Result<StoreItem> {
        val parsed = StoreUrlParser.parse(url) ?: return Result.failure(IllegalArgumentException("This is not a web address"))
        val existing = repository.getAll()
        val wanted = StoreUrlParser.toUrl(parsed.source).lowercase()
        if (existing.any { StoreUrlParser.toUrl(it.source).lowercase() == wanted }) {
            return Result.failure(IllegalStateException("This app is already in your list"))
        }
        val release = registry.resolveLatestRelease(parsed.source).getOrElse { return Result.failure(it) }
        val item = StoreItem(
            id = UUID.randomUUID().toString(),
            packageName = parsed.packageName ?: packageName.trim().ifBlank { StoreActionHandler.UNKNOWN_PACKAGE },
            displayName = parsed.suggestedName.substringAfterLast('/'),
            source = parsed.source,
            latestRelease = release,
            lastCheckedAt = System.currentTimeMillis(),
        )
        repository.insertItem(item)
        return Result.success(item)
    }
}
