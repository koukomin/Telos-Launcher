import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/calendar/CalendarScreen.kt', 'r') as f:
    code = f.read()

# Add font settings to CalendarScreen
calendar_old = r"""                IconButton\(onClick = \{ searching = !searching; if \(!searching\) calQuery = "" \}\) \{
                    Icon\(painterResource\(if \(searching\) R\.drawable\.close_24px else R\.drawable\.search_24px\), stringResource\(R\.string\.ts_search\)\)
                \}
                Box \{
                    IconButton\(onClick = \{ menu = true \}\) \{ Icon\(painterResource\(R\.drawable\.more_vert_24px\), null\) \}"""

calendar_new = """                var showFontDialog by remember { mutableStateOf(false) }
                if (showFontDialog) {
                    de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                        appKey = "calendar",
                        appName = "Telos Calendar",
                        onDismiss = { showFontDialog = false }
                    )
                }
                IconButton(onClick = { searching = !searching; if (!searching) calQuery = "" }) {
                    Icon(painterResource(if (searching) R.drawable.close_24px else R.drawable.search_24px), stringResource(R.string.ts_search))
                }
                IconButton(onClick = { showFontDialog = true }) {
                    Icon(painterResource(R.drawable.settings_24px), contentDescription = "Font Settings")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.more_vert_24px), null) }"""

code = re.sub(calendar_old, calendar_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/calendar/CalendarScreen.kt', 'w') as f:
    f.write(code)

