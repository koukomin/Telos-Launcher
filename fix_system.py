import re

# Fix conflicting currentSsid in SystemDashboardScreen
with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemDashboardScreen.kt', 'r') as f:
    code = f.read()

code = code.replace("private fun currentSsid(context: Context): String? {", "private fun currentSsidSystemDash(context: Context): String? {")
code = code.replace("val connectedSsid = currentSsid(context)", "val connectedSsid = currentSsidSystemDash(context)")

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemDashboardScreen.kt', 'w') as f:
    f.write(code)


# Fix Unresolved 'Margin' in SavedWifiPasswordsPreference.kt
with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SavedWifiPasswordsPreference.kt', 'r') as f:
    code = f.read()

code = code.replace("style = Margin,", "style = MaterialTheme.typography.bodyMedium,")

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SavedWifiPasswordsPreference.kt', 'w') as f:
    f.write(code)

