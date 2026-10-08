package de.mm20.launcher2.ui.notes

import de.mm20.launcher2.backup.Backupable
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

val notesModule = module {
    single { NotesStore(androidContext()) }
    factory<Backupable>(named<NotesStore>()) { get<NotesStore>() }
}
