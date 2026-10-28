import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/comms/radio/RadioDashboardScreen.kt', 'r') as f:
    code = f.read()

# Remove the LazyRow with filter tabs (Collection / History)
tabs_old = r"""        androidx\.compose\.foundation\.lazy\.LazyRow\(
            contentPadding = PaddingValues\(horizontal = 16\.dp\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\),
        \) \{
            item \{
                de\.mm20\.launcher2\.ui\.media\.MediaFilterButton\(selectedTabIndex == 0 && !searchExpanded,
                    \{ selectedTabIndex = 0; searchExpanded = false \}, stringResource\(R\.string\.au_radio_tab_collection\)\)
            \}
            item \{
                de\.mm20\.launcher2\.ui\.media\.MediaFilterButton\(selectedTabIndex == 2 && !searchExpanded,
                    \{ selectedTabIndex = 2; searchExpanded = false \}, stringResource\(R\.string\.au_radio_tab_history\)\)
            \}
        \}"""

code = re.sub(tabs_old, "", code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/comms/radio/RadioDashboardScreen.kt', 'w') as f:
    f.write(code)

