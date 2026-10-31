package de.mm20.launcher2.ui.component
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.ui.theme.panelContentColor
import de.mm20.launcher2.ui.theme.rememberWallpaperContentStyle
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.theme.transparency.transparency
import org.koin.compose.koinInject

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    style: SearchBarStyle,
    level: SearchBarLevel,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: () -> Unit = {},
    onUnfocus: () -> Unit = {},
    reverse: Boolean = false,
    darkColors: Boolean = false,
    automaticColors: Boolean = false,
    readOnly: Boolean = false,
    menu: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
    onKeyboardActionGo: (KeyboardActionScope.() -> Unit)? = null
) {

    val readability = rememberWallpaperContentStyle(
        forcedDark = if (automaticColors) null else darkColors,
        backing = false,
    )
    val transition = updateTransition(level, label = "Searchbar")
    val context = LocalContext.current
    val searchUiSettings: SearchUiSettings = koinInject()
    val privateKeyboard by remember { searchUiSettings.privateKeyboard }.collectAsState(false)

    val elevation by transition.animateDp(
        label = "elevation",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                targetState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                else -> tween(durationMillis = 500)
            }
        }
    ) {
        when {
            it == SearchBarLevel.Resting && style != SearchBarStyle.Solid -> 0.dp
            it == SearchBarLevel.Raised -> 8.dp
            else -> 0.dp
        }
    }

    val backgroundOpacity by transition.animateFloat(label = "backgroundOpacity",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                targetState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                else -> tween(durationMillis = 200)
            }
        }) {
        when {
            it == SearchBarLevel.Active -> MaterialTheme.transparency.surface
            style != SearchBarStyle.Transparent -> 1f
            it == SearchBarLevel.Resting -> 0f
            else -> 1f
        }
    }

    val opacity by transition.animateFloat(label = "opacity") {
        if (style == SearchBarStyle.Hidden && it == SearchBarLevel.Resting) 0f
        else 1f
    }

    // Use wallpaper text only after the actual surface has faded out, not at the target
    // state change. Otherwise white text can briefly sit on a still-light panel.
    val overWallpaper = style == SearchBarStyle.Transparent && backgroundOpacity <= 0.01f
    val panelContent = panelContentColor(
        MaterialTheme.colorScheme.surface.copy(alpha = backgroundOpacity)
    )
    val resolvedContentColor = if (overWallpaper) readability.color else panelContent
    LauncherCard(
        modifier = modifier
            .then(if (overWallpaper) readability.modifier else Modifier)
            .alpha(opacity),
        backgroundOpacity = backgroundOpacity,
        elevation = elevation,
        contentColor = resolvedContentColor
    ) {
        CompositionLocalProvider(
            LocalContentColor provides resolvedContentColor
        ) {
            Column(
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.heightIn(min = de.mm20.launcher2.ui.layout.UiSpacing.TouchTarget),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.padding(12.dp),
                        painter = painterResource(BaseR.drawable.search_24px),
                        contentDescription = null,
                        tint = resolvedContentColor
                    )
                    Box(
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(I18nR.string.search_bar_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = resolvedContentColor
                            )
                        }
                        BasicTextField(
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.hasFocus) onFocus()
                                }
                                .focusRequester(focusRequester)
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = context.getString(I18nR.string.search_bar_placeholder)
                                },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = resolvedContentColor
                            ),
                            singleLine = true,
                            value = value,
                            onValueChange = onValueChange,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Go,
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None,
                            ).withPrivateKeyboard(privateKeyboard),
                            keyboardActions = KeyboardActions(
                                onGo = onKeyboardActionGo,
                            ),
                            readOnly = readOnly,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        trailing()
                        menu()
                    }
                }
                this.actions()
            }
        }
    }
}

enum class SearchBarLevel: Comparable<SearchBarLevel> {
    /**
     * The default, "hidden" state, when the launcher is in its initial state (scroll position is 0
     * and search is closed)
     */
    Resting,

    /**
     * When the search is open but there is no content behind the search bar (scroll position is 0)
     */
    Active,

    /**
     * When there is content below the search bar which requires the search bar to be raised above
     * this content (scroll position is not 0)
     */
    Raised
}
