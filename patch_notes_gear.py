import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/notes/NotesScreen.kt', 'r') as f:
    code = f.read()

# Add font settings to NotesScreen
notes_old = r"""                    IconButton\(onClick = \{ searching = !searching; if \(!searching\) vm\.query\.value = "" \}\) \{
                        Icon\(painterResource\(if \(searching\) R\.drawable\.close_24px else R\.drawable\.search_24px\), stringResource\(R\.string\.notes_search\)\)
                    \}
                    if \(syncing\) CircularProgressIndicator\(Modifier\.size\(24\.dp\)\) else Box \{
                        IconButton\(onClick = \{ menu = true \}\) \{ Icon\(painterResource\(R\.drawable\.more_vert_24px\), stringResource\(R\.string\.notes_title\)\) \}"""

notes_new = """                    var showFontDialog by remember { mutableStateOf(false) }
                    if (showFontDialog) {
                        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                            appKey = "notes",
                            appName = "Telos Notes",
                            onDismiss = { showFontDialog = false }
                        )
                    }
                    IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) {
                        Icon(painterResource(if (searching) R.drawable.close_24px else R.drawable.search_24px), stringResource(R.string.notes_search))
                    }
                    IconButton(onClick = { showFontDialog = true }) {
                        Icon(painterResource(R.drawable.settings_24px), contentDescription = "Font Settings")
                    }
                    if (syncing) CircularProgressIndicator(Modifier.size(24.dp)) else Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.notes_title)) }"""

code = re.sub(notes_old, notes_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/notes/NotesScreen.kt', 'w') as f:
    f.write(code)

