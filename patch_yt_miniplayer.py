import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/youtube/YouTubeScreen.kt', 'r') as f:
    code = f.read()

yt_card_old = r"""    Surface\(
        onClick = \{ if \(matches && music\) showPlayer = true else onOpen\(item\.mediaMetadata\.extras\?\.getBoolean\("audioOnly"\) == true\) \},
        shape = RoundedCornerShape\(12\.dp\),
        color = MaterialTheme\.colorScheme\.surfaceContainerHighest,
        shadowElevation = 0\.dp,
        modifier = Modifier
            \.fillMaxWidth\(\)
            \.padding\(12\.dp\)
    \) \{
        Row\(
            modifier = Modifier\.padding\(10\.dp\),
            verticalAlignment = Alignment\.CenterVertically
        \) \{"""

yt_card_new = """    Surface(
        onClick = { if (matches && music) showPlayer = true else onOpen(item.mediaMetadata.extras?.getBoolean("audioOnly") == true) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {"""

code = re.sub(yt_card_old, yt_card_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/youtube/YouTubeScreen.kt', 'w') as f:
    f.write(code)

