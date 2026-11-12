package de.mm20.launcher2.ui.keepass

import de.mm20.launcher2.i18n.R as I18nR
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.ui.component.VirtualAppWindow
import kotlinx.serialization.Serializable

@Serializable
data object KeePassDashboardRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeePassDashboardScreen() {
    val context = LocalContext.current
    val viewModel: KeePassViewModel = viewModel()

    val database = viewModel.database.value
    val currentGroup = viewModel.currentGroup.value
    val isUnlocked = viewModel.isUnlocked.value
    val unlockError = viewModel.unlockError.value

    var password by remember { mutableStateOf("") }

    val kdbxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && password.isNotEmpty()) {
            viewModel.loadFromUri(context, uri, password)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && password.isNotEmpty()) {
            viewModel.loadFromSteganography(context, uri, password)
        }
    }

    VirtualAppWindow {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isUnlocked) currentGroup?.name ?: "KeePassTL" else "KeePassTL Vault") },
                    actions = {
                        if (isUnlocked) {
                            IconButton(onClick = { viewModel.lock() }) {
                                Icon(painterResource(BaseR.drawable.lock_24px), contentDescription = stringResource(I18nR.string.keepass_lock))
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (!isUnlocked) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(BaseR.drawable.lock_24px),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(I18nR.string.keepass_db_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (unlockError) {
                            Text(stringResource(I18nR.string.keepass_wrong_password), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { kdbxLauncher.launch("*/*") }, enabled = password.isNotEmpty()) {
                                Text(stringResource(I18nR.string.keepass_open_kdbx))
                            }
                            FilledTonalButton(onClick = { imageLauncher.launch("image/png") }, enabled = password.isNotEmpty()) {
                                Text(stringResource(I18nR.string.keepass_open_stego))
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        currentGroup?.groups?.let { groups ->
                            items(items = groups, key = { it.uuid.toString() }) { group ->
                                ListItem(
                                    headlineContent = { Text(group.name) },
                                    leadingContent = { Icon(painterResource(BaseR.drawable.folder_24px), null) },
                                    modifier = Modifier.clickable { viewModel.currentGroup.value = group }
                                )
                                HorizontalDivider()
                            }
                        }
                        currentGroup?.entries?.let { entries ->
                            items(items = entries, key = { it.uuid.toString() }) { entry ->
                                val title = entry.fields.entries.firstOrNull { it.key == "Title" }?.value?.let {
                                    if (it is EntryValue.Plain) it.content else stringResource(I18nR.string.keepass_encrypted_val)
                                } ?: stringResource(I18nR.string.keepass_unnamed)
                                val username = entry.fields.entries.firstOrNull { it.key == "UserName" }?.value?.let {
                                    if (it is EntryValue.Plain) it.content else "***"
                                } ?: ""

                                ListItem(
                                    headlineContent = { Text(title) },
                                    supportingContent = { if (username.isNotEmpty()) Text(username) },
                                    leadingContent = { Icon(painterResource(BaseR.drawable.lock_24px), null) },
                                    modifier = Modifier.clickable { /* TODO Open Entry */ }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
