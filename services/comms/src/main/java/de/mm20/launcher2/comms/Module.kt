package de.mm20.launcher2.comms

import de.mm20.launcher2.comms.backup.CommsBackupManager
import de.mm20.launcher2.comms.privacy.VaultSmsRouterImpl
import de.mm20.launcher2.comms.radio.NetworkAutomationWatcher
import de.mm20.launcher2.comms.sms.VaultSmsRouter
import de.mm20.launcher2.comms.t9.T9SearchEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val commsModule = module {
    single(createdAtStart = true) { de.mm20.launcher2.comms.remote.RemotePhonebook.also { it.init(androidContext()) } }
    single(createdAtStart = true) { de.mm20.launcher2.comms.sip.SipController(androidContext(), get()) }
    single { T9SearchEngine() }
    single { CommsBackupManager(get(), get()) }
    single<VaultSmsRouter> { VaultSmsRouterImpl(get()) }
    single(createdAtStart = true) { NetworkAutomationWatcher(androidContext(), get()) }
    // Telos TV: all lazy (nothing is created or downloaded until TV is opened). TvRepository comes from :data:comms.
    single {
        de.mm20.launcher2.comms.tv.TvCatalog(androidContext()).also { catalog ->
            // optional extra Greek sources: started only when TV is opened (resolved lazily here)
            catalog.openHook = { get<de.mm20.launcher2.comms.tv.TvExtraSources>().launchOnOpen() }
        }
    }
    single { de.mm20.launcher2.comms.tv.TvExtraCache(androidContext()) }
    single { de.mm20.launcher2.comms.tv.TvEpg(androidContext(), get(), get(), get()) }
    single { de.mm20.launcher2.comms.tv.TvExtraSources(get(), get(), get(), get()) }
    single { de.mm20.launcher2.comms.tv.TvSettings(androidContext()) }
    single { de.mm20.launcher2.comms.tv.TvLibrary(get(), get()) }
    single { de.mm20.launcher2.comms.tv.TvBackup(get(), get()) }
    single { de.mm20.launcher2.comms.tv.TvPlayerController(androidContext(), get(), get(), get()) }
}
