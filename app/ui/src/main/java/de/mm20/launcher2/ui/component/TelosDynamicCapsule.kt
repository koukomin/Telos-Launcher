package de.mm20.launcher2.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.base.R as BaseR

/**
 * High-performance Dynamic Island / Capsule component for Telos Launcher.
 * Reactive HUD displaying active audio playback, battery health, Stego vault lock state,
 * and download progress with smooth spring animations.
 */
@Composable
fun TelosDynamicCapsule(
    modifier: Modifier = Modifier,
    isPlayingMedia: Boolean = false,
    mediaTitle: String = "Telos Radio",
    mediaArtist: String = "Live Stream",
    batteryPercentage: Int? = null,
    isCharging: Boolean = false,
    isVaultLocked: Boolean = true,
    downloadProgress: Float? = null,
    onMediaPlayPause: () -> Unit = {},
    onVaultClick: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Equalizer Bar Heights animation
    val infiniteTransition = rememberInfiniteTransition(label = "eqAnim")
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse), label = "b1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "b2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "b3"
    )

    Surface(
        modifier = modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { isExpanded = !isExpanded }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Collapsed Compact View
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Audio Equalizer / Music Icon
                if (isPlayingMedia) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .height(18.dp)
                            .width(16.dp)
                    ) {
                        Box(modifier = Modifier.width(3.dp).height(bar1Height.dp).background(Color(0xFF6366F1), RoundedCornerShape(1.dp)))
                        Box(modifier = Modifier.width(3.dp).height(bar2Height.dp).background(Color(0xFFA855F7), RoundedCornerShape(1.dp)))
                        Box(modifier = Modifier.width(3.dp).height(bar3Height.dp).background(Color(0xFF06B6D4), RoundedCornerShape(1.dp)))
                    }
                } else {
                    Icon(
                        painter = painterResource(BaseR.drawable.music_note_24px),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Main Text
                Text(
                    text = if (isPlayingMedia) "$mediaTitle • $mediaArtist" else "Telos System Active",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = if (isExpanded) 240.dp else 150.dp)
                )

                // Battery / Status Badge
                if (batteryPercentage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        if (isCharging) {
                            Icon(
                                painter = painterResource(BaseR.drawable.bolt_24px),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFF10B981)
                            )
                        }
                        Text(
                            text = "$batteryPercentage%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Vault lock glyph
                Icon(
                    painter = painterResource(if (isVaultLocked) BaseR.drawable.lock_24px else BaseR.drawable.lock_open_20px),
                    contentDescription = "Vault Status",
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onVaultClick() },
                    tint = if (isVaultLocked) Color(0xFFF59E0B) else Color(0xFF10B981)
                )
            }

            // Expanded Quick Control Tray
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = mediaTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = mediaArtist,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onMediaPlayPause,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF6366F1), CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(if (isPlayingMedia) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px),
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Download indicator if active
                    if (downloadProgress != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Telos Downloader", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${(downloadProgress * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            TelosWaveProgress(
                                progress = downloadProgress,
                                color = Color(0xFF06B6D4),
                                waveAmplitude = 3.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
