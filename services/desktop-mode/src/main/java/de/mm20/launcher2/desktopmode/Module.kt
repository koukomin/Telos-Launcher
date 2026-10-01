package de.mm20.launcher2.desktopmode

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val desktopModeModule = module {
    single { DesktopModeManager(androidContext(), get()) }
    // === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
    single { DesktopWindowManager() }
    // === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
}
