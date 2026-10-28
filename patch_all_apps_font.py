import os

apps = [
    ("app/ui/src/main/java/de/mm20/launcher2/ui/calculator/CalculatorScreen.kt", "calculator", "Telos Calculator"),
    ("app/ui/src/main/java/de/mm20/launcher2/ui/calendar/CalendarScreen.kt", "calendar", "Telos Calendar"),
    ("app/ui/src/main/java/de/mm20/launcher2/ui/downloads/DownloadsScreen.kt", "downloader", "Telos Downloader"),
    ("app/ui/src/main/java/de/mm20/launcher2/ui/notes/NotesScreen.kt", "notes", "Telos Notes"),
    ("app/ui/src/main/java/de/mm20/launcher2/ui/screenrec/ScreenRecorderScreen.kt", "screenrec", "Screen Recorder"),
    ("app/ui/src/main/java/de/mm20/launcher2/ui/voice/VoiceRecorderScreen.kt", "voice", "Voice Recorder"),
    ("app/ui/src/main/java/de/mm20/launcher2/ui/ermis/ErmisScreen.kt", "ermis", "Telos Mail & Chat")
]

for path, key, name in apps:
    if not os.path.exists(path):
        print(f"Skipping {path}, not found.")
        continue
    with open(path, 'r') as f:
        content = f.read()
    
    # Check if already patched
    if key in content and "PerAppFontScaleDialog" in content:
        print(f"Already patched {path}")
        continue

    # Find TopAppBar or similar header row and add settings icon
    # We can inject a state and dialog at the top of the main @Composable function
    print(f"Patching {path}...")

