package de.mm20.launcher2.data.comms

import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.MessageRepository
import de.mm20.launcher2.comms.repository.RadioRepository
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.search.VirtualAppDeserializer
import de.mm20.launcher2.search.SearchableDeserializer
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
        HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            engine {
                config { dns(de.mm20.launcher2.data.comms.radio.FallbackDns) }
            }
            install(io.ktor.client.plugins.HttpTimeout)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single { RadioBrowserClient(get()) }
    single<RadioRepository> { RadioRepositoryImpl(androidContext(), get()) }
    single<de.mm20.launcher2.comms.tv.TvRepository> { de.mm20.launcher2.data.comms.tv.TvRepositoryImpl(androidContext()) }

    // === TELOS_PENDING_REVIEW_START: virtual_app_koin_fix ===
    factory<VirtualAppProvider>(org.koin.core.qualifier.named("commsVirtualAppProvider")) { CommsVirtualAppProvider(androidContext()) }
    // === TELOS_PENDING_REVIEW_END: virtual_app_koin_fix ===

    // One deserializer per virtual app domain, so pinned / docked virtual apps survive a reload.
    listOf(
        VirtualPhoneApp.Domain, VirtualMessagesApp.Domain, VirtualRadioApp.Domain,
        VirtualMusicApp.Domain, VirtualVideoApp.Domain, VirtualPhotosApp.Domain,
        VirtualFilesApp.Domain, VirtualCalculatorApp.Domain, VirtualVoiceRecorderApp.Domain,
        VirtualScreenRecorderApp.Domain, VirtualScreenshotApp.Domain, VirtualNotesApp.Domain,
        VirtualCalendarApp.Domain, VirtualDownloadsApp.Domain, VirtualMediaApp.Domain,
        VirtualNetworkApp.Domain,
    ).forEach { domain ->
        factory<SearchableDeserializer>(org.koin.core.qualifier.named(domain)) {
            VirtualAppDeserializer(domain) { getAll<VirtualAppProvider>() }
        }
    }
}
