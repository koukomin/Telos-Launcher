package de.mm20.launcher2.ui.settings.webapps

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.IconPicker
import de.mm20.launcher2.ui.component.BottomSheet
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.withPrivateKeyboard
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.webappshortcuts.CustomTabsBrowsers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class IconPickerTab { IconPack, Photo, Favicon }

@Composable
fun EditWebAppShortcutSheet(
    expanded: Boolean,
    existing: WebAppShortcut?,
    onSave: (label: String, url: String, iconUri: String?, faviconUrl: String?, rendererPackage: String?, showInGrid: Boolean, showInPanel: Boolean, iconSource: WebAppShortcut.IconSource, customCss: String?, notificationsEnabled: Boolean, groupId: String?) -> Unit,
    onDismiss: () -> Unit,
    onImportIcon: suspend (uri: Uri, sizePx: Int) -> String?,
    onFindFavicon: suspend (url: String) -> String?,
    onExportIconPackIcon: suspend (customIcon: CustomIcon?, sizePx: Int) -> String?,
) {
    val browsingSettings: WebAppBrowsingSettings = koinInject()
    val groupsEnabled by browsingSettings.groupsEnabled.collectAsState(false)
    val groups by browsingSettings.groups.collectAsState(emptyList())

    BottomSheet(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
        // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
        var url by remember(existing) { mutableStateOf(existing?.url ?: "https://") }
        // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
        var iconUri by remember(existing) { mutableStateOf(existing?.iconUri) }
        var faviconUrl by remember(existing) { mutableStateOf(existing?.faviconUrl) }
        var rendererPackage by remember(existing) { mutableStateOf(existing?.rendererPackage) }
        var showInGrid by remember(existing) { mutableStateOf(existing?.showInGrid ?: true) }
        var showInPanel by remember(existing) { mutableStateOf(existing?.showInPanel ?: false) }
        var iconSource by remember(existing) { mutableStateOf(existing?.iconSource ?: WebAppShortcut.IconSource.Website) }
        var customCss by remember(existing) { mutableStateOf(existing?.customCss ?: "") }
        var notificationsEnabled by remember(existing) { mutableStateOf(existing?.notificationsEnabled ?: false) }
        var groupId by remember(existing, groups) { 
            mutableStateOf(existing?.let { e -> groups.find { it.appKeys.contains(e.key) }?.id }) 
        }
        var findingFavicon by remember { mutableStateOf(false) }
        var showRendererMenu by remember { mutableStateOf(false) }
        var showIconSourceMenu by remember { mutableStateOf(false) }
        var showGroupMenu by remember { mutableStateOf(false) }
        var showIconPicker by remember { mutableStateOf(false) }
        var iconPickerTab by remember { mutableStateOf(IconPickerTab.IconPack) }

        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val iconSizePx = 48.dp.toPixels().toInt()
        val supportedBrowsers = remember { CustomTabsBrowsers.findSupportedBrowsers(context) }
        val searchUiSettings: SearchUiSettings = koinInject()
        val privateKeyboard by remember { searchUiSettings.privateKeyboard }.collectAsState(false)

        val pickIconLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    scope.launch {
                        val path = onImportIcon(uri, iconSizePx)
                        if (path != null) {
                            iconUri = path
                            iconSource = WebAppShortcut.IconSource.Custom
                        }
                    }
                }
            }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(
                    if (existing == null) R.string.web_app_shortcut_create
                    else R.string.web_app_shortcut_edit
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val iconModel = iconUri ?: faviconUrl
                if (iconModel != null) {
                    AsyncImage(
                        model = iconModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
                OutlinedButton(onClick = { showIconPicker = true }) {
                    Text(stringResource(R.string.web_app_shortcut_pick_icon))
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.web_app_shortcut_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    .withPrivateKeyboard(privateKeyboard),
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                value = url,
                onValueChange = { url = it; findingFavicon = false },
                label = { Text(stringResource(R.string.web_app_shortcut_url)) },
                singleLine = true,
                // Explicitly Uri, never routed through withPrivateKeyboard: that helper's only way
                // to get IME_FLAG_NO_PERSONALIZED_LEARNING set is KeyboardType.Password, which is
                // exactly what was making Android's autofill/password-manager treat this field as
                // a credential to save on every edit. A URL isn't a credential.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
                    capitalization = KeyboardCapitalization.None,
                    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
                    autoCorrectEnabled = false,
                ),
                trailingIcon = if (findingFavicon) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                } else null,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.web_app_shortcut_icon_source),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showIconSourceMenu = true }) {
                    Text(
                        when (iconSource) {
                            WebAppShortcut.IconSource.Website -> stringResource(R.string.web_app_shortcut_icon_source_website)
                            WebAppShortcut.IconSource.System -> stringResource(R.string.web_app_shortcut_icon_source_system)
                            WebAppShortcut.IconSource.Custom -> stringResource(R.string.web_app_shortcut_icon_source_custom)
                        }
                    )
                }
                DropdownMenuPopup(
                    expanded = showIconSourceMenu,
                    onDismissRequest = { showIconSourceMenu = false },
                ) {
                    // DropdownMenuGroup wraps its content in an opaque Surface - a bare
                    // DropdownMenuPopup has no background of its own, so this menu rendered
                    // see-through onto whatever was behind the sheet.
                    DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                        WebAppShortcut.IconSource.entries.forEach { source ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (source) {
                                            WebAppShortcut.IconSource.Website -> stringResource(R.string.web_app_shortcut_icon_source_website)
                                            WebAppShortcut.IconSource.System -> stringResource(R.string.web_app_shortcut_icon_source_system)
                                            WebAppShortcut.IconSource.Custom -> stringResource(R.string.web_app_shortcut_icon_source_custom)
                                        }
                                    )
                                },
                                onClick = {
                                    iconSource = source
                                    showIconSourceMenu = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_filter_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Checkbox(checked = showInGrid, onCheckedChange = { showInGrid = it })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.preference_screen_dock),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Checkbox(checked = showInPanel, onCheckedChange = { showInPanel = it })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.web_app_shortcut_renderer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showRendererMenu = true }) {
                    Text(
                        rendererPackage?.let { appLabel(context, it) }
                            ?: stringResource(R.string.web_app_shortcut_renderer_embedded)
                    )
                }
                DropdownMenuPopup(
                    expanded = showRendererMenu,
                    onDismissRequest = { showRendererMenu = false },
                ) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.web_app_shortcut_renderer_embedded)) },
                            onClick = {
                                rendererPackage = null
                                showRendererMenu = false
                            },
                        )
                        if (supportedBrowsers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_app_shortcut_renderer_none_detected)) },
                                enabled = false,
                                onClick = {},
                            )
                        }
                        for (pkg in supportedBrowsers) {
                            DropdownMenuItem(
                                text = { Text(appLabel(context, pkg)) },
                                onClick = {
                                    rendererPackage = pkg
                                    showRendererMenu = false
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                value = customCss,
                onValueChange = { customCss = it },
                label = { Text(stringResource(R.string.web_app_shortcut_custom_css)) },
                supportingText = { Text(stringResource(R.string.web_app_shortcut_custom_css_summary)) },
                minLines = 3,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.web_app_shortcut_enable_notifications),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Checkbox(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            }

            if (groupsEnabled && groups.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Group",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showGroupMenu = true }) {
                        Text(
                            groupId?.let { id -> groups.find { it.id == id }?.name }
                                ?: "None"
                        )
                    }
                    DropdownMenuPopup(
                        expanded = showGroupMenu,
                        onDismissRequest = { showGroupMenu = false },
                    ) {
                        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    groupId = null
                                    showGroupMenu = false
                                },
                            )
                            groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        groupId = group.id
                                        showGroupMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    enabled = label.isNotBlank() && url.isNotBlank(),
                    onClick = {
                        onSave(
                            label.trim(), url.trim(), iconUri, faviconUrl, rendererPackage,
                            showInGrid, showInPanel, iconSource, customCss.trim().ifBlank { null },
                            notificationsEnabled, groupId
                        )
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }

        DismissableBottomSheet(
            expanded = showIconPicker,
            onDismissRequest = { showIconPicker = false },
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                TabRow(selectedTabIndex = iconPickerTab.ordinal) {
                    Tab(
                        selected = iconPickerTab == IconPickerTab.IconPack,
                        onClick = { iconPickerTab = IconPickerTab.IconPack },
                        text = { Text(stringResource(R.string.web_app_icon_picker_tab_icon_pack)) },
                    )
                    Tab(
                        selected = iconPickerTab == IconPickerTab.Photo,
                        onClick = { iconPickerTab = IconPickerTab.Photo },
                        text = { Text(stringResource(R.string.web_app_icon_picker_tab_photo)) },
                    )
                    Tab(
                        selected = iconPickerTab == IconPickerTab.Favicon,
                        onClick = { iconPickerTab = IconPickerTab.Favicon },
                        text = { Text(stringResource(R.string.web_app_icon_picker_tab_favicon)) },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                ) {
                    when (iconPickerTab) {
                        IconPickerTab.IconPack -> {
                            IconPicker(
                                searchable = WebAppIconPickerTarget,
                                onSelect = { customIcon ->
                                    scope.launch {
                                        val path = onExportIconPackIcon(customIcon, iconSizePx)
                                        if (path != null) {
                                            iconUri = path
                                            iconSource = WebAppShortcut.IconSource.Custom
                                        }
                                        showIconPicker = false
                                    }
                                },
                            )
                        }

                        IconPickerTab.Photo -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                OutlinedButton(onClick = {
                                    pickIconLauncher.launch("image/*")
                                    showIconPicker = false
                                }) {
                                    Text(stringResource(R.string.web_app_shortcut_pick_icon))
                                }
                            }
                        }

                        IconPickerTab.Favicon -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (findingFavicon) {
                                    CircularProgressIndicator()
                                } else {
                                    TextButton(
                                        enabled = url.isNotBlank(),
                                        onClick = {
                                            findingFavicon = true
                                            scope.launch {
                                                val favicon = onFindFavicon(url)
                                                findingFavicon = false
                                                if (favicon != null) {
                                                    faviconUrl = favicon
                                                    iconSource = WebAppShortcut.IconSource.Website
                                                }
                                                showIconPicker = false
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.web_app_shortcut_detect_icon))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun appLabel(context: Context, packageName: String): String {
    return try {
        context.packageManager.getApplicationInfo(packageName, 0)
            .loadLabel(context.packageManager)
            .toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
