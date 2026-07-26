package de.mm20.launcher2.preferences.media

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MediaSettingsData(
    val allowList: Set<String>,
    val denyList: Set<String>,
)

class MediaSettings internal constructor(
    private val launcherDataStore: LauncherDataStore
) : Flow<MediaSettingsData> by (launcherDataStore.data.map {
    MediaSettingsData(
        it.media.mediaAllowList,
        it.media.mediaDenyList
    )
}) {
    val allowList
        get() = launcherDataStore.data.map { it.media.mediaAllowList }

    fun setLists(allowList: Set<String>, denyList: Set<String>) {
        launcherDataStore.update {
            it.copy(media = it.media.copy(mediaAllowList = allowList, mediaDenyList = denyList))
        }
    }

    val denyList
        get() = launcherDataStore.data.map { it.media.mediaDenyList }
}