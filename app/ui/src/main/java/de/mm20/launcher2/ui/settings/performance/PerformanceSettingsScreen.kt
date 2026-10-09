package de.mm20.launcher2.ui.settings.performance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data object PerformanceSettingsRoute : NavKey

@Composable
private fun SettingHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun PerformanceSettingsScreen() {
    val viewModel: PerformanceSettingsScreenVM = viewModel()

    val reduceAnimations by viewModel.reduceAnimations.collectAsStateWithLifecycle()
    val animationSpeed by viewModel.animationSpeed.collectAsStateWithLifecycle()
    val searchDebounceMs by viewModel.searchDebounceMs.collectAsStateWithLifecycle()
    val iconCacheSize by viewModel.iconCacheSize.collectAsStateWithLifecycle()
    val bouncePhysics by viewModel.bouncePhysics.collectAsStateWithLifecycle()

    PreferenceScreen(title = stringResource(R.string.preference_screen_performance)) {
        item {
            PreferenceCategory(title = stringResource(R.string.preference_performance_category_animations)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_performance_reduce_animations),
                    summary = stringResource(R.string.preference_performance_reduce_animations_summary),
                    value = reduceAnimations == true,
                    onValueChanged = { viewModel.setReduceAnimations(it) },
                )
                AnimatedVisibility(reduceAnimations == false) {
                    androidx.compose.foundation.layout.Column {
                        SliderPreference(
                            title = stringResource(R.string.preference_performance_animation_speed),
                            value = animationSpeed ?: 1f,
                            min = 0.5f,
                            max = 2f,
                            step = 0.25f,
                            onValueChanged = { viewModel.setAnimationSpeed(it) },
                            label = { Text("${it}x") }
                        )
                        SliderPreference(
                            title = stringResource(R.string.preference_performance_bounce_physics),
                            value = (((1f - (bouncePhysics ?: 1f)) / 0.7f) * 100).roundToInt().coerceIn(0, 100),
                            min = 0,
                            max = 100,
                            step = 10,
                            onValueChanged = { viewModel.setBouncePhysics(1f - (it / 100f) * 0.7f) },
                            label = { Text("$it%") }
                        )
                    }
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_performance_category_search)) {
                SliderPreference(
                    title = stringResource(R.string.preference_performance_search_debounce),
                    value = searchDebounceMs ?: 0,
                    min = 0,
                    max = 500,
                    step = 50,
                    onValueChanged = { viewModel.setSearchDebounceMs(it) },
                    label = { Text(if (it == 0) stringResource(R.string.preference_performance_debounce_off) else "${it}ms") }
                )
                SettingHint(stringResource(R.string.preference_performance_search_debounce_summary))
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_performance_category_caching)) {
                SliderPreference(
                    title = stringResource(R.string.preference_performance_icon_cache_size),
                    value = iconCacheSize ?: 200,
                    min = 50,
                    max = 500,
                    step = 50,
                    onValueChanged = { viewModel.setIconCacheSize(it) },
                )
                SettingHint(stringResource(R.string.preference_performance_icon_cache_size_summary))
                SettingHint(stringResource(R.string.preference_performance_background_note))
            }
        }
    }
}
