package de.mm20.launcher2.data.store

import de.mm20.launcher2.data.store.fetcher.GitHubFetcher
import de.mm20.launcher2.data.store.fetcher.StoreFetcherRegistryImpl
import de.mm20.launcher2.data.store.installer.KtorDownloader
import de.mm20.launcher2.data.store.installer.TelosPackageInstaller
import de.mm20.launcher2.data.store.worker.StoreUpdateScheduler
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
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
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single { GitHubFetcher(httpClient = get()) }
    single<StoreFetcherRegistry> { StoreFetcherRegistryImpl(gitHubFetcher = get()) }

    single<AppInstaller> { TelosPackageInstaller(context = androidContext()) }
    single<Downloader> { KtorDownloader(context = androidContext(), httpClient = get()) }
    single<StoreRepository> { StoreRepositoryImpl(context = androidContext()) }

    single { StoreUpdateScheduler(context = androidContext()) }
}
