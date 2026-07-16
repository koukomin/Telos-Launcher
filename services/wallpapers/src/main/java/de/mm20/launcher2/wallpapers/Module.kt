package de.mm20.launcher2.wallpapers

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val wallpapersModule = module {
    single { WallpapersService(androidContext()) }
}
