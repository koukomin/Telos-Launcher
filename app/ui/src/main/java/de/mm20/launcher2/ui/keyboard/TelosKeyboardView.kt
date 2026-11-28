@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.mm20.launcher2.ui.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.mm20.launcher2.base.R as BaseR
import kotlinx.coroutines.delay

@Composable
fun TelosKeyboardView(
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onToggleIncognito: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onPasteText: (String) -> Unit,
    onPasteClipboardItem: (ClipboardItem) -> Unit,
    onVibrate: () -> Unit,
) {
    val state = TelosKeyboardState
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .navigationBarsPadding()
            .padding(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 4.dp)
    ) {
        // Gboard Style Toolbar Row
        GboardToolbar(
            onVibrate = onVibrate,
            onToggleIncognito = onToggleIncognito,
            onSwitchLanguage = onSwitchLanguage,
            onPasteClipboardItem = onPasteClipboardItem
        )

        // Gboard Style Candidate Strip (Word Suggestions)
        if (TelosKeyboardBuffer.activeSuggestions.isNotEmpty()) {
            GboardCandidateStrip(
                suggestions = TelosKeyboardBuffer.activeSuggestions.toList(),
                onSelectWord = { sugWord ->
                    onVibrate()
                    onPasteText("$sugWord ")
                    TelosKeyboardDictionary.learnWord(sugWord, state.privateMode)
                    TelosKeyboardBuffer.clear()
                }
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Main Keyboard Area
            when (state.keyboardMode) {
                KeyboardMode.Letters ->
                    LetterKeyboardGrid(
                        onKeyTap = onKeyTap,
                        onBackspace = onBackspace,
                        onEnter = onEnter,
                        onSpace = onSpace,
                        onVibrate = onVibrate,
                        onSwitchLanguage = onSwitchLanguage,
                    )
                KeyboardMode.Numbers ->
                    SymbolKeyboardGrid(
                        isSymbols = false,
                        onKeyTap = onKeyTap,
                        onBackspace = onBackspace,
                        onEnter = onEnter,
                        onSpace = onSpace,
                        onVibrate = onVibrate,
                    )
                KeyboardMode.Symbols ->
                    SymbolKeyboardGrid(
                        isSymbols = true,
                        onKeyTap = onKeyTap,
                        onBackspace = onBackspace,
                        onEnter = onEnter,
                        onSpace = onSpace,
                        onVibrate = onVibrate,
                    )
                KeyboardMode.Emojis ->
                    EmojiKeyboardGrid { emoji ->
                        onVibrate()
                        onKeyTap(emoji)
                    }
                KeyboardMode.Clipboard ->
                    ClipboardPanel(
                        items = state.clipboardItems,
                        onPasteItem = { item ->
                            onVibrate()
                            onPasteClipboardItem(item)
                        }
                    )
            }

            // Floating Key Preview Bubble (Magnifier)
            val previewText = state.activeKeyPreview
            if (previewText != null) {
                KeyPreviewBubble(text = previewText)
            }
        }
    }
}

@Composable
private fun GboardToolbar(
    onVibrate: () -> Unit,
    onToggleIncognito: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onPasteClipboardItem: (ClipboardItem) -> Unit,
) {
    val state = TelosKeyboardState
    val primaryColor = TelosKeyboardAppearance.accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji Mode Button
            IconButton(
                onClick = {
                    onVibrate()
                    state.keyboardMode =
                        if (state.keyboardMode == KeyboardMode.Emojis) KeyboardMode.Letters
                        else KeyboardMode.Emojis
                }
            ) {
                Text(
                    if (state.keyboardMode == KeyboardMode.Emojis) "ABC" else "😊",
                    fontSize = 18.sp,
                )
            }

            // Clipboard Panel Button
            IconButton(
                onClick = {
                    onVibrate()
                    state.keyboardMode =
                        if (state.keyboardMode == KeyboardMode.Clipboard) KeyboardMode.Letters
                        else KeyboardMode.Clipboard
                }
            ) {
                Icon(
                    painterResource(BaseR.drawable.content_copy_24px),
                    contentDescription = "Clipboard",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Incognito / Private Mode Toggle
            IconButton(
                onClick = {
                    onVibrate()
                    onToggleIncognito()
                }
            ) {
                Icon(
                    painterResource(BaseR.drawable.lock_24px),
                    contentDescription = "Incognito",
                    tint = if (state.privateMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Quick Clipboard Snippet Chip (Text or Image Thumbnail)
        val lastClip = state.lastClipboardItem
        if (lastClip != null && ((lastClip.text != null && lastClip.text.length > 0) || lastClip.imageUri != null)) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onVibrate()
                        onPasteClipboardItem(lastClip)
                    },
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (lastClip.imageUri != null) {
                        AsyncImage(
                            model = lastClip.imageUri,
                            contentDescription = "Clipboard Image",
                            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "Image",
                            maxLines = 1,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else if (lastClip.text != null) {
                        Text(
                            text = "📋 " + lastClip.text,
                            maxLines = 1,
                            fontSize = 12.sp,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Language Pill Switcher
        Text(
            text = if (state.currentLanguage == KeyboardLanguage.Greek) "ΕΛ" else "EN",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = primaryColor,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable {
                    onVibrate()
                    onSwitchLanguage()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GboardCandidateStrip(
    suggestions: List<String>,
    onSelectWord: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val primaryWord = if (suggestions.size > 0) suggestions[0] else ""
        val word2 = if (suggestions.size > 1) suggestions[1] else null
        val word3 = if (suggestions.size > 2) suggestions[2] else null

        if (word2 != null) {
            CandidateItem(
                text = word2,
                isPrimary = false,
                modifier = Modifier.weight(1f),
                onClick = { onSelectWord(word2) }
            )
        }

        if (primaryWord.length > 0) {
            CandidateItem(
                text = primaryWord,
                isPrimary = true,
                modifier = Modifier.weight(1.2f),
                onClick = { onSelectWord(primaryWord) }
            )
        }

        if (word3 != null) {
            CandidateItem(
                text = word3,
                isPrimary = false,
                modifier = Modifier.weight(1f),
                onClick = { onSelectWord(word3) }
            )
        }
    }
}

@Composable
private fun CandidateItem(
    text: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)) {
            Text(
                text = text,
                fontSize = if (isPrimary) 15.sp else 13.sp,
                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LetterKeyboardGrid(
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onVibrate: () -> Unit,
    onSwitchLanguage: () -> Unit,
) {
    val state = TelosKeyboardState
    val look = TelosKeyboardAppearance

    val letterRows = if (state.currentLanguage == KeyboardLanguage.Greek) {
        listOf(
            listOf(";", "ς", "ε", "ρ", "τ", "υ", "θ", "ι", "ο", "π"),
            listOf("α", "σ", "δ", "φ", "γ", "η", "ξ", "κ", "λ"),
            listOf("ζ", "χ", "ψ", "ω", "β", "ν", "μ"),
        )
    } else {
        listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m"),
        )
    }

    val symbolHintsMap = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        ";" to "1", "ς" to "2", "ε" to "3", "ρ" to "4", "τ" to "5",
        "υ" to "6", "θ" to "7", "ι" to "8", "ο" to "9", "π" to "0"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .pointerInput(state.currentLanguage) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var dragging = false
                    drag(down.id) { change ->
                        val dist = (change.position - start).getDistance()
                        if (!dragging && dist > 28f) {
                            dragging = true
                            TelosGlideTyping.startSwipe()
                            TelosGlideTyping.addPoint(start)
                        }
                        if (dragging) {
                            change.consume()
                            TelosGlideTyping.addPoint(change.position)
                        }
                    }
                    if (dragging) {
                        TelosGlideTyping.finishSwipe(state.currentLanguage) { word ->
                            onKeyTap(word)
                            onSpace()
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Dedicated Number Row (if enabled)
        if (look.showNumberRow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { num ->
                    KeyboardKey(text = num, modifier = Modifier.weight(1f)) {
                        onVibrate()
                        onKeyTap(num)
                    }
                }
            }
        }

        // Row 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            letterRows[0].forEach { char ->
                val hint = if (look.showSymbolHints) symbolHintsMap[char] else null
                KeyboardKey(text = char, subLabel = hint, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }
        }

        // Row 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            letterRows[1].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }
        }

        // Row 3 (Shift + Letters + Backspace)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shift Key
            Surface(
                modifier = Modifier
                    .weight(1.3f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        if (state.isShifted && !state.isCapsLock) {
                            state.isCapsLock = true
                        } else {
                            state.isShifted = !state.isShifted
                            state.isCapsLock = false
                        }
                    },
                color = if (state.isCapsLock || state.isShifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (state.isCapsLock) "⇪" else "⇧",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isCapsLock || state.isShifted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            letterRows[2].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }

            // Backspace Key
            Surface(
                modifier = Modifier
                    .weight(1.3f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        onBackspace()
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⌫", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Control Row (Gboard Style)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ?123 Mode Toggle
            Surface(
                modifier = Modifier
                    .weight(1.4f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        state.keyboardMode = KeyboardMode.Numbers
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("?123", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Globe Language Switcher
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        onSwitchLanguage()
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🌐", fontSize = 16.sp)
                }
            }

            // Comma
            KeyboardKey(text = ",", modifier = Modifier.weight(1f)) {
                onVibrate()
                onKeyTap(",")
            }

            // Spacebar (Pill Shape with Language Label)
            Surface(
                modifier = Modifier
                    .weight(4.2f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        onSpace()
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (state.currentLanguage == KeyboardLanguage.Greek) "Ελληνικά" else "English",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Period
            KeyboardKey(text = ".", modifier = Modifier.weight(1f)) {
                onVibrate()
                onKeyTap(".")
            }

            // Gboard Dynamic Enter Key
            GboardEnterKey(
                imeAction = state.imeAction,
                modifier = Modifier
                    .weight(1.8f)
                    .height(look.scaledKeyHeightDp().dp),
                onClick = {
                    onVibrate()
                    onEnter()
                }
            )
        }
    }
}

@Composable
private fun GboardEnterKey(
    imeAction: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (imeAction) {
                EditorInfo.IME_ACTION_SEARCH ->
                    Icon(
                        painter = painterResource(BaseR.drawable.search_24px),
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT ->
                    Icon(
                        painter = painterResource(BaseR.drawable.arrow_forward_24px),
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                EditorInfo.IME_ACTION_DONE ->
                    Icon(
                        painter = painterResource(BaseR.drawable.check_24px),
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                else ->
                    Text(
                        "↵",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
            }
        }
    }
}

@Composable
fun KeyboardKey(
    text: String,
    subLabel: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val state = TelosKeyboardState
    val look = TelosKeyboardAppearance
    val displayText = when {
        state.isShifted || state.isCapsLock -> text.uppercase()
        else -> text.lowercase()
    }

    val borderStroke = if (look.showKeyBorders) {
        BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    } else null

    Surface(
        modifier = modifier
            .height(look.scaledKeyHeightDp().dp)
            .onGloballyPositioned { coords ->
                val box = coords.boundsInRoot()
                TelosKeyboardHits.remember(
                    text,
                    androidx.compose.ui.geometry.Rect(box.left, box.top, box.right, box.bottom),
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                state.activeKeyPreview = displayText
                onClick()
            },
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                fontSize = look.scaledFontSp().sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (subLabel != null) {
                Text(
                    text = subLabel,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyPreviewBubble(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    LaunchedEffect(text) {
        delay(180)
        TelosKeyboardState.activeKeyPreview = null
    }
}

@Composable
fun SymbolKeyboardGrid(
    isSymbols: Boolean,
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onVibrate: () -> Unit,
) {
    val state = TelosKeyboardState
    val look = TelosKeyboardAppearance

    val rows = if (!isSymbols) {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
            listOf("=\\<", "*", "\"", "'", ":", ";", "!", "?", "€", "£"),
        )
    } else {
        listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
            listOf("£", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\"),
            listOf("%", "©", "®", "™", "✓", "[", "]", "<", ">", "§"),
        )
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { sym ->
                    KeyboardKey(text = sym, modifier = Modifier.weight(1f)) {
                        onVibrate()
                        onKeyTap(sym)
                    }
                }
            }
        }

        // Bottom Row for Symbols Mode
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .weight(1.5f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        state.keyboardMode = KeyboardMode.Letters
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ABC", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1.5f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        state.keyboardMode = if (isSymbols) KeyboardMode.Numbers else KeyboardMode.Symbols
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (isSymbols) "123" else "=\\<",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(4f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        onSpace()
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("Space", fontSize = 13.sp) }
            }

            Surface(
                modifier = Modifier
                    .weight(1.5f)
                    .height(look.scaledKeyHeightDp().dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onVibrate()
                        onBackspace()
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("⌫", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
fun EmojiKeyboardGrid(onSelectEmoji: (String) -> Unit) {
    val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "☺️", "😊",
        "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋",
        "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏",
        "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺",
        "😢", "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥶", "😱", "😨",
        "😰", "😥", "😓", "🤗", "🤔", "🫣", "🤭", "🫢", "🫡", "🤫", "🫠", "🤥",
        "😶", "😶‍🌫️", "😐", "😑", "😬", "🫨", "🫠", "🇬🇷", "👍", "👎", "👏", "🙌",
        "🫶", "❤️", "🔥", "✨", "💯", "🎉", "🚀", "💡", "⚽", "🇬🇷", "🇬🇷", "🇬🇷"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(emojis) { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectEmoji(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
            }
        }
    }
}

@Composable
fun ClipboardPanel(
    items: List<ClipboardItem>,
    onPasteItem: (ClipboardItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "📋 Telos Clipboard Manager",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        if (items.size > 0) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(items) { _, item ->
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 220.dp)
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPasteItem(item) },
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (item.imageUri != null) {
                                AsyncImage(
                                    model = item.imageUri,
                                    contentDescription = "Copied Image",
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tap to paste image",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (item.text != null) {
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                "No recent items on clipboard",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
