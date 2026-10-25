import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemAppsTab.kt', 'r') as f:
    code = f.read()

# Fix flags
code = code.replace(
    'val flags = android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES',
    'val flags = android.content.pm.PackageManager.GET_META_DATA or android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES'
)
# Ensure we don't show the launcher itself or uninstalled leftovers
filter_code = """val filtered = apps.filter { app ->
                val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isVirtual = app.packageName.startsWith(context.packageName)
                if ((app.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED) == 0) return@filter false
                if (app.packageName == context.packageName) return@filter false
                (showSystem || !isSystem) && (showVirtual || !isVirtual)
            }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }"""
            
code = re.sub(
    r'val filtered = apps.filter \{ app ->.*?\.sortedBy \{ pm\.getApplicationLabel\(it\)\.toString\(\)\.lowercase\(\) \}',
    filter_code,
    code,
    flags=re.DOTALL
)

# Insert UadRepository usage
import_code = "import kotlinx.coroutines.withContext\nimport de.mm20.launcher2.ui.system.UadRepository\nimport de.mm20.launcher2.ui.system.UadInfo"
code = code.replace("import kotlinx.coroutines.withContext", import_code)

state_code = """    var installedApps by remember { mutableStateOf<List<android.content.pm.ApplicationInfo>>(emptyList()) }
    var uadList by remember { mutableStateOf<Map<String, UadInfo>>(emptyMap()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }"""
code = code.replace(
    'var installedApps by remember { mutableStateOf<List<android.content.pm.ApplicationInfo>>(emptyList()) }\n    var refreshTrigger by remember { mutableIntStateOf(0) }',
    state_code
)

effect_code = """    LaunchedEffect(showSystem, showVirtual, refreshTrigger) {
        withContext(Dispatchers.IO) {
            uadList = UadRepository.getList(context)
            val pm = context.packageManager"""
code = code.replace(
    'LaunchedEffect(showSystem, showVirtual, refreshTrigger) {\n        withContext(Dispatchers.IO) {\n            val pm = context.packageManager',
    effect_code
)

badge_code = """                                    if (isFrozen) {
                                        Text("❄ Frozen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    uadList[app.packageName]?.let { uad ->
                                        Text(" · ")
                                        val uadColor = when(uad.removal) {
                                            "Safe", "Recommended" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                            "Unsafe" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        Text("${uad.removal} to freeze", style = MaterialTheme.typography.labelSmall, color = uadColor, fontWeight = FontWeight.Bold)
                                    }
                                }"""
code = code.replace(
    """                                    if (isFrozen) {
                                        Text("❄ Frozen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }""",
    badge_code
)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemAppsTab.kt', 'w') as f:
    f.write(code)

