import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/downloads/DownloadsScreen.kt', 'r') as f:
    code = f.read()

# Add font settings to DownloadsScreen
downloader_old = r"""        actions = \{
            IconButton\(onClick = \{ searching = !searching; if \(!searching\) query = "" \}\) \{
                Icon\(painterResource\(R\.drawable\.search_24px\), stringResource\(R\.string\.dl_search\)\)
            \}
            IconButton\(onClick = \{ menu = true \}\) \{
                Icon\(painterResource\(R\.drawable\.more_vert_24px\), stringResource\(R\.string\.action_more_actions\)\)
            \}"""

downloader_new = """        actions = {
            var showFontDialog by remember { mutableStateOf(false) }
            if (showFontDialog) {
                de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                    appKey = "downloader",
                    appName = "Telos Downloader",
                    onDismiss = { showFontDialog = false }
                )
            }
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(painterResource(R.drawable.search_24px), stringResource(R.string.dl_search))
            }
            IconButton(onClick = { showFontDialog = true }) {
                Icon(painterResource(R.drawable.settings_24px), contentDescription = "Font Settings")
            }
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.action_more_actions))
            }"""

code = re.sub(downloader_old, downloader_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/downloads/DownloadsScreen.kt', 'w') as f:
    f.write(code)

