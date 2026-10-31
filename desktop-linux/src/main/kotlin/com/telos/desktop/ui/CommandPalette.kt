package com.telos.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.apps.DesktopFileLauncher
import com.telos.desktop.apps.TelosVirtualApp
import com.telos.desktop.apps.TelosVirtualApps
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.CandyIcon
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.search.DesktopSearch
import com.telos.desktop.search.SearchHit
import com.telos.desktop.search.SearchKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CommandPalette(
    visible: Boolean,
    icons: LinuxIconThemeResolver,
    onDismiss: () -> Unit,
    onOpenTelos: (TelosVirtualApp) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = TelosVirtualApps.all.map {
                SearchHit(
                    id = it.domain,
                    title = it.label,
                    subtitle = it.category.name,
                    kind = SearchKind.TelosApp,
                    score = 100,
                    telos = it,
                )
            }.take(5)
        } else {
            results = withContext(Dispatchers.IO) { DesktopSearch.search(query) }.take(8)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.95f),
        exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.95f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 120.dp)
                    .width(640.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TelosColors.surface)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {} // intercept clicks
                    .onKeyEvent {
                        if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                            onDismiss()
                            true
                        } else if (it.key == Key.Enter && it.type == KeyEventType.KeyDown && results.isNotEmpty()) {
                            val hit = results.first()
                            when (hit.kind) {
                                SearchKind.TelosApp -> hit.telos?.let { app -> onOpenTelos(app) }
                                SearchKind.Desktop -> hit.desktop?.let { d -> DesktopFileLauncher.launch(d) }
                                SearchKind.File -> hit.file?.let { f -> com.telos.desktop.apps.TelosFileHandler.open(f) }
                                else -> {}
                            }
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    }
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search apps, files, commands...", color = TelosColors.onSurface.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TelosColors.surfaceContainer,
                        unfocusedContainerColor = TelosColors.surfaceContainer,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = TelosColors.onSurface),
                    leadingIcon = {
                        CandyCyberIcon(CandyIcon.AppGrid, 24.dp)
                    }
                )

                if (results.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TelosColors.outline))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(results, key = { it.id }, contentType = { it.kind }) { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when (hit.kind) {
                                            SearchKind.TelosApp -> hit.telos?.let { onOpenTelos(it) }
                                            SearchKind.Desktop -> hit.desktop?.let { DesktopFileLauncher.launch(it) }
                                            SearchKind.File -> hit.file?.let { com.telos.desktop.apps.TelosFileHandler.open(it) }
                                            else -> {}
                                        }
                                        onDismiss()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (hit.telos != null) {
                                    CandyCyberIcon(hit.telos.icon, 32.dp)
                                } else if (hit.desktop != null) {
                                    DiskIcon(icons.resolve(hit.desktop.icon), hit.desktop.name, 32.dp)
                                } else {
                                    CandyCyberIcon(CandyIcon.Folder, 32.dp)
                                }

                                Column {
                                    Text(hit.title, color = TelosColors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    if (hit.subtitle.isNotBlank()) {
                                        Text(hit.subtitle, color = TelosColors.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
