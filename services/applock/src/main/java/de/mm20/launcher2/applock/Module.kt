package de.mm20.launcher2.applock

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appLockModule = module {
    single { AppLockForegroundMonitor(get(), get(), get()) }
    single(createdAtStart = true) { AppLockManager(androidContext(), get(), get()) }
}
