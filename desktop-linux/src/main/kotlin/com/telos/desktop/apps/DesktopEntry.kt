package com.telos.desktop.apps

import java.io.File

enum class TelosAppCategory {
    System, Utility, Development, Network, AudioVideo, Graphics, Game, Office, Settings, Other
}

data class DesktopEntry(
    val file: File,
    val name: String,
    val comment: String,
    val icon: String,
    val exec: String,
    val path: String?,
    val terminal: Boolean,
    val noDisplay: Boolean,
    val categories: List<String>,
    val telosCategory: TelosAppCategory,
)
