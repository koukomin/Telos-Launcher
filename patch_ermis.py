import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/ermis/ErmisScreen.kt', 'r') as f:
    code = f.read()

# Replace the Scaffold setup with M3 Expressive / Telos TopAppBar + settings
scaffold_old = r"""    Scaffold\(containerColor = androidx\.compose\.ui\.graphics\.Color\.Transparent,
        topBar = \{ Row\(Modifier\.statusBarsPadding\(\)\.fillMaxWidth\(\)\.padding\(horizontal = 8\.dp, vertical = 8\.dp\), verticalAlignment = Alignment\.CenterVertically\) \{
            IconButton\(onClick = \{ backStack\.removeLastOrNull\(\) \}\) \{ Icon\(painterResource\(R\.drawable\.arrow_back_24px\), stringResource\(R\.string\.hc_close\)\) \}
            OutlinedTextField\(query, \{ query = it; serverResults = null \}, placeholder = \{ Text\(stringResource\(R\.string\.ermis_search\)\) \},
                modifier = Modifier\.weight\(1f\), singleLine = true, shape = MaterialTheme\.shapes\.extraLarge\)
            IconButton\(onClick = \{ accounts = true \}\) \{ Icon\(painterResource\(R\.drawable\.person_24px\), stringResource\(R\.string\.ermis_accounts\)\) \}
        \} \},"""

scaffold_new = """    var showFontDialog by remember { mutableStateOf(false) }
    if (showFontDialog) {
        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
            appKey = "ermis",
            appName = "Telos Mail & Chat",
            onDismiss = { showFontDialog = false }
        )
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 2.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.hc_close))
                    }
                    OutlinedTextField(
                        query, { query = it; serverResults = null },
                        placeholder = { Text(stringResource(R.string.ermis_search)) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    IconButton(onClick = { showFontDialog = true }) {
                        Icon(painterResource(R.drawable.settings_24px), contentDescription = "Font Settings")
                    }
                    IconButton(onClick = { accounts = true }) {
                        Icon(painterResource(R.drawable.person_24px), stringResource(R.string.ermis_accounts))
                    }
                }
            }
        },"""

code = re.sub(scaffold_old, scaffold_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/ermis/ErmisScreen.kt', 'w') as f:
    f.write(code)

