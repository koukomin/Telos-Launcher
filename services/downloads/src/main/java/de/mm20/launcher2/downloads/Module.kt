package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.engine.HttpDownloadEngine
import de.mm20.launcher2.downloads.engine.TorrentController
import de.mm20.launcher2.downloads.engine.TorrentDownloadEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The engines of the download manager: HTTP (phase 1), torrents (phase 2). Phase 3 adds a media (yt-dlp) engine here.
 */
val downloadsModule = module {
    single { DownloadSettings(androidContext()) }
    single { DownloadStore(androidContext()) }
    single { DownloadFiles(androidContext()) }
    single { DownloadNotifier(androidContext(), get()) }
    single { TorrentController(get()) }
    single {
        DownloadManager(
            context = androidContext(),
            store = get(),
            settings = get(),
            files = get(),
            engines = listOf(HttpDownloadEngine(), TorrentDownloadEngine(androidContext(), get())),
            notifier = get(),
        )
    }
}
