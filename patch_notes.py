import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/notes/NotesScreen.kt', 'r') as f:
    code = f.read()

markdown_imports = """import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
"""
code = code.replace("import androidx.compose.ui.text.font.FontWeight\n", "import androidx.compose.ui.text.font.FontWeight\n" + markdown_imports)

# We want to use Markdown formatting in NoteCard display, so we create a function parseMarkdown
markdown_func = """
private val mdBold = Regex("\\*\\*(.*?)\\*\\*")
private val mdItalic = Regex("\\*(.*?)\\*")
private val mdStrike = Regex("~~(.*?)~~")

@Composable
private fun renderMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(text)
        mdBold.findAll(text).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }
        mdItalic.findAll(text).forEach { match ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
        }
        mdStrike.findAll(text).forEach { match ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), match.range.first, match.range.last + 1)
        }
    }
}
"""

# Insert function before NoteCard
code = code.replace("@Composable\nprivate fun NoteCard", markdown_func + "\n@Composable\nprivate fun NoteCard")

# Update NoteCard to use renderMarkdown
old_body = r"""        if \(note\.body\.isNotEmpty\(\)\) Text\(note\.body, style = MaterialTheme\.typography\.bodyMedium, maxLines = 8, overflow = TextOverflow\.Ellipsis\)"""
new_body = """        if (note.body.isNotEmpty()) Text(renderMarkdown(note.body), style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)"""
code = re.sub(old_body, new_body, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/notes/NotesScreen.kt', 'w') as f:
    f.write(code)

