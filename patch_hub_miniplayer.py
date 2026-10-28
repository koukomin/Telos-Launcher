import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/hub/MediaHubScreen.kt', 'r') as f:
    code = f.read()

# Shrink the spacing of HubMiniPlayer
old_card = r"""        Surface\(
            shape = cardShape,
            color = scheme\.surfaceContainerHigh,
            contentColor = scheme\.onSurface,
            tonalElevation = 0\.dp,
            shadowElevation = 0\.dp,
            modifier = Modifier
                \.fillMaxWidth\(\)
                \.padding\(horizontal = 8\.dp, vertical = 4\.dp\)
                \.clip\(cardShape\)
                \.clickable\(onClickLabel = stringResource\(R\.string\.au9_mediahub_open_player\)\) \{ onOpen\(display\) \},
        \) \{
            Box \{
            Row\(
                Modifier\.padding\(start = 8\.dp, end = 8\.dp, top = 6\.dp, bottom = 8\.dp\),
                verticalAlignment = Alignment\.CenterVertically,
            \) \{
                Box\("""

new_card = """        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
            contentColor = scheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClickLabel = stringResource(R.string.au9_mediahub_open_player)) { onOpen(display) },
        ) {
            Box {
            Row(
                Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box("""

code = re.sub(old_card, new_card, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/hub/MediaHubScreen.kt', 'w') as f:
    f.write(code)

