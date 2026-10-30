package com.telos.desktop.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.telos.desktop.files.ZipArchives
import com.telos.desktop.linux.Sys
import java.io.File

// === TELOS_PENDING_REVIEW_START: telos_desktop_kwin_parity_upgrade ===
enum class TelosOpenTarget { Files, Media, Host }

object TelosFileHandler {
    var onOpenFiles: ((File) -> Unit)? = null
    var onOpenMedia: ((File) -> Unit)? = null
    var pendingMedia by mutableStateOf<File?>(null)

    private val mediaExt = setOf(
        "mp3", "flac", "ogg", "wav", "m4a", "aac", "opus",
        "mp4", "mkv", "webm", "avi", "mov", "m4v",
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
        "heic", "heif", "raw", "cr2", "nef", "arw", "dng",
    )

    fun classify(file: File): TelosOpenTarget = when {
        file.isDirectory || ZipArchives.isZip(file) -> TelosOpenTarget.Files
        file.extension.lowercase() in mediaExt -> TelosOpenTarget.Media
        else -> TelosOpenTarget.Host
    }

    fun open(file: File): Boolean {
        if (!file.exists()) return false
        return when (classify(file)) {
            TelosOpenTarget.Files -> {
                onOpenFiles?.invoke(file) ?: Sys.runBg("xdg-open", file.absolutePath)
                true
            }
            TelosOpenTarget.Media -> {
                pendingMedia = file
                onOpenMedia?.invoke(file) ?: Sys.runBg("xdg-open", file.absolutePath)
                true
            }
            TelosOpenTarget.Host -> Sys.runBg("xdg-open", file.absolutePath)
        }
    }
}
// === TELOS_PENDING_REVIEW_END: telos_desktop_kwin_parity_upgrade ===
