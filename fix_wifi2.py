import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SavedWifiPasswordsPreference.kt', 'r') as f:
    code = f.read()

code = code.replace("UiSpacing.Margin", "16.dp")

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SavedWifiPasswordsPreference.kt', 'w') as f:
    f.write(code)

