package com.telos.desktop.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.files.ArchivePasswordException
import com.telos.desktop.files.CloudApi
import com.telos.desktop.files.CloudOAuth
import com.telos.desktop.files.DavSecrets
import com.telos.desktop.files.NetworkPlaces
import com.telos.desktop.files.OAuthSecrets
import com.telos.desktop.files.WebDavClient
import com.telos.desktop.files.ZipArchives
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.CandyIcon
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.xdg.XdgTrash
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TelosFilesNav {
    var path by mutableStateOf(File(System.getProperty("user.home")))
}

private data class FilesRow(
    val name: String,
    val directory: Boolean,
    val disk: File?,
    val zipInner: String?,
    val size: Long,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelosFilesApp(
    start: File? = null,
    showHiddenFiles: Boolean = false,
    extraPlaces: List<com.telos.desktop.files.NetworkPlace> = emptyList(),
    onPathChange: (File) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var path by remember(start?.absolutePath) { mutableStateOf(start ?: TelosFilesNav.path) }
    androidx.compose.runtime.LaunchedEffect(path) {
        TelosFilesNav.path = path
        onPathChange(path)
    }
    var zipPrefix by remember { mutableStateOf("") }
    var archiveSecret by remember { mutableStateOf("") }
    var needPassword by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var davHref by remember { mutableStateOf<String?>(null) }
    var davUser by remember { mutableStateOf("") }
    var davPlaceUri by remember { mutableStateOf<String?>(null) }
    var davRows by remember { mutableStateOf<List<FilesRow>>(emptyList()) }
    var cloudTrail by remember { mutableStateOf(listOf("root")) }
    val scope = rememberCoroutineScope()
    val dir = path
    val inZip = ZipArchives.isArchive(dir)
    val trashRoot = remember { XdgTrash.filesDir() }
    val inTrash = remember(dir, tick) {
        val p = dir.absoluteFile
        val tdir = trashRoot.absoluteFile
        p == tdir || p.path.startsWith(tdir.path + File.separator)
    }
    androidx.compose.runtime.LaunchedEffect(davHref, davUser, davPlaceUri, tick, cloudTrail) {
        val href = davHref
        if (href == null) {
            davRows = emptyList()
            return@LaunchedEffect
        }
        davRows = withContext(Dispatchers.IO) {
            val kind = CloudOAuth.detect(davPlaceUri ?: href)
            if (kind != null) {
                val key = davPlaceUri ?: CloudOAuth.placeUri(kind)
                val tokens = OAuthSecrets.get(key) ?: return@withContext emptyList()
                val fresh = CloudOAuth.refresh(tokens) ?: tokens
                OAuthSecrets.put(key, fresh)
                val folder = cloudTrail.lastOrNull() ?: "root"
                CloudApi.list(kind, fresh.access, folder).map {
                    FilesRow(it.name, it.directory, null, it.id, it.size)
                }
            } else {
                val pw = DavSecrets.get(davPlaceUri ?: href) ?: DavSecrets.get(href) ?: ""
                WebDavClient.list(href, davUser, pw).map {
                    FilesRow(it.name, it.directory, null, it.href, it.size)
                }
            }
        }
    }
    val rows = remember(dir, query, showHiddenFiles, tick, zipPrefix, archiveSecret, davHref, davRows) {
        if (davHref != null) {
            return@remember if (query.isBlank()) davRows else davRows.filter { it.name.contains(query, true) }
        }
        val list = if (inZip) {
            try {
                val secret = archiveSecret.toCharArray().takeIf { it.isNotEmpty() }
                val kids = ZipArchives.listChildren(dir, zipPrefix, secret).map {
                    FilesRow(it.name, it.directory, dir, it.inner, it.size)
                }
                needPassword = false
                kids
            } catch (_: ArchivePasswordException) {
                needPassword = true
                emptyList()
            }
        } else {
            dir.listFiles()
                ?.filter { showHiddenFiles || !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory && !ZipArchives.isArchive(it) }, { it.name.lowercase() }))
                ?.map {
                    FilesRow(it.name, it.isDirectory || ZipArchives.isArchive(it), it, null, if (it.isFile) it.length() else 0)
                }
                ?: emptyList()
        }
        if (query.isBlank()) list else list.filter { it.name.contains(query, true) }
    }
    val xdg = remember { XdgUserDirsManager.resolve() }
    val shortcuts = remember(xdg) {
        listOf(
            t("pdft_files") to xdg.home,
            t("dl_title") to xdg.download,
            t("file_type_document") to xdg.documents,
            t("au_music_title") to xdg.music,
            t("hc_photos") to xdg.pictures,
            t("hc_all_videos") to xdg.videos,
            t("ermis_trash") to xdg.trash,
        )
    }
    val net = remember(tick, extraPlaces) { NetworkPlaces.places() + extraPlaces }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            shortcuts.forEach { (n, f) ->
                TextButton(onClick = {
                    zipPrefix = ""
                    archiveSecret = ""
                    davHref = null
                    if (f.exists() || f.mkdirs()) {
                        path = f
                        TelosFilesNav.path = f
                    }
                }) { Text(n, fontSize = 11.sp) }
            }
            net.forEach { place ->
                TextButton(onClick = {
                    zipPrefix = ""
                    archiveSecret = ""
                    val cloud = CloudOAuth.detect(place.uri)
                    val dav = place.uri.startsWith("dav", true) ||
                        place.uri.startsWith("http://", true) ||
                        place.uri.startsWith("https://", true)
                    if (cloud != null) {
                        davPlaceUri = place.uri
                        davUser = place.user.orEmpty()
                        cloudTrail = listOf("root")
                        davHref = place.uri
                    } else if (dav) {
                        davPlaceUri = place.uri
                        davUser = place.user.orEmpty()
                        davHref = WebDavClient.normalize(place.uri, davUser)
                    } else {
                        davHref = null
                        val local = NetworkPlaces.open(place)
                        if (local != null) {
                            path = local
                            TelosFilesNav.path = local
                        }
                    }
                    tick++
                }) { Text(place.label, fontSize = 11.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = {
                val href = davHref
                val cloudKind = CloudOAuth.detect(davPlaceUri ?: href.orEmpty())
                if (cloudKind != null) {
                    if (cloudTrail.size > 1) cloudTrail = cloudTrail.dropLast(1)
                    else {
                        davHref = null
                        davPlaceUri = null
                    }
                    tick++
                } else if (href != null) {
                    val trimmed = href.trimEnd('/')
                    val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
                    davHref = parent.takeIf { it.contains("://") && it.count { c -> c == '/' } > 2 }
                    if (davHref == null) tick++
                } else if (inZip && zipPrefix.isNotEmpty()) {
                    zipPrefix = zipPrefix.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
                } else {
                    zipPrefix = ""
                    archiveSecret = ""
                    dir.parentFile?.let { path = it; TelosFilesNav.path = it }
                }
            }) { Text(t("menu_back")) }
            if (!inZip && davHref == null) {
                TextButton(onClick = {
                    val folder = File(dir, "Folder")
                    var dest = folder
                    var i = 1
                    while (dest.exists()) dest = File(dir, "Folder $i").also { i++ }
                    dest.mkdirs()
                    tick++
                }) { Text(t("hc_new_folder")) }
                TextButton(onClick = {
                    val zip = File(dir.parentFile ?: dir, dir.name + ".zip")
                    ZipArchives.compressDir(dir, zip)
                    tick++
                }) { Text(t("hc_compress_to_zip")) }
            }
            if (inZip) {
                TextButton(onClick = {
                    val dest = File(dir.parentFile ?: dir, dir.nameWithoutExtension)
                    ZipArchives.extractAll(dir, dest, archiveSecret.toCharArray().takeIf { it.isNotEmpty() })
                    zipPrefix = ""
                    path = dest
                    TelosFilesNav.path = dest
                    tick++
                }) { Text(t("hc_extract_here")) }
            }
            val cloudKindBar = CloudOAuth.detect(davPlaceUri.orEmpty())
            if (cloudKindBar != null) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val chosen = withContext(Dispatchers.Main) {
                            java.awt.FileDialog(null as java.awt.Frame?, t("hc_add"), java.awt.FileDialog.LOAD).run {
                                isVisible = true
                                file?.let { File(directory, it) }
                            }
                        }
                        if (chosen == null || !chosen.isFile) return@launch
                        val key = davPlaceUri ?: return@launch
                        val tokens = OAuthSecrets.get(key) ?: return@launch
                        val fresh = CloudOAuth.refresh(tokens) ?: tokens
                        OAuthSecrets.put(key, fresh)
                        CloudApi.upload(cloudKindBar, fresh.access, cloudTrail.last(), chosen.name, chosen)
                        withContext(Dispatchers.Main) { tick++ }
                    }
                }) { Text(t("hc_add")) }
            }
            if (inTrash) {
                TextButton(onClick = { XdgTrash.empty(); tick++; path = trashRoot }) { Text(t("hc_delete")) }
            }
        }
        Text(
            if (inZip) "${ZipArchives.archiveRoot(dir).absolutePath} :: ${zipPrefix.ifBlank { "/" }}" else dir.absolutePath,
            color = TelosColors.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (inZip && ZipArchives.isSplitZip(dir)) {
            Text(t("files_archive_split_note"), color = TelosColors.secondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (needPassword) {
            OutlinedTextField(
                archiveSecret,
                { archiveSecret = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("hc_password")) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t("hc_filter")) }, singleLine = true)
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.zipInner ?: it.disk?.absolutePath ?: it.name }) { row ->
                val size = if (row.directory) "" else humanSize(row.size)
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            when {
                                davHref != null && CloudOAuth.detect(davPlaceUri.orEmpty()) != null && row.directory && row.zipInner != null -> {
                                    cloudTrail = cloudTrail + row.zipInner
                                    tick++
                                }
                                davHref != null && CloudOAuth.detect(davPlaceUri.orEmpty()) != null && row.zipInner != null -> {
                                    val dest = File(File(System.getProperty("java.io.tmpdir"), "telos-cloud"), row.name)
                                    val id = row.zipInner
                                    val key = davPlaceUri ?: ""
                                    val kind = CloudOAuth.detect(key) ?: return@combinedClickable
                                    scope.launch(Dispatchers.IO) {
                                        dest.delete()
                                        val tokens = OAuthSecrets.get(key) ?: return@launch
                                        val fresh = CloudOAuth.refresh(tokens) ?: tokens
                                        OAuthSecrets.put(key, fresh)
                                        val got = CloudApi.download(kind, fresh.access, id, dest)
                                        if (got != null) withContext(Dispatchers.Main) { TelosFileHandler.open(got) }
                                    }
                                }
                                davHref != null && row.directory && row.zipInner != null -> davHref = row.zipInner
                                davHref != null && row.zipInner != null -> {
                                    val cache = File(System.getProperty("java.io.tmpdir"), "telos-dav")
                                    val dest = File(cache, row.name)
                                    val href = row.zipInner
                                    val user = davUser
                                    val key = davPlaceUri ?: davHref ?: ""
                                    scope.launch(Dispatchers.IO) {
                                        dest.delete()
                                        val pw = DavSecrets.get(key) ?: ""
                                        val got = WebDavClient.download(href, user, pw, dest)
                                        if (got != null) withContext(Dispatchers.Main) { TelosFileHandler.open(got) }
                                    }
                                }
                                row.zipInner != null && row.directory -> zipPrefix = row.zipInner
                                row.zipInner != null -> {
                                    val cache = File(System.getProperty("java.io.tmpdir"), "telos-zip")
                                    val extracted = ZipArchives.extractEntry(
                                        dir,
                                        row.zipInner,
                                        cache,
                                        archiveSecret.toCharArray().takeIf { it.isNotEmpty() },
                                    )
                                    extracted?.let { TelosFileHandler.open(it) }
                                }
                                row.disk != null && (row.disk.isDirectory || ZipArchives.isArchive(row.disk)) -> {
                                    zipPrefix = ""
                                    archiveSecret = ""
                                    val next = if (ZipArchives.isArchive(row.disk)) ZipArchives.archiveRoot(row.disk) else row.disk
                                    path = next
                                    TelosFilesNav.path = next
                                }
                                inTrash && row.disk != null -> {
                                    XdgTrash.restore(row.disk)
                                    tick++
                                }
                                row.disk != null -> TelosFileHandler.open(row.disk)
                            }
                        },
                        onLongClick = {
                            val cloudKind = CloudOAuth.detect(davPlaceUri.orEmpty())
                            if (cloudKind != null && row.zipInner != null && !row.directory) {
                                val dest = File(XdgUserDirsManager.resolve().download, row.name)
                                val id = row.zipInner
                                val key = davPlaceUri ?: return@combinedClickable
                                scope.launch(Dispatchers.IO) {
                                    val tokens = OAuthSecrets.get(key) ?: return@launch
                                    val fresh = CloudOAuth.refresh(tokens) ?: tokens
                                    OAuthSecrets.put(key, fresh)
                                    CloudApi.download(cloudKind, fresh.access, id, dest)
                                }
                                return@combinedClickable
                            }
                            val f = row.disk ?: return@combinedClickable
                            if (inZip) return@combinedClickable
                            if (inTrash) {
                                f.deleteRecursively()
                                File(XdgTrash.infoDir(), f.name + ".trashinfo").delete()
                            } else {
                                XdgTrash.moveToTrash(f)
                            }
                            tick++
                        },
                    ).padding(8.dp),
                ) {
                    CandyCyberIcon(if (row.directory) CandyIcon.Folder else CandyIcon.Settings, 20.dp)
                    Text(
                        row.name + if (size.isNotEmpty()) "  $size" else "",
                        color = TelosColors.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

internal fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.1f GB", mb / 1024.0)
}
