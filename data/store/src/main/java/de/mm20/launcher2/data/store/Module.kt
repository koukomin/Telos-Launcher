package de.mm20.launcher2.data.store

import de.mm20.launcher2.data.store.fetcher.StoreFetcherRegistryImpl
import de.mm20.launcher2.data.store.installer.KtorDownloader
import de.mm20.launcher2.data.store.installer.TelosPackageInstaller
import de.mm20.launcher2.data.store.worker.StoreUpdateScheduler
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.store.installer.AppInstaller
import de.mm20.launcher2.store.installer.Downloader
import de.mm20.launcher2.store.repository.StoreRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataStoreModule = module {
    single {
        HttpClient {
            // without these a stalled server hangs the update check (and the worker) forever.
            // No overall request timeout: release APKs can be large, the socket timeout catches stalls.
            install(io.ktor.client.plugins.HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 60_000
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single<StoreFetcherRegistry> {
        val options = get<de.mm20.launcher2.store.options.StoreOptions>()
        StoreFetcherRegistryImpl.create(get()) { options.global.value.githubToken }
    }

    single<AppInstaller> { TelosPackageInstaller(context = androidContext()) }
    single<Downloader> { KtorDownloader(context = androidContext(), httpClient = get()) }
    single<StoreRepository> { StoreRepositoryImpl(context = androidContext()) }

    // === TELOS_PENDING_REVIEW_START: virtual_app_koin_fix ===
    factory<VirtualAppProvider>(org.koin.core.qualifier.named("storeVirtualAppProvider")) { StoreVirtualAppProvider(context = androidContext()) }
    // === TELOS_PENDING_REVIEW_END: virtual_app_koin_fix ===
    factory<de.mm20.launcher2.search.SearchableDeserializer>(org.koin.core.qualifier.named(VirtualStoreApp.Domain)) {
        de.mm20.launcher2.search.VirtualAppDeserializer(VirtualStoreApp.Domain) { getAll<VirtualAppProvider>() }
    }

    single<de.mm20.launcher2.store.updater.StoreUpdater> {
        de.mm20.launcher2.data.store.updater.StoreUpdaterImpl(
            context = androidContext(),
            registry = get(),
            repository = get(),
            options = get(),
            handler = get(),
            installer = get(),
        )
    }
    single<de.mm20.launcher2.store.updater.StoreTools> {
        de.mm20.launcher2.data.store.updater.StoreToolsImpl(registry = get(), repository = get(), options = get())
    }

    single { StoreUpdateScheduler(context = androidContext(), options = get()) }
}
