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
    single { T9SearchEngine() }
    single { CommsBackupManager(get(), get()) }
    single<VaultSmsRouter> { VaultSmsRouterImpl(get()) }
    single(createdAtStart = true) { NetworkAutomationWatcher(androidContext()) }
}
