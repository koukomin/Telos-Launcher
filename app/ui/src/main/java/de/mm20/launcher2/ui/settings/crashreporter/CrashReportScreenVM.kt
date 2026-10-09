package de.mm20.launcher2.ui.settings.crashreporter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.crashreporter.CrashReport
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.flow
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

        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            File(crashReport.filePath)
        )
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_TEXT, CrashReporter.getDeviceInformation(context))
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.tryStartActivity(
            Intent.createChooser(intent, context.getString(R.string.au3_sysb_crash_share_via))
        )
    }

}
