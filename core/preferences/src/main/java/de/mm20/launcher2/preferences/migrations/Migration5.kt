package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.BaseLayout
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.LauncherSettingsData

class Migration5  : DataMigration<LauncherSettingsData>  {
    override suspend fun cleanUp() {
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        return currentData.copy(
            schemaVersion = 5,
            gestures = currentData.gestures.copy(
                gesturesSwipeDown = if (currentData.ui.uiBaseLayout == BaseLayout.PullDown) GestureAction.Search else currentData.gestures.gesturesSwipeDown,
                gesturesSwipeLeft = if (currentData.ui.uiBaseLayout == BaseLayout.Pager) GestureAction.Search else currentData.gestures.gesturesSwipeLeft,
                gesturesSwipeRight = if (currentData.ui.uiBaseLayout == BaseLayout.PagerReversed) GestureAction.Search else currentData.gestures.gesturesSwipeRight,
                gesturesSwipeUp = GestureAction.Widgets(),
            ),
            home = currentData.home.copy(
                homeScreenWidgets = !currentData.clock.clockWidgetFillHeight,
            ),
        )
    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 5
    }
}
