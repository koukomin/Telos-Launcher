package de.mm20.launcher2.ui.settings.crashreporter

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.crashreporter.BuildConfig
import de.mm20.launcher2.crashreporter.CrashReport
import de.mm20.launcher2.crashreporter.CrashReportType
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.launch
import org.koin.core.component.inject

class CrashReporterScreenVM: ViewModel(), org.koin.core.component.KoinComponent {
    private val uiSettings: de.mm20.launcher2.preferences.ui.UiSettings by inject()

    fun setShowCrashes(showCrashes: Boolean) {
        this.showCrashes.value = showCrashes
        updateReports()
    }

    fun setShowExceptions(showExceptions: Boolean) {
        this.showExceptions.value = showExceptions
        updateReports()
    }

    private fun updateReports() {
        val exceptions = showExceptions.value == true
        val crashes = showCrashes.value == true
        reports.value = _reports?.filter {
            it.type == CrashReportType.Exception && exceptions ||
            it.type == CrashReportType.Crash && crashes
        }
    }

    val enabled = mutableStateOf(CrashReporter.isEnabled())

    fun setEnabled(context: android.content.Context, value: Boolean) {
        // the SharedPreferences mirror is written right away; the setting is the source of truth
        CrashReporter.setEnabled(context, value)
        enabled.value = value
        uiSettings.setCrashReporterEnabled(value)
    }

    fun deleteAll() {
        viewModelScope.launch {
            CrashReporter.deleteAllReports()
            reload()
        }
    }

    val showExceptions = mutableStateOf(false)
    val showCrashes = mutableStateOf(true)

    val reports = mutableStateOf<List<CrashReport>?>(null)
    private var _reports: List<CrashReport>? = null

    private var initialized = false

    /**
     * (Re)load the list of reports from disk. Called whenever the screen enters the composition,
     * so reports deleted from the detail screen disappear from the list.
     */
    fun reload() {
        viewModelScope.launch {
            _reports = CrashReporter.getCrashReports()
            if (!initialized) {
                initialized = true
                showExceptions.value = BuildConfig.DEBUG
            }
            updateReports()
        }
    }

    init {
        viewModelScope.launch {
            uiSettings.crashReporterEnabled.collect { enabled.value = it }
        }
        reload()
    }

}