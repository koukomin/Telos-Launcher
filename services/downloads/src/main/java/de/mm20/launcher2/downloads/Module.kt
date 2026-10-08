package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.engine.HttpDownloadEngine
import de.mm20.launcher2.downloads.engine.MediaDownloadEngine
import de.mm20.launcher2.downloads.media.MediaRuntime
import de.mm20.launcher2.downloads.engine.TorrentController
import de.mm20.launcher2.downloads.engine.TorrentDownloadEngine
import org.koin.android.ext.koin.androidContext
import de.mm20.launcher2.backup.Backupable
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The engines of the download manager: HTTP (phase 1), torrents (phase 2), media through yt-dlp (phase 3, needs the optional runtime).
 */
val downloadsModule = module {
    single { DownloadSettings(androidContext()) }
    single { DownloadStore(androidContext()) }
    single { DownloadFiles(androidContext()) }
    single { DownloadNotifier(androidContext(), get()) }
    single { TorrentController(get()) }
    single { MediaRuntime(androidContext()) }
    factory<Backupable>(named<DownloadsBackup>()) { DownloadsBackup(get(), get(), get()) }
    single {
        DownloadManager(
            context = androidContext(),
            store = get(),
            settings = get(),
            files = get(),
            engines = listOf(HttpDownloadEngine(), TorrentDownloadEngine(androidContext(), get()), MediaDownloadEngine(androidContext(), get())),
            notifier = get(),
        )
    }
}
