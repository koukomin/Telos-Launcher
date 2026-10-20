package de.mm20.launcher2.ui.files

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledAppItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val apkSize: Long,
    val icon: Drawable?,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesAppManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = packages.map { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString()
                val pkgInfo = runCatching { pm.getPackageInfo(appInfo.packageName, 0) }.getOrNull()
                val apkFile = File(appInfo.sourceDir)
                val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                InstalledAppItem(
                    label = label,
                    packageName = appInfo.packageName,
                    versionName = pkgInfo?.versionName ?: "1.0",
                    apkPath = appInfo.sourceDir,
                    apkSize = if (apkFile.exists()) apkFile.length() else 0L,
                    icon = icon,
                    isSystem = isSys
                )
            }.sortedBy { it.label }

            withContext(Dispatchers.Main) {
                installedApps = list
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    fun extractApk(app: InstalledAppItem) {
        scope.launch(Dispatchers.IO) {
            try {
                val source = File(app.apkPath)
                if (!source.exists()) return@launch

                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Telos/APKs")
                targetDir.mkdirs()

                val targetFile = File(targetDir, "${app.label}_${app.versionName}.apk")
                source.copyTo(targetFile, overwrite = true)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Το APK αποθηκεύτηκε στο Downloads/Telos/APKs", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Σφάλμα εξαγωγής: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.apps_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Διαχειριστής Εφαρμογών & Εξαγωγή APK", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Αναζήτηση Εφαρμογής") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps) { app ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val iconBmp = remember(app.icon) { app.icon?.toBitmap(80, 80) }
                                    if (iconBmp != null) {
                                        Image(
                                            bitmap = iconBmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.apps_24px),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(
                                            text = "${app.packageName} • ${Formatter.formatFileSize(context, app.apkSize)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    IconButton(onClick = { extractApk(app) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.download_24px),
                                            contentDescription = "Extract APK",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Κλείσιμο")
            }
        }
    )
}
