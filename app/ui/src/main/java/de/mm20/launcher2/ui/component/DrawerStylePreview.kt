package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.preferences.DrawerStyle

@Composable
fun DrawerStylePreview(
    style: DrawerStyle,
    modifier: Modifier = Modifier,
) {
    var triggerAnim by remember(style) { mutableStateOf(0) }
    val progress = remember(style, triggerAnim) { Animatable(0f) }

    LaunchedEffect(style, triggerAnim) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
        )
    }

    val neonCyan = Color(0xFF00F0FF)
    val neonMagenta = Color(0xFFFF007F)
    val neonGreen = Color(0xFF00FF66)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(BaseR.drawable.apps_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(I18nR.string.drawer_style_live_preview),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable {
                        triggerAnim++
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▶  Replay Demo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mini Phone Frame Simulation
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Background Wallpaper Glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF1F2937),
                                    Color(0xFF0D1117)
                                )
                            )
                        )
                )

                // Render specific style live preview inside the phone frame
                when (style) {
                    DrawerStyle.Classic -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp * progress.value)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(12.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(32.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("App Drawer", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                PreviewGridItems(progress = progress.value, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    DrawerStyle.CyberMatrix -> {
                        val infiniteTransition = rememberInfiniteTransition(label = "scanline")
                        val scanPos by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                            label = "scanline"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp * progress.value)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(Color(0xFF050D1A))
                                .border(1.dp, neonCyan.copy(alpha = 0.5f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .padding(12.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, neonCyan.copy(alpha = 0.15f), Color.Transparent),
                                        startY = size.height * scanPos - 20f,
                                        endY = size.height * scanPos + 20f
                                    )
                                )
                            }
                            Column {
                                TelosDotMatrixText("MATRIX // ONLINE", fontSize = 12.sp, color = neonCyan)
                                Spacer(modifier = Modifier.height(8.dp))
                                PreviewGridItems(progress = progress.value, tint = neonCyan)
                            }
                        }
                    }

                    DrawerStyle.HoloPortal -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp * progress.value)
                                .graphicsLayer {
                                    scaleX = 0.8f + (progress.value * 0.2f)
                                    scaleY = progress.value
                                    alpha = progress.value
                                }
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            neonMagenta.copy(alpha = 0.25f),
                                            Color(0xFF10002B)
                                        )
                                    )
                                )
                                .border(1.dp, neonMagenta.copy(alpha = 0.6f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, neonCyan, CircleShape)
                                        .background(neonMagenta.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚡", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                TelosDotMatrixText("HOLO-PORTAL ACTIVE", fontSize = 11.sp, color = neonMagenta)
                                Spacer(modifier = Modifier.height(8.dp))
                                PreviewGridItems(progress = progress.value, tint = neonMagenta)
                            }
                        }
                    }

                    DrawerStyle.CommandDeck -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp * progress.value)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(Color(0xFF090C10))
                                .border(1.dp, neonGreen.copy(alpha = 0.5f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TelosDotMatrixText("TAC-HUD v2.4", fontSize = 11.sp, color = neonGreen)
                                    Text("CPU: 18%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = neonGreen.copy(alpha = 0.8f))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(neonGreen.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PreviewGridItems(progress = progress.value, tint = neonGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewGridItems(progress: Float, tint: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(3) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                repeat(4) { col ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .scale(progress)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint.copy(alpha = 0.2f))
                            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(tint.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }
    }
}
