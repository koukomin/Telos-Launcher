package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.engine.HttpDownloadEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Phase 2 adds a torrent engine and phase 3 a media (yt-dlp) engine to the list of engines here.
 */
val downloadsModule = module {
    single { DownloadSettings(androidContext()) }
    single { DownloadStore(androidContext()) }
    single { DownloadFiles(androidContext()) }
    single { DownloadNotifier(androidContext(), get()) }
    single {
        DownloadManager(
            context = androidContext(),
            store = get(),
            settings = get(),
            files = get(),
            engines = listOf(HttpDownloadEngine()),
            notifier = get(),
        )
    }
}
