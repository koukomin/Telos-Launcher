package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which broad file categories are included in local file search results. */
data class FileTypeFilters(
    val documents: Boolean,
    val images: Boolean,
    val videos: Boolean,
    val music: Boolean,
    val other: Boolean,
) {
    val allEnabled: Boolean
        get() = documents && images && videos && music && other
}

class FileSearchSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val enabledProviders: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.fileSearchProviders }

    val typeFilters: Flow<FileTypeFilters>
        get() = launcherDataStore.data.map {
            FileTypeFilters(
                documents = it.fileSearchDocuments,
                images = it.fileSearchImages,
                videos = it.fileSearchVideos,
                music = it.fileSearchMusic,
                other = it.fileSearchOther,
            )
        }

    fun setDocuments(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearchDocuments = enabled) }
    }

    fun setImages(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearchImages = enabled) }
    }

    fun setVideos(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearchVideos = enabled) }
    }

    fun setMusic(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearchMusic = enabled) }
    }

    fun setOther(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearchOther = enabled) }
    }

    val excludedFolders: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.fileSearchExcludedFolders }

    fun addExcludedFolder(path: String) {
        launcherDataStore.update {
            it.copy(fileSearchExcludedFolders = it.fileSearchExcludedFolders + path.trimEnd('/'))
        }
    }

    fun removeExcludedFolder(path: String) {
        launcherDataStore.update {
            it.copy(fileSearchExcludedFolders = it.fileSearchExcludedFolders - path)
        }
    }

    val localFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("local") }

    fun setLocalFiles(localFiles: Boolean) {
        launcherDataStore.update {
            if (localFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "local")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "local")
            }
        }
    }

    val gdriveFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("gdrive") }

    fun setGdriveFiles(gdriveFiles: Boolean) {
        launcherDataStore.update {
            if (gdriveFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "gdrive")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "gdrive")
            }
        }
    }

    val nextcloudFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("nextcloud") }

    fun setNextcloudFiles(nextcloudFiles: Boolean) {
        launcherDataStore.update {
            if (nextcloudFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "nextcloud")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "nextcloud")
            }
        }
    }

    val owncloudFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("owncloud") }

    fun setOwncloudFiles(owncloudFiles: Boolean) {
        launcherDataStore.update {
            if (owncloudFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "owncloud")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "owncloud")
            }
        }
    }

    val enabledPlugins: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.fileSearchProviders - "local" - "gdrive" - "nextcloud" - "owncloud" }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        launcherDataStore.update {
            if (enabled) {
                it.copy(fileSearchProviders = it.fileSearchProviders + authority)
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - authority)
            }
        }
    }
}