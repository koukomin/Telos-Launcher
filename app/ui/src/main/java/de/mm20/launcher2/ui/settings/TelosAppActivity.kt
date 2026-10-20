package de.mm20.launcher2.ui.settings

/** Document task host: a stable data URI gives each built-in app its own Recents entry. */
class TelosAppActivity : SettingsActivity() {
    // An explicitly closed app must leave Recents. Android may still recreate an
    // unfinished task after process death, restoring its saved navigation.
    override fun finish() {
        if (isTaskRoot) finishAndRemoveTask() else super.finish()
    }
}
