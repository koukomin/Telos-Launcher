package de.mm20.launcher2.network

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.network.nextdns.NextDnsRepository
import de.mm20.launcher2.network.nextdns.NextDnsViewModel

val NetworkModule = module {
    single<VirtualAppProvider> { de.mm20.launcher2.network.nextdns.NetworkVirtualAppProvider(get()) }
    single { NextDnsRepository(get()) }
    viewModel { NextDnsViewModel(get()) }
}
