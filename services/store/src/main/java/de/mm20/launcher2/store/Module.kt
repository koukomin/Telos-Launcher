package de.mm20.launcher2.store

import de.mm20.launcher2.store.action.StoreActionHandler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val storeModule = module {
    single {
        StoreActionHandler(
            context = androidContext(),
            installer = get(),
            downloader = get(),
            fetcherRegistry = get(),
            repository = get(),
        )
    }
    single { de.mm20.launcher2.store.options.StoreOptions(androidContext()) }
}
