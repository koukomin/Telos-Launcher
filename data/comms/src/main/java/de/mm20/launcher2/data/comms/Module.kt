package de.mm20.launcher2.data.comms

import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.MessageRepository
import de.mm20.launcher2.comms.repository.RadioRepository
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.data.comms.radio.RadioBrowserClient
import de.mm20.launcher2.data.comms.radio.RadioRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataCommsModule = module {
    single<ContactDirectoryRepository> { ContactDirectoryRepositoryImpl(androidContext(), get()) }
    single<CallLogRepository> { CallLogRepositoryImpl(androidContext(), get()) }
    single<MessageRepository> { MessageRepositoryImpl() }
    single<de.mm20.launcher2.comms.repository.SpamRepository> { SpamRepositoryImpl(androidContext()) }

    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single { RadioBrowserClient(get()) }
    single<RadioRepository> { RadioRepositoryImpl(androidContext(), get()) }

    // === TELOS_PENDING_REVIEW_START: virtual_app_koin_fix ===
    factory<VirtualAppProvider>(org.koin.core.qualifier.named("commsVirtualAppProvider")) { CommsVirtualAppProvider(androidContext()) }
    // === TELOS_PENDING_REVIEW_END: virtual_app_koin_fix ===
}
