package de.mm20.launcher2.freeze

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val freezeModule = module {
    single { FreezeManager(androidContext(), get()) }
    single(createdAtStart = true) { AutoFreezeController(androidContext(), get(), get()) }
}
