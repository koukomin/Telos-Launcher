package de.mm20.launcher2.contextprofiles

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val contextProfilesModule = module {
    single { ContextProfileManager(androidContext(), get(), get()) }
    single { ContextProfileEffectsApplier(androidContext(), get(), get(), get()) }
}
