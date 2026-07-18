package de.mm20.launcher2.webappshortcuts

import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.WebAppShortcut
import org.koin.core.qualifier.named
import org.koin.dsl.module

val webAppShortcutsModule = module {
    single<WebAppShortcutRepository> { WebAppShortcutRepositoryImpl(get()) }
    single<SearchableRepository<WebAppShortcut>>(named<WebAppShortcut>()) { get<WebAppShortcutRepository>() }
    factory<SearchableDeserializer>(named(WebAppShortcutImpl.Domain)) { WebAppShortcutDeserializer() }
}
