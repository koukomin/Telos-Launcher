import os
import re

files = [
    "app/ui/src/main/java/de/mm20/launcher2/ui/media/hub/MediaHubScreen.kt",
    "app/ui/src/main/java/de/mm20/launcher2/ui/files/FilesScreen.kt",
    "app/ui/src/main/java/de/mm20/launcher2/ui/clock/ClockScreen.kt"
]

def add_haptics(content):
    if "import androidx.compose.ui.hapticfeedback.HapticFeedbackType" not in content:
        content = content.replace("import androidx.compose.ui.unit.dp\n", "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.hapticfeedback.HapticFeedbackType\nimport androidx.compose.ui.platform.LocalHapticFeedback\n")
    
    # Simple replace to inject haptics logic into sliders where possible if not present
    content = re.sub(r'onValueChange = \{ (.*?)\}', r'onValueChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); \1}', content)
    return content

for path in files:
    if os.path.exists(path):
        with open(path, 'r') as f:
            content = f.read()
        
        if "LocalHapticFeedback" not in content:
             content = add_haptics(content)
             
             # Need to ensure val haptic is defined.
             if "val haptic = LocalHapticFeedback.current" not in content:
                 content = re.sub(r'(@Composable\s*fun [^\(]+\(.*?\)\s*\{)', r'\1\n    val haptic = LocalHapticFeedback.current\n', content)
                 
             with open(path, 'w') as f:
                f.write(content)

