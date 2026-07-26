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
        get() = launcherDataStore.data.map { it.fileSearch.fileSearchProviders }

    val typeFilters: Flow<FileTypeFilters>
        get() = launcherDataStore.data.map {
            FileTypeFilters(
                documents = it.fileSearch.fileSearchDocuments,
                images = it.fileSearch.fileSearchImages,
                videos = it.fileSearch.fileSearchVideos,
                music = it.fileSearch.fileSearchMusic,
                other = it.fileSearch.fileSearchOther,
            )
        }

    fun setDocuments(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearch = it.fileSearch.copy(fileSearchDocuments = enabled)) }
    }

    fun setImages(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearch = it.fileSearch.copy(fileSearchImages = enabled)) }
    }

    fun setVideos(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearch = it.fileSearch.copy(fileSearchVideos = enabled)) }
    }

    fun setMusic(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearch = it.fileSearch.copy(fileSearchMusic = enabled)) }
    }

    fun setOther(enabled: Boolean) {
        launcherDataStore.update { it.copy(fileSearch = it.fileSearch.copy(fileSearchOther = enabled)) }
    }

    val excludedFolders: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.fileSearch.fileSearchExcludedFolders }

    fun addExcludedFolder(path: String) {
        launcherDataStore.update {
            it.copy(fileSearch = it.fileSearch.copy(fileSearchExcludedFolders = it.fileSearch.fileSearchExcludedFolders + path.trimEnd('/')))
        }
    }

    fun removeExcludedFolder(path: String) {
        launcherDataStore.update {
            it.copy(fileSearch = it.fileSearch.copy(fileSearchExcludedFolders = it.fileSearch.fileSearchExcludedFolders - path))
        }
    }

    val localFiles
        get() = launcherDataStore.data.map { it.fileSearch.fileSearchProviders.contains("local") }

    fun setLocalFiles(localFiles: Boolean) {
        launcherDataStore.update {
            if (localFiles) {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders + "local"))
            } else {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders - "local"))
            }
        }
    }

    val gdriveFiles
        get() = launcherDataStore.data.map { it.fileSearch.fileSearchProviders.contains("gdrive") }

    fun setGdriveFiles(gdriveFiles: Boolean) {
        launcherDataStore.update {
            if (gdriveFiles) {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders + "gdrive"))
            } else {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders - "gdrive"))
            }
        }
    }

    val nextcloudFiles
        get() = launcherDataStore.data.map { it.fileSearch.fileSearchProviders.contains("nextcloud") }

    fun setNextcloudFiles(nextcloudFiles: Boolean) {
        launcherDataStore.update {
            if (nextcloudFiles) {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders + "nextcloud"))
            } else {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders - "nextcloud"))
            }
        }
    }

    val owncloudFiles
        get() = launcherDataStore.data.map { it.fileSearch.fileSearchProviders.contains("owncloud") }

    fun setOwncloudFiles(owncloudFiles: Boolean) {
        launcherDataStore.update {
            if (owncloudFiles) {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders + "owncloud"))
            } else {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders - "owncloud"))
            }
        }
    }

    val enabledPlugins: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.fileSearch.fileSearchProviders - "local" - "gdrive" - "nextcloud" - "owncloud" }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        launcherDataStore.update {
            if (enabled) {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders + authority))
            } else {
                it.copy(fileSearch = it.fileSearch.copy(fileSearchProviders = it.fileSearch.fileSearchProviders - authority))
            }
        }
    }
}