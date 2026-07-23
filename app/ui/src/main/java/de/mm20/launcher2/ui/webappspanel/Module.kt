package de.mm20.launcher2.ui.webappspanel

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val webAppsPanelModule = module {
    single { WebAppsPanelManager(androidContext(), get(), get(), get(), get()) }
}
