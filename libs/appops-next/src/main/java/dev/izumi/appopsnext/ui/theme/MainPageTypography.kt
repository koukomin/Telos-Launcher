package dev.izumi.appopsnext.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/** The embedded component inherits Telos typography, including the user's font scale. */
@Composable fun MainPageTypography(content: @Composable () -> Unit) = content()
@Composable fun mainPageHeadingWeight(): FontWeight = FontWeight.SemiBold
