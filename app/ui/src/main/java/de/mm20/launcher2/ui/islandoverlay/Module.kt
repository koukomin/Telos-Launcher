package de.mm20.launcher2.ui.islandoverlay

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val islandOverlayModule = module {
    single { TimerManager() }
    single { CallStateProvider(androidContext(), get()) }
    single { DynamicIslandContentProvider(androidContext(), get(), get(), get(), get()) }
}
