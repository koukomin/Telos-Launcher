import re

# Fix Notes Regex Escapes
with open('app/ui/src/main/java/de/mm20/launcher2/ui/notes/NotesScreen.kt', 'r') as f:
    code = f.read()

code = code.replace('Regex("\\*\\*(.*?)\\*\\*")', 'Regex("""\\*\\*(.*?)\\*\\*""")')
code = code.replace('Regex("\\*(.*?)\\*")', 'Regex("""\\*(.*?)\\*""")')

with open('app/ui/src/main/java/de/mm20/launcher2/ui/notes/NotesScreen.kt', 'w') as f:
    f.write(code)

# Fix Music tween and animateFloat imports
with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/music/MusicScreen.kt', 'r') as f:
    code = f.read()

imports = """import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
"""

if "import androidx.compose.animation.core.tween" not in code:
    code = code.replace("import androidx.compose.animation.core.spring", "import androidx.compose.animation.core.spring\n" + imports)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/music/MusicScreen.kt', 'w') as f:
    f.write(code)

