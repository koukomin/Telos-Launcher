package de.mm20.launcher2.desktopmode

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val desktopModeModule = module {
    single { DesktopModeManager(androidContext(), get()) }
}
