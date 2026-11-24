package de.mm20.launcher2.ui.store

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.i18n.R as I18nR

@Composable
internal fun StoreRow.actionLabel(): String = when {
    item.installedVersionCode == null && options.trackOnly -> stringResource(I18nR.string.au_store_action_page)
    item.installedVersionCode == null -> stringResource(I18nR.string.hc_install)
    updateAvailable && options.trackOnly -> stringResource(I18nR.string.au_store_action_page)
    updateAvailable -> stringResource(I18nR.string.au_store_action_update)
    else -> stringResource(I18nR.string.hc_open)
}

internal fun StoreRow.isOpenAction(): Boolean = item.installedVersionCode != null && !updateAvailable

@Composable
internal fun AppSource.summary(): String = when (this) {
    is AppSource.GitHub -> "$owner/$repo · GitHub"
    is AppSource.GitLab -> "$path · GitLab"
    is AppSource.Gitea -> "$owner/$repo · $host"
    is AppSource.FDroid -> if (repoUrl.contains("izzysoft")) "IzzyOnDroid" else if (repoUrl.contains("f-droid.org")) "F-Droid" else repoUrl.substringAfter("://").substringBefore('/')
    is AppSource.SourceForge -> "$project · SourceForge"
    is AppSource.Html -> pageUrl.substringAfter("://").substringBefore('/')
    is AppSource.DirectApk -> stringResource(I18nR.string.au_store_src_direct)
    is AppSource.AffiliatePlayStore -> stringResource(I18nR.string.au_store_src_play)
    is AppSource.AffiliateDirect -> stringResource(I18nR.string.au_store_src_direct)
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceAtMost(units.size - 1)
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
