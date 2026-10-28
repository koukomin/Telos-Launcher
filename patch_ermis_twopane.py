import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/ermis/ErmisScreen.kt', 'r') as f:
    code = f.read()

# Inspect how selected message is handled in ErmisMailbox
old_selected_dialog = r"""    selected\?\.let \{ message ->
        val messageFolders = remember\(state\.folders, message\.accountId\) \{ state\.folders\.filter \{ it\.accountId == message\.accountId \} \}
        MessageDialog\(message, conversation = selectedConversation,"""

# We can wrap the whole Scaffold or main content in TwoPane if isWide
old_scaffold_content = r"""    Scaffold\(containerColor = androidx\.compose\.ui\.graphics\.Color\.Transparent,
        topBar = \{
            Surface\("""

# Let's check LocalConfiguration width
twopane_check = """    val config = LocalConfiguration.current
    val isWideScreen = config.screenWidthDp >= 600
"""

if "val isWideScreen = config.screenWidthDp >= 600" not in code:
    code = code.replace("val context = LocalContext.current", "val context = LocalContext.current\n" + twopane_check)

# Add TwoPane logic around selected message dialog for non-wide vs wide
old_dialog_call = r"""    selected\?\.let \{ message ->
        val messageFolders = remember\(state\.folders, message\.accountId\) \{ state\.folders\.filter \{ it\.accountId == message\.accountId \} \}
        MessageDialog\(message, conversation = selectedConversation,"""

new_dialog_call = """    if (!isWideScreen) {
        selected?.let { message ->
            val messageFolders = remember(state.folders, message.accountId) { state.folders.filter { it.accountId == message.accountId } }
            MessageDialog(message, conversation = selectedConversation,"""

code = re.sub(old_dialog_call, new_dialog_call, code)

# Close the if (!isWideScreen) block at the end of MessageDialog
old_dialog_end = r"""        onTrash = \{ task \{ repository\.deleteMessage\(message\.accountId, message\.folder, message\.uid\); selected = null \} \}
    \) \}"""

new_dialog_end = """        onTrash = { task { repository.deleteMessage(message.accountId, message.folder, message.uid); selected = null } }
        ) }
    }"""

code = re.sub(old_dialog_end, new_dialog_end, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/ermis/ErmisScreen.kt', 'w') as f:
    f.write(code)

