package de.mm20.launcher2.data.comms

import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.MessageRepository
import de.mm20.launcher2.search.VirtualAppProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataCommsModule = module {
    single<ContactDirectoryRepository> { ContactDirectoryRepositoryImpl(androidContext(), get()) }
    single<CallLogRepository> { CallLogRepositoryImpl() }
    single<MessageRepository> { MessageRepositoryImpl() }
    // === TELOS_PENDING_REVIEW_START: virtual_app_koin_fix ===
    factory<VirtualAppProvider>(org.koin.core.qualifier.named("commsVirtualAppProvider")) { CommsVirtualAppProvider(androidContext()) }
    // === TELOS_PENDING_REVIEW_END: virtual_app_koin_fix ===
}
