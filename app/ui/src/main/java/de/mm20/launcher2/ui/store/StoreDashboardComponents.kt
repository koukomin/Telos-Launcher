package de.mm20.launcher2.ui.store

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.ui.component.TelosCyberHeading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

@Composable
internal fun EmptyMessage(title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StoreItemRow(
    row: StoreRow,
    installState: StoreInstallUiState,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onDetailsClick: () -> Unit,
) {
    val item = row.item
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val cyan = Color(0xFF00F2FE)
    val pink = Color(0xFFFF007F)
    val darkCard = Color(0xFF0D0E15)

    Box {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = darkCard.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .border(
                1.dp,
                if (row.updateAvailable) pink.copy(alpha = 0.6f) else cyan.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(item.packageName, item.displayName, 48, item.installedVersionCode, item.source)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (row.updateAvailable) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "[UPDATE]",
                                style = MaterialTheme.typography.labelSmall,
                                color = pink,
                                fontWeight = FontWeight.Black,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        item.versionLine(row.updateAvailable),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (row.updateAvailable) pink else cyan
                    )

                    val installerPkg: String? = remember(item.packageName) {
                        runCatching {
                            val pm = context.packageManager
                            if (android.os.Build.VERSION.SDK_INT >= 30) {
                                pm.getInstallSourceInfo(item.packageName).installingPackageName
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getInstallerPackageName(item.packageName)
                            }
                        }.getOrNull()
                    }

                    val storeLabel = when (installerPkg) {
                        "com.android.vending" -> "Google Play"
                        context.packageName -> "Telos Store"
                        null -> "Telos Store"
                        else -> runCatching {
                            val pm = context.packageManager
                            @Suppress("DEPRECATION")
                            val info = pm.getApplicationInfo(installerPkg, 0)
                            pm.getApplicationLabel(info).toString()
                        }.getOrDefault(installerPkg)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (installerPkg != null && installerPkg.length > 0 && installerPkg != context.packageName) {
                            AppIcon(packageName = installerPkg, name = storeLabel, size = 16)
                        }
                        Text(
                            text = storeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (installerPkg == "com.android.vending") Color(0xFF34A853) else cyan
                        )
                    }

                    val sizeText = item.latestRelease?.size?.let { formatBytes(it) }
                    Text(listOfNotNull(item.source.summary(), sizeText, row.options.category.ifBlank { null },
                        if (row.options.trackOnly) stringResource(I18nR.string.hf_store_track_only) else null,
                        if (row.options.pinned) stringResource(I18nR.string.au_store_pinned) else null).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.7f))
                }
            }
            StoreTransferProgress(item.id)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDetailsClick) { Text(stringResource(I18nR.string.hc_details)) }
                StoreActionButton(row, installState, onActionClick)
            }
        }
    }
    DropdownMenu(menu, { menu = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(I18nR.string.hc_share_link)) },
            onClick = {
                menu = false
                de.mm20.launcher2.ui.common.share.ShareActions.shareText(context, de.mm20.launcher2.store.parser.StoreUrlParser.toUrl(item.source))
            },
        )
        // the APK can only be shared for an app that is installed
        if (item.installedVersionCode != null) DropdownMenuItem(
            text = { Text(stringResource(I18nR.string.menu_share_apk_file)) },
            onClick = {
                menu = false
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { de.mm20.launcher2.ui.common.share.ShareActions.shareApk(context, item.packageName) }
                    if (!ok) android.widget.Toast.makeText(context, context.getString(I18nR.string.au9_share_apk_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    }
}

@Composable
private fun StoreItem.versionLine(update: Boolean): String {
    val installed = installedVersionName ?: installedVersionCode?.toString()
    val latest = latestRelease?.version?.takeIf { it != "unknown" }
    return when {
        installed == null && latest != null -> stringResource(I18nR.string.au_store_version_available, latest)
        installed == null -> stringResource(I18nR.string.hf_store_not_installed)
        update && latest != null -> "v$installed → v$latest"
        else -> "v$installed"
    }
}

@Composable
internal fun AppIcon(packageName: String, name: String, size: Int, installedVersion: Long? = null, source: de.mm20.launcher2.store.model.AppSource? = null) {
    val context = LocalContext.current
    val appRepository: de.mm20.launcher2.applications.AppRepository = org.koin.compose.koinInject()
    val iconService: de.mm20.launcher2.icons.IconService = org.koin.compose.koinInject()
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.dp.roundToPx() }
    
    // Defer DB/PackageManager lookups aggressively to IO and cache locally to prevent List UI jank
    val launcherIcon by remember(packageName, installedVersion, sizePx) {
        if (packageName.isBlank()) {
            kotlinx.coroutines.flow.flowOf<de.mm20.launcher2.icons.LauncherIcon?>(null)
        } else {
            appRepository.findOne(packageName, android.os.Process.myUserHandle())
                .flatMapLatest { app ->
                    if (app == null) kotlinx.coroutines.flow.flowOf<de.mm20.launcher2.icons.LauncherIcon?>(null)
                    else iconService.getIcon(app, sizePx)
                }
        }
    }.collectAsStateWithLifecycle(null)

    var localResolved by remember(packageName, installedVersion) { mutableStateOf(false) }
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, packageName, installedVersion) {
        value = if (packageName.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val d: Drawable = context.packageManager.getApplicationIcon(packageName)
                d.toBitmap(size * 3, size * 3).asImageBitmap()
            }.getOrNull()
        }
        localResolved = true
    }
    
    val resolver: de.mm20.launcher2.store.icons.StoreIconResolver = org.koin.compose.koinInject()
    val remote by androidx.compose.runtime.produceState<List<String>>(emptyList(), source, packageName, localResolved, launcherIcon != null || bitmap != null) {
        if (localResolved && source != null && launcherIcon == null && bitmap == null) {
            value = withContext(Dispatchers.IO) { resolver.candidates(source, packageName) }
        } else {
            value = emptyList()
        }
    }
    var remoteIndex by remember(source, packageName, remote) { mutableIntStateOf(0) }
    
    val li = launcherIcon
    val b = bitmap
    
    if (li != null) {
        de.mm20.launcher2.ui.component.ShapedLauncherIcon(size = size.dp, icon = { li })
    } else if (b != null) {
        Image(b, contentDescription = null, modifier = Modifier.size(size.dp))
    } else if (remoteIndex < remote.size) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(remote[remoteIndex])
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            onError = { remoteIndex++ }
        )
    } else {
        Surface(Modifier.size(size.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
internal fun StoreActionButton(row: StoreRow, state: StoreInstallUiState, onClick: () -> Unit) {
    val downloader: de.mm20.launcher2.store.installer.Downloader = org.koin.compose.koinInject()
    val transfers by downloader.progress.collectAsStateWithLifecycle()
    val transfer = transfers[row.item.id]
    val paused = transfer?.status == de.mm20.launcher2.store.installer.StoreDownloadStatus.Paused
    if (state == StoreInstallUiState.Downloading || paused) {
        OutlinedButton(onClick = {
            if (paused) { downloader.resume(row.item.id); onClick() }
            else downloader.pause(row.item.id)
        }) { Text(stringResource(if (paused) I18nR.string.telos_store_resume else I18nR.string.telos_store_pause)) }
        return
    }
    when (state) {
        StoreInstallUiState.Installing -> OutlinedButton(onClick = {}, enabled = false) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(I18nR.string.hf_store_installing))
        }
        StoreInstallUiState.Failed -> OutlinedButton(onClick = onClick) { Text(stringResource(I18nR.string.hc_retry)) }
        else -> {
            val label = row.actionLabel()
            if (row.isOpenAction()) OutlinedButton(onClick = onClick) { Text(label) } else Button(onClick = onClick) { Text(label) }
        }
    }
}

