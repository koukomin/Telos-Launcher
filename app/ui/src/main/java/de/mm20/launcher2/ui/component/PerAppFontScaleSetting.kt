package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.preferences.ui.AppFontSettings
import kotlin.math.roundToInt

@Composable
fun PerAppFontScaleSetting(
    appKey: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fontSettings = remember(context) { AppFontSettings(context) }
    var fontScale by remember(appKey) { mutableFloatStateOf(fontSettings.getFontScale(appKey)) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Per-App Font Size / Μέγεθος Γραμματοσειράς",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scale: ${(fontScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (fontScale != 1.0f) {
                    TextButton(onClick = {
                        fontScale = 1.0f
                        fontSettings.setFontScale(appKey, 1.0f)
                    }) {
                        Text("Reset")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Slider(
                value = fontScale,
                onValueChange = {
                    fontScale = (it * 100).roundToInt() / 100f
                    fontSettings.setFontScale(appKey, fontScale)
                },
                valueRange = 0.75f..1.50f,
                steps = 14,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Live Text Preview Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aα - Live Text Preview / Ζωντανή Προεπισκόπηση",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Telos App Typography Scale Sample Text",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
