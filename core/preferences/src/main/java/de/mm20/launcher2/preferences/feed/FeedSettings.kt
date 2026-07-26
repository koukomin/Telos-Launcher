package de.mm20.launcher2.preferences.feed

import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class FeedSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {

    val enabled
        get() = launcherDataStore.data.map { it.gestures.gesturesSwipeRight is GestureAction.Feed }

    fun setEnabled(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesSwipeRight = if (enabled) GestureAction.Feed else GestureAction.NoAction))
        }
    }

    val providerPackage
        get() = launcherDataStore.data.map { it.feed.feedProviderPackage }

    fun setProviderPackage(providerPackage: String?) {
        launcherDataStore.update {
            it.copy(feed = it.feed.copy(feedProviderPackage = providerPackage))
        }
    }

}