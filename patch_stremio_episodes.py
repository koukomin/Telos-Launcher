import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/stremio/StremioScreen.kt', 'r') as f:
    code = f.read()

episodes_old = r"""    Column\(verticalArrangement = Arrangement\.spacedBy\(4\.dp\)\) \{
        episodes\.forEach \{ v ->
            Row\(
                Modifier\.fillMaxWidth\(\)\.clip\(RoundedCornerShape\(12\.dp\)\)\.clickable \{ vm\.showStreams\(meta, v\) \}\.padding\(vertical = 8\.dp\),
                verticalAlignment = Alignment\.CenterVertically,
            \) \{
                if \(v\.thumbnail != null\) \{
                    AsyncImage\(model = v\.thumbnail, contentDescription = null, contentScale = ContentScale\.Crop, modifier = Modifier\.width\(104\.dp\)\.aspectRatio\(1\.77f\)\.clip\(RoundedCornerShape\(8\.dp\)\)\)
                    Spacer\(Modifier\.width\(12\.dp\)\)
                \}
                Column\(Modifier\.weight\(1f\)\) \{
                    Text\(listOfNotNull\(v\.episode\?\.toString\(\), v\.title\)\.joinToString\("\. "\), style = MaterialTheme\.typography\.bodyLarge, maxLines = 2, overflow = TextOverflow\.Ellipsis\)
                    WatchMark\(vm, meta\.id, v\.id, videoOrder, v\.season, v\.episode\)
                    v\.overview\?\.let \{ Text\(it, style = MaterialTheme\.typography\.bodySmall, color = MaterialTheme\.colorScheme\.onSurfaceVariant, maxLines = 2, overflow = TextOverflow\.Ellipsis\) \}
                \}
            \}
        \}
    \}"""

episodes_new = """    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        episodes.forEach { v ->
            var showOptions by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.showStreams(meta, v) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (v.thumbnail != null) {
                        AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(104.dp).aspectRatio(1.77f).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(listOfNotNull(v.episode?.toString(), v.title).joinToString(". "), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        WatchMark(vm, meta.id, v.id, videoOrder, v.season, v.episode)
                        v.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    IconButton(onClick = { showOptions = true }) {
                        Icon(painterResource(R.drawable.more_vert_24px), contentDescription = "Options")
                    }
                }
                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                    DropdownMenuItem(
                        text = { Text("Mark as Watched") },
                        onClick = {
                            showOptions = false
                            vm.markWatched(meta.id, v.id, v.season, v.episode)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark Season as Watched") },
                        onClick = {
                            showOptions = false
                            vm.markSeasonWatched(meta.id, v.season, episodes.map { it.id to it.episode })
                        }
                    )
                }
            }
        }
    }"""

code = re.sub(episodes_old, episodes_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/stremio/StremioScreen.kt', 'w') as f:
    f.write(code)

