import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/comms/radio/RadioHome.kt', 'r') as f:
    code = f.read()

# Replace RadioHome with simplified version without pills
radio_home_old = r"fun RadioHome\([\s\S]*?RadioChip\.values\(\)\.toList\(\)\) \{ c ->[\s\S]*?RadioPill\([\s\S]*?selected = chip == c,[\s\S]*?\n\s*\}\n\s*\}"
radio_home_new = """fun RadioHome(
    favorites: List<RadioStation>,
    player: RadioViewModel,
    chip: RadioChip,
    onChip: (RadioChip) -> Unit,
    onLongClick: (RadioStation) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    val shown = favorites
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }"""

code = re.sub(radio_home_old, radio_home_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/comms/radio/RadioHome.kt', 'w') as f:
    f.write(code)

