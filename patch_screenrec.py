import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/screenrec/ScreenRecorderScreen.kt', 'r') as f:
    code = f.read()

# Add font settings to ScreenRecorderScreen
rec_old = r"""            Row\(Modifier\.fillMaxWidth\(\)\.padding\(horizontal = 8\.dp, vertical = 8\.dp\), verticalAlignment = Alignment\.CenterVertically\) \{
                Spacer\(Modifier\.weight\(1f\)\)
                IconButton\(onClick = \{ backStack\.add\(ScreenRecorderSettingsRoute\) \}\) \{
                    Icon\(painterResource\(R\.drawable\.settings_24px\), contentDescription = stringResource\(R\.string\.voice_settings\)\)
                \}
            \}"""

rec_new = """            var showFontDialog by remember { mutableStateOf(false) }
            if (showFontDialog) {
                de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                    appKey = "screenrec",
                    appName = "Telos Screen Recorder",
                    onDismiss = { showFontDialog = false }
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFontDialog = true }) {
                    Icon(painterResource(R.drawable.text_fields_24px), contentDescription = "Font Settings")
                }
                IconButton(onClick = { backStack.add(ScreenRecorderSettingsRoute) }) {
                    Icon(painterResource(R.drawable.settings_24px), contentDescription = stringResource(R.string.voice_settings))
                }
            }"""

code = re.sub(rec_old, rec_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/screenrec/ScreenRecorderScreen.kt', 'w') as f:
    f.write(code)

