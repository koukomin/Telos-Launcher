package de.mm20.launcher2.ui.calendar

import de.mm20.launcher2.backup.Backupable
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val telosCalendarModule = module {
    factory<Backupable>(named<CalendarBackup>()) { CalendarBackup(androidContext()) }
}
