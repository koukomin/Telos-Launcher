package de.mm20.launcher2.network

import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.network.impl.DefaultAppDirectory
import de.mm20.launcher2.network.impl.DefaultBlocklistController
import de.mm20.launcher2.network.impl.DefaultDnsController
import de.mm20.launcher2.network.impl.DefaultFirewallController
import de.mm20.launcher2.network.impl.DefaultLogController
import de.mm20.launcher2.network.impl.DefaultNetworkSettings
import de.mm20.launcher2.network.impl.DefaultWireguardController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Telos Network. Every part is registered under its interface from the `api` package, so another
 * implementation only has to replace the matching line here.
 */
val networkModule = module {
    single<NetworkSettings> { DefaultNetworkSettings(androidContext()) }
    single<AppDirectory> { DefaultAppDirectory(androidContext()) }
    single<LogController> { DefaultLogController(androidContext(), get(), get()) }
    single<BlocklistController> { DefaultBlocklistController(androidContext()) }
    single<FirewallController> { DefaultFirewallController(androidContext(), get(), get()) }
    single<DnsController> { DefaultDnsController(androidContext()) }
    single<WireguardController> { DefaultWireguardController(androidContext()) }
    single {
        NetworkEngine(
            context = androidContext(),
            settings = get(),
            components = listOf(get<DnsController>(), get<WireguardController>(), get<BlocklistController>()),
        )
    }
}
