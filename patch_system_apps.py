import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemAppsTab.kt', 'r') as f:
    code = f.read()

# Add Icon imports
imports = """import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.AnimatedVisibility
"""
code = code.replace("import androidx.compose.foundation.clickable\n", "import androidx.compose.foundation.clickable\n" + imports)

# Remove old AppOpsNextButton placement and search bar
old_search_and_filters = r"""        de\.mm20\.launcher2\.ui\.component\.TelosSearchBar\(query, \{ query = it \}, stringResource\(R\.string\.hf_store_search\)\)
        FlowRow\(Modifier\.padding\(horizontal = 16\.dp\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\) \{
            FilterChip\(frozenOnly, \{ frozenOnly = !frozenOnly \}, \{ Text\(stringResource\(R\.string\.hf_freeze_frozen_apps\)\) \}\)
            FilterChip\(showSystem, \{ showSystem = !showSystem \}, \{ Text\(stringResource\(R\.string\.preference_freeze_show_system_apps\)\) \}\)
        \}
        FlowRow\(Modifier\.padding\(horizontal = 16\.dp\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\) \{
            TextButton\(onClick = \{ backStack\.add\(de\.mm20\.launcher2\.ui\.settings\.freeze\.FreezeSettingsRoute\) \}\) \{
                Text\(stringResource\(R\.string\.preference_screen_freeze\)\)
            \}
            AppOpsNextButton\(\)
        \}"""

new_search_and_filters = """        // Search and Toolbar - Google Apps 2026 Style
        var searchExpanded by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (searchExpanded) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).height(56.dp),
                        placeholder = { Text(stringResource(R.string.hc_search)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        trailingIcon = {
                            IconButton(onClick = { query = ""; searchExpanded = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.hc_apps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { searchExpanded = true }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.hc_search))
                    }
                    IconButton(onClick = { showSystem = !showSystem }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(if (showSystem) R.drawable.visibility_24px else R.drawable.visibility_off_24px),
                            contentDescription = "System Apps"
                        )
                    }
                    // Global AppOps Next Button (Only in top bar)
                    AppOpsNextButton()
                }
            }
        }"""
code = re.sub(old_search_and_filters, new_search_and_filters, code)


# Clean up individual app cards (remove text buttons for freeze and AppOps, replace with icon buttons)
old_card_content = r"""                        AppOpsNextButton\(app\.pkg\)
                        uad\[app\.pkg\]\?\.let \{ info ->
                            Text\(stringResource\(R\.string\.freeze_hub_uad_help\), style = MaterialTheme\.typography\.labelSmall\)
                            Text\(info\.recommendation, style = MaterialTheme\.typography\.labelMedium\)
                            Text\(info\.description, maxLines = 4, overflow = androidx\.compose\.ui\.text\.style\.TextOverflow\.Ellipsis, style = MaterialTheme\.typography\.bodySmall\)
                        \}
                        OutlinedButton\(enabled = !busy, onClick = \{
                            busy = true
                            scope\.launch \{
                                try \{
                                    val ok = withContext\(Dispatchers\.IO\) \{ if \(app\.frozen\) freeze\.unfreeze\(app\.pkg\) else freeze\.freeze\(app\.pkg\) \}
                                    outcome = context\.getString\(R\.string\.freeze_hub_result, if \(app\.pkg in ok\) 1 else 0, 1\)
                                    revision\+\+
                                \} finally \{ busy = false \}
                            \}
                        \}\) \{ Text\(stringResource\(if \(app\.frozen\) R\.string\.freeze_hub_unfreeze else R\.string\.freeze_widget_freeze_now\)\) \}
                        OutlinedButton\(onClick = \{
                            backStack\.add\(de\.mm20\.launcher2\.ui\.network\.firewall\.NetworkPackageFirewallRoute\(app\.pkg\)\)
                        \}\) \{ Text\(stringResource\(R\.string\.netfw_title\)\) \}"""

new_card_content = """                        uad[app.pkg]?.let { info ->
                            Spacer(Modifier.height(8.dp))
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(stringResource(R.string.freeze_hub_uad_help), style = MaterialTheme.typography.labelSmall)
                                    Text(info.recommendation, style = MaterialTheme.typography.labelMedium)
                                    Text(info.description, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    backStack.add(de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute(app.pkg))
                                }
                            ) {
                                Icon(androidx.compose.ui.res.painterResource(R.drawable.security_24px), contentDescription = "Firewall")
                            }
                            IconButton(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val ok = withContext(Dispatchers.IO) { if (app.frozen) freeze.unfreeze(app.pkg) else freeze.freeze(app.pkg) }
                                            outcome = context.getString(R.string.freeze_hub_result, if (app.pkg in ok) 1 else 0, 1)
                                            revision++
                                        } finally { busy = false }
                                    }
                                },
                                enabled = !busy
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(if (app.frozen) R.drawable.play_arrow_24px else R.drawable.ac_unit_24px),
                                    contentDescription = "Freeze/Unfreeze",
                                    tint = if (app.frozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }"""

code = re.sub(old_card_content, new_card_content, code)

# Make app cards more expressive
old_surface = r"""                Surface\(Modifier\.fillMaxWidth\(\)\.padding\(horizontal = 16\.dp, vertical = 4\.dp\), shape = MaterialTheme\.shapes\.medium,
                    color = MaterialTheme\.colorScheme\.surfaceContainerLow\) \{"""
new_surface = """                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), shadowElevation = 4.dp, tonalElevation = 2.dp) {"""
code = code.replace(old_surface, new_surface)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemAppsTab.kt', 'w') as f:
    f.write(code)

