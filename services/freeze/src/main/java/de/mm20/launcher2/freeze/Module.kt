package de.mm20.launcher2.freeze

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val freezeModule = module {
    single { BloatDescriptions(androidContext()) }
    single { FreezeManager(androidContext(), get(), get(), get()) }
    single(createdAtStart = true) { RecentsFreezeMonitor(androidContext(), get(), get(), get()) }
    single { FreezeProfileManager(get(), get()) }
    single { AppUsageStatsProvider(androidContext(), get()) }
    single { FreezeExclusionChecker(androidContext(), get(), get(), get(), get()) }
    single(createdAtStart = true) {
        AutoFreezeController(androidContext(), get(), get(), get(), get(), get(), get(), get(), get())
    }
}
