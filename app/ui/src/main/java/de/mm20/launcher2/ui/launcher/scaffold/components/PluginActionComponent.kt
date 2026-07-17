package de.mm20.launcher2.ui.launcher.scaffold.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.contracts.GestureActionPluginContract
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Invokes a single action on a GestureAction plugin, then immediately returns to the home
 * screen. Shows no UI of its own - the plugin has no opportunity to draw anything, it's a
 * fire-and-forget content-provider call.
 */
internal class PluginActionComponent(
    private val context: Context,
    private val authority: String,
    private val actionId: String,
) : ScaffoldComponent() {

    override val showSearchBar: Boolean = false
    override val drawBackground: Boolean = false
    override val hapticFeedback: Boolean = false

    override val isAtTop: State<Boolean?> = mutableStateOf(true)
    override val isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        Box(modifier = modifier.pointerInput(Unit) {})
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.call(
                    authority,
                    GestureActionPluginContract.Methods.Invoke,
                    actionId,
                    null,
                )
            } catch (e: Exception) {
                CrashReporter.logException(e)
            }
        }
        state.reset()
    }
}
