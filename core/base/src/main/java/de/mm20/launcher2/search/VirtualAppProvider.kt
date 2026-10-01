package de.mm20.launcher2.search

/**
 * Extension point for injecting synthetic, non-PackageManager-backed entries into the installed
 * app list ([de.mm20.launcher2.applications.AppRepository.findMany]) - e.g. a feature module's
 * own "virtual app" shortcut (the Store's app drawer entry). Bound via Koin as a multibinding
 * (multiple `single<VirtualAppProvider> { ... }`/`factory<VirtualAppProvider> { ... }` across
 * modules, collected with `getAll<VirtualAppProvider>()`), so `:data:applications` never needs to
 * know about the specific features contributing entries.
 */
interface VirtualAppProvider {
    fun getVirtualApps(): List<Application>
}
