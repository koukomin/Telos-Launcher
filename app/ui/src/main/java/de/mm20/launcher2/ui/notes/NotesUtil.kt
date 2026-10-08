package de.mm20.launcher2.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLong: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLong)

@Composable
fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())
