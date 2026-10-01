package de.mm20.launcher2.store.installer

import java.io.File

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Failed(val reason: String, val cause: Throwable? = null) : DownloadResult
}

/** Downloads a release's APK to local storage so [AppInstaller] can install it. */
interface Downloader {
    suspend fun download(url: String, suggestedFileName: String): DownloadResult
}
