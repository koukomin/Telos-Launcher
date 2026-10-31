package de.mm20.launcher2.ui.settings.crashreporter
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.crashreporter.CrashReport
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

class CrashReportScreenVM : ViewModel() {
    val notFound = mutableStateOf(false)

    fun getCrashReport(fileName: String) = flow<CrashReport?> {
        val report = try {
            CrashReporter.getCrashReport(fileName)
        } catch (e: Exception) {
            notFound.value = true
            null
        }
        emit(report)
    }

    fun deleteCrashReport(crashReport: CrashReport): Boolean {
        return try {
            File(crashReport.filePath).delete()
        } catch (e: SecurityException) {
            false
        }
    }

    fun getDeviceInformation(context: Context): String {
        return CrashReporter.getDeviceInformation(context)
    }

    fun createIssue(context: Context, crashReport: CrashReport) {
        val stacktrace = crashReport.stacktrace?.lines()?.let {
            if (it.size > 15) it.subList(0, 15)
                .joinToString("\n") + "\n[${it.size - 15} lines truncated]"
            else it.joinToString("\n")
        } ?: ""
        val body =
            "## Description\n\n" +
                    "*Please provide as many information about the crash as possible (What did you do before the crash happened? Steps to reproduce?)*\n\n" +
                    "## Stack trace\n\n" +
                    "```\n" +
                    "${stacktrace}\n" +
                    "```\n\n" +
                    "## Device info\n" +
                    "${getDeviceInformation(context).replace("\n", "<br>")}\n"
        val url = "https://github.com/koukomin/Telos-Launcher/issues/new?labels=crash+report&body=${
            URLEncoder.encode(
                body,
                "utf8"
            )
        }"
        context.tryStartActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        })
    }

    fun shareCrashReport(context: Context, crashReport: CrashReport) {
        viewModelScope.launch {
            val shared = withContext(Dispatchers.IO) {
                ShareActions.shareFile(
                    context,
                    File(crashReport.filePath),
                    "*/*",
                    chooserTitle = context.getString(I18nR.string.au3_sysb_crash_share_via),
                    text = CrashReporter.getDeviceInformation(context),
                )
            }
            if (!shared) {
                Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Copies the whole report as text, for when saving or sharing the file does not work */
    fun copyCrashReport(context: Context, crashReport: CrashReport) {
        val text = buildString {
            appendLine(crashReport.summary)
            crashReport.stacktrace?.let { appendLine(it) }
            appendLine()
            append(CrashReporter.getDeviceInformation(context))
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("crash report", text))
    }

}
