import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/voice/VoiceRecorderScreen.kt', 'r') as f:
    code = f.read()

# Add font settings to VoiceRecorderScreen
voice_old = r"""                        Spacer\(Modifier\.weight\(1f\)\)
                        IconButton\(onClick = \{ searching = true \}\) \{
                            Icon\(painterResource\(R\.drawable\.search_24px\), contentDescription = stringResource\(R\.string\.voice_search\)\)
                        \}
                        Box \{"""

voice_new = """                        var showFontDialog by remember { mutableStateOf(false) }
                        if (showFontDialog) {
                            de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                                appKey = "voice",
                                appName = "Telos Voice Recorder",
                                onDismiss = { showFontDialog = false }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { searching = true }) {
                            Icon(painterResource(R.drawable.search_24px), contentDescription = stringResource(R.string.voice_search))
                        }
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(painterResource(R.drawable.settings_24px), contentDescription = "Font Settings")
                        }
                        Box {"""

code = re.sub(voice_old, voice_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/voice/VoiceRecorderScreen.kt', 'w') as f:
    f.write(code)

