package de.mm20.launcher2.ui.settings.searchactions

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.AppSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomIntentActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomWebsearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheet
import de.mm20.launcher2.ui.component.ExperimentalBadge
import de.mm20.launcher2.ui.component.SearchActionIcon
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.ktx.toPixels

@Composable
fun EditSearchActionSheet(
    expanded: Boolean,
    initialSearchAction: CustomizableSearchActionBuilder?,
    onSave: (CustomizableSearchActionBuilder) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(
        expanded = expanded,
    ) {
        var confirmDismiss by remember { mutableStateOf(false) }

        val viewModel: EditSearchActionSheetVM = viewModel()
        DisposableEffect(initialSearchAction) {
            viewModel.init(initialSearchAction)
            onDispose {
                viewModel.onDismiss()
            }
        }
        val page by viewModel.currentPage

        if (page == null) return@BottomSheet

        val searchAction by viewModel.searchAction
        val createNew by viewModel.createNew

        if (confirmDismiss) {
            AlertDialog(
                onDismissRequest = { confirmDismiss = false },
                dismissButton = {
                    OutlinedButton(onClick = {
                        confirmDismiss = false
                    }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        confirmDismiss = false
                        onDismiss()
                    }) {
                        Text(stringResource(I18nR.string.action_quit))
                    }
                },
                text = {
                    Text(stringResource(I18nR.string.dialog_discard_unsaved))
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            CenterAlignedTopAppBar(
                title = {
                    TelosCyberHeading {
                        if (page != EditSearchActionPage.PickIcon) {
                            Text(
                                stringResource(
                                    if (createNew) {
                                        I18nR.string.create_search_action_title
                                    } else {
                                        I18nR.string.edit_search_action_title
                                    }
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (page == EditSearchActionPage.PickIcon) {
                        FilledTonalIconButton(onClick = {
                            viewModel.applyIcon()
                        }) {
                            Icon(
                                painterResource(BaseR.drawable.arrow_back_24px),
                                stringResource(I18nR.string.menu_back)
                            )
                        }
                    }
                },
                actions = {
                    if (page != EditSearchActionPage.PickIcon) {
                        FilledTonalIconButton(
                            onClick = {
                                if (initialSearchAction == searchAction) {
                                    onDismiss()
                                    return@FilledTonalIconButton
                                }
                                when (page) {
                                    EditSearchActionPage.CustomizeAppSearch,
                                    EditSearchActionPage.CustomizeWebSearch,
                                    EditSearchActionPage.CustomizeCustomIntent -> {
                                        confirmDismiss = true
                                    }

                                    else -> onDismiss()
                                }
                            },
                        ) {
                            Icon(
                                painterResource(BaseR.drawable.close_24px),
                                stringResource(I18nR.string.close)
                            )
                        }
                    }
                },
            )
            AnimatedContent(
                page!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, false)
            ) {
                when (it) {
                    EditSearchActionPage.SelectType -> SelectTypePage(viewModel)
                    EditSearchActionPage.InitWebSearch -> InitWebSearchPage(viewModel)
                    EditSearchActionPage.InitAppSearch -> InitAppSearchPage(viewModel)
                    EditSearchActionPage.CustomizeWebSearch -> CustomizeWebSearch(viewModel)
                    EditSearchActionPage.CustomizeCustomIntent -> CustomizeCustomIntent(viewModel)
                    EditSearchActionPage.CustomizeAppSearch -> CustomizeAppSearch(viewModel)
                    EditSearchActionPage.PickIcon -> PickIcon(viewModel)
                }
            }

            val button: (@Composable () -> Unit)? = when (page) {
                EditSearchActionPage.CustomizeAppSearch,
                EditSearchActionPage.CustomizeWebSearch,
                EditSearchActionPage.CustomizeCustomIntent -> {
                    {
                        Button(onClick = {
                            if (viewModel.validate()) {
                                viewModel.onSave()
                                searchAction?.let { onSave(it) }
                            }
                        }, enabled = !searchAction?.label.isNullOrBlank()) {
                            Text(stringResource(I18nR.string.save))
                        }
                    }
                }

                EditSearchActionPage.InitWebSearch -> {
                    {
                        val density = LocalDensity.current
                        Button(
                            onClick = {
                                if (viewModel.skipWebsearchImport.value) {
                                    viewModel.skipWebsearchImport()
                                } else {
                                    viewModel.importWebsearch(density)
                                }
                            },
                            enabled = !viewModel.loadingWebsearch.value
                        ) {
                            Text(
                                stringResource(
                                    if (viewModel.skipWebsearchImport.value) {
                                        I18nR.string.skip
                                    } else {
                                        I18nR.string.action_next
                                    }
                                )
                            )
                        }
                    }
                }

                else -> null
            }

            AnimatedVisibility(button != null) {
                FlexibleBottomAppBar(
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        button?.invoke()
                    }
                }
            }
        }
    }
}
