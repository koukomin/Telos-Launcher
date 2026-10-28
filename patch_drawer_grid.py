import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/settings/icons/IconsSettingsScreen.kt', 'r') as f:
    code = f.read()

# Replace the conditional check for AnimatedVisibility(override)
# so that the columnCount slider is visible when !drawerFullWidth AND !override too (or just decouple it)
old_override = r"""                AnimatedVisibility\(override\) \{
                    Column \{
                        SliderPreference\(
                            title = stringResource\(R\.string\.preference_grid_column_count\),
                            value = drawerGrid\.columnCount,
                            min = 3,
                            max = 12,
                            onValueChanged = \{ viewModel\.setDrawerGridColumnCount\(it\) \}
                        \)
                        SliderPreference\(
                            title = stringResource\(R\.string\.preference_grid_icon_size\),
                            value = drawerGrid\.iconSize,
                            step = 8,
                            min = 32,
                            max = 64,
                            onValueChanged = \{ viewModel\.setDrawerGridIconSize\(it\) \}
                        \)
                    \}
                \}"""

new_override = """                AnimatedVisibility(override || !drawerFullWidth) {
                    Column {
                        SliderPreference(
                            title = stringResource(R.string.preference_grid_column_count),
                            value = drawerGrid.columnCount,
                            min = 3,
                            max = 12,
                            onValueChanged = { viewModel.setDrawerGridColumnCount(it) }
                        )
                    }
                }
                AnimatedVisibility(override) {
                    Column {
                        SliderPreference(
                            title = stringResource(R.string.preference_grid_icon_size),
                            value = drawerGrid.iconSize,
                            step = 8,
                            min = 32,
                            max = 64,
                            onValueChanged = { viewModel.setDrawerGridIconSize(it) }
                        )
                    }
                }"""

code = re.sub(old_override, new_override, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/settings/icons/IconsSettingsScreen.kt', 'w') as f:
    f.write(code)

