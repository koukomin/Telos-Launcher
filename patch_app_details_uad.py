import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/store/AppDetailsScreen.kt', 'r') as f:
    code = f.read()

import_code = "import kotlinx.coroutines.launch\nimport de.mm20.launcher2.ui.system.UadRepository"
code = code.replace("import kotlinx.coroutines.launch", import_code)

uad_card = """                var uadInfo by remember { mutableStateOf<de.mm20.launcher2.ui.system.UadInfo?>(null) }
                LaunchedEffect(item.packageName) {
                    val map = UadRepository.getList(context)
                    uadInfo = map[item.packageName]
                }
                uadInfo?.let { uad ->
                    val uadColor = when(uad.removal) {
                        "Safe", "Recommended" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        "Unsafe" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Card("Universal Android Debloater (UAD)") {
                        Text("${uad.removal} to freeze/remove", style = MaterialTheme.typography.titleSmall, color = uadColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        Text(uad.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                var inureData"""

code = code.replace("var inureData", uad_card)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/store/AppDetailsScreen.kt', 'w') as f:
    f.write(code)

