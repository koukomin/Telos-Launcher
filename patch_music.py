import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/music/MusicScreen.kt', 'r') as f:
    code = f.read()

mesh_old = """    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))
            .background(Color.Black.copy(alpha = 0.25f)),
    ) {"""

mesh_new = """    // Adaptive Dynamic Mesh Gradient (Contextual Dynamic Theming from Album Art)
    val animatedTop by animateColorAsState(top, animationSpec = tween(1500), label = "topColor")
    val animatedBottom by animateColorAsState(bottom, animationSpec = tween(1500), label = "bottomColor")
    val animatedSoft by animateColorAsState(tint.copy(alpha = 0.5f), animationSpec = tween(2000), label = "softColor")
    
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "meshGlow")
    val glowOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(8000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "glow1"
    )
    
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(animatedTop, animatedBottom)))
            .drawBehind {
                val r = size.minDimension * 0.8f
                // Glow 1 - Top Left rotating
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(animatedSoft, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.2f * glowOffset1, size.height * 0.3f),
                        radius = r
                    ),
                    radius = r
                )
                // Glow 2 - Bottom Right rotating opposite
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(animatedSoft.copy(alpha = 0.3f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * (0.8f + (1-glowOffset1)*0.2f), size.height * 0.7f),
                        radius = r * 1.2f
                    ),
                    radius = r * 1.2f
                )
            }
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {"""

code = code.replace(mesh_old, mesh_new)

if "import androidx.compose.ui.draw.drawBehind" not in code:
    code = code.replace("import androidx.compose.ui.draw.clip", "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.drawBehind")

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/music/MusicScreen.kt', 'w') as f:
    f.write(code)

