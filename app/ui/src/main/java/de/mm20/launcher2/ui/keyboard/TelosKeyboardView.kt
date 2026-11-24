@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.mm20.launcher2.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.base.R as BaseR

@Composable
fun TelosKeyboardView(
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onToggleIncognito: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onPasteText: (String) -> Unit,
    onVibrate: () -> Unit,
) {
    val state = TelosKeyboardState
    val look = TelosKeyboardAppearance
    val primaryColor = look.accent
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(surfaceColor)
                .navigationBarsPadding()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
    ) {
        // Toolbar / Action Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    )
                }

                // Incognito Privacy Mode Indicator
                IconButton(
                    onClick = {
                        onVibrate()
                        onToggleIncognito()
                    }
                ) {
                    Icon(
                        painterResource(BaseR.drawable.lock_24px),
                        contentDescription = "Incognito",
                        tint =
                            if (state.privateMode) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Quick Clipboard Snippet Bar
            if (state.lastClipboardText.isNotBlank()) {
                Surface(
                    modifier =
                        Modifier.weight(1f)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onVibrate()
                                onPasteText(state.lastClipboardText)
                            },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = "📋 " + state.lastClipboardText,
                        maxLines = 1,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            Text(
                text = if (state.currentLanguage == KeyboardLanguage.Greek) "ΕΛ" else "EN",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = primaryColor,
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSwitchLanguage()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Auto-Correction / Suggestion Bar
        if (TelosKeyboardBuffer.activeSuggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(TelosKeyboardBuffer.activeSuggestions.toList()) { index, sugWord ->
                    val lead = index == 0
                    Surface(
                        modifier =
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                onVibrate()
                                onPasteText("$sugWord ")
                                TelosKeyboardDictionary.learnWord(sugWord, state.privateMode)
                                TelosKeyboardBuffer.clear()
                            },
                        color = if (lead) primaryColor.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = sugWord,
                            fontSize = if (lead) 16.sp else 13.sp,
                            fontWeight = if (lead) FontWeight.Bold else FontWeight.SemiBold,
                            color = primaryColor,
                            modifier = Modifier.padding(
                                horizontal = if (lead) 16.dp else 12.dp,
                                vertical = if (lead) 8.dp else 6.dp,
                            ),
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Main Keyboard Area
            when (state.keyboardMode) {
                KeyboardMode.Letters ->
                    LetterKeyboardGrid(
                        onKeyTap,
                        onBackspace,
                        onEnter,
                        onSpace,
                        onVibrate,
                        onSwitchLanguage,
                    )
                KeyboardMode.Numbers ->
                    SymbolKeyboardGrid(
                        isSymbols = false,
                        onKeyTap,
                        onBackspace,
                        onEnter,
                        onSpace,
                        onVibrate,
                    )
                KeyboardMode.Symbols ->
                    SymbolKeyboardGrid(
                        isSymbols = true,
                        onKeyTap,
                        onBackspace,
                        onEnter,
                        onSpace,
                        onVibrate,
                    )
                KeyboardMode.Emojis ->
                    EmojiKeyboardGrid { emoji ->
                        onVibrate()
                        onKeyTap(emoji)
                    }
                KeyboardMode.Clipboard ->
                    ClipboardPanel(state.lastClipboardText, onPasteText, onVibrate)
            }

            // A full-size pointer-input overlay hides the keys from hit testing. Direct key
            // input owns these gestures; the old heuristic overlay also inserted fixed words.

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
    val rows =
        if (state.currentLanguage == KeyboardLanguage.Greek) {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("ς", "ε", "ρ", "τ", "υ", "θ", "ι", "ο", "π"),
                listOf("α", "σ", "δ", "φ", "γ", "η", "ξ", "κ", "λ"),
                listOf("ζ", "χ", "ψ", "ω", "β", "ν", "μ"),
            )
        } else {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m"),
            )
        }

    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp)
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
        // Number Row 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rows[0].forEach { num ->
                KeyboardKey(text = num, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(num)
                }
            }
        }

        // Row 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rows[1].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }
        }

        // Row 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rows[2].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }
        }

        // Row 3 (Shift + Letters + Backspace)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shift Key
            Surface(
                modifier =
                    Modifier.weight(1.3f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            if (state.isShifted && !state.isCapsLock) {
                                state.isCapsLock = true
                            } else {
                                state.isShifted = !state.isShifted
                                state.isCapsLock = false
                            }
                        },
                color =
                    if (state.isCapsLock || state.isShifted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (state.isCapsLock) "⇪" else "⇧",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            rows[3].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }

            // Backspace Key
            Surface(
                modifier =
                    Modifier.weight(1.3f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onBackspace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⌫", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Control Row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ?123 Mode Toggle
            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            state.keyboardMode = KeyboardMode.Numbers
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("?123", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Globe Language Switcher
            Surface(
                modifier =
                    Modifier.weight(1f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSwitchLanguage()
                        },
                color = MaterialTheme.colorScheme.surface,
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

            // Spacebar
            Surface(
                modifier =
                    Modifier.weight(4.5f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSpace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (state.currentLanguage == KeyboardLanguage.Greek) "Ελληνικά"
                        else "English",
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

            // Enter Key
            Surface(
                modifier =
                    Modifier.weight(1.8f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onEnter()
                        },
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
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
}

@Composable
fun KeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val state = TelosKeyboardState
    val displayText =
        when {
            state.isShifted || state.isCapsLock -> text.uppercase()
            else -> text.lowercase()
        }

    Surface(
        modifier =
            modifier
                .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                .onGloballyPositioned { coords ->
                    val box = coords.boundsInRoot()
                    TelosKeyboardHits.remember(
                        text,
                        androidx.compose.ui.geometry.Rect(box.left, box.top, box.right, box.bottom),
                    )
                }
                .clip(RoundedCornerShape(6.dp))
                .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border =
            androidx.compose.foundation.BorderStroke(
                0.7.dp,
                de.mm20.launcher2.ui.component.TelosCyber.NeonCyan.copy(alpha = 0.35f),
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayText,
                fontSize = TelosKeyboardAppearance.scaledFontSp().sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
    val rows =
        if (!isSymbols) {
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
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            state.keyboardMode = KeyboardMode.Letters
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ABC", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            state.keyboardMode =
                                if (isSymbols) KeyboardMode.Numbers else KeyboardMode.Symbols
                        },
                color = MaterialTheme.colorScheme.surface,
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
                modifier =
                    Modifier.weight(4f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSpace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("Space", fontSize = 12.sp) }
            }

            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = TelosKeyboardAppearance.scaledKeyHeightDp().dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onBackspace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("⌫", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
fun EmojiKeyboardGrid(onSelectEmoji: (String) -> Unit) {
    val emojis =
        listOf(
            "😀",
            "😃",
            "😄",
            "😁",
            "😆",
            "😅",
            "😂",
            "🤣",
            "🥲",
            "🥹",
            "☺️",
            "😊",
            "😇",
            "🙂",
            "🙃",
            "😉",
            "😌",
            "😍",
            "🥰",
            "😘",
            "😗",
            "😙",
            "😚",
            "😋",
            "😛",
            "😝",
            "😜",
            "🤪",
            "🤨",
            "🧐",
            "🤓",
            "😎",
            "🥸",
            "🤩",
            "🥳",
            "😏",
            "😒",
            "😞",
            "😔",
            "😟",
            "😕",
            "🙁",
            "☹️",
            "😣",
            "😖",
            "😫",
            "😩",
            "🥺",
            "😢",
            "😭",
            "😮‍💨",
            "😤",
            "😠",
            "😡",
            "🤬",
            "🤯",
            "😳",
            "🥶",
            "😱",
            "😨",
            "😰",
            "😥",
            "😓",
            "🤗",
            "🤔",
            "🫣",
            "🤭",
            "🫢",
            "🫡",
            "🤫",
            "🫠",
            "🤥",
            "😶",
            "😶‍🌫️",
            "😐",
            "😑",
            "😬",
            "🫨",
            "🫠",
            "🇬🇷",
            "👍",
            "👎",
            "👏",
            "🙌",
            "🫶",
            "❤️",
            "🔥",
            "✨",
            "💯",
            "🎉",
            "🚀",
            "💡",
            "⚽",
            "🇬🇷",
            "🇬🇷",
            "🇬🇷",
        )

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(emojis) { emoji ->
            Box(
                modifier =
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        onSelectEmoji(emoji)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
            }
        }
    }
}

@Composable
fun ClipboardPanel(lastSnippet: String, onPaste: (String) -> Unit, onVibrate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "📋 Telos Clipboard Manager",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (lastSnippet.isNotBlank()) {
            Surface(
                modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        onVibrate()
                        onPaste(lastSnippet)
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = lastSnippet,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                "No recent text copied",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
