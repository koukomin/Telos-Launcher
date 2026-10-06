package de.mm20.launcher2.ui.comms

import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object ContactGroupsRoute : NavKey

data class ContactGroup(val id: Long, val title: String, val count: Int)

@Composable
fun ContactGroupsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val groups = produceState(emptyList<ContactGroup>()) {
        value = withContext(Dispatchers.IO) {
            val out = mutableListOf<ContactGroup>()
            runCatching {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(
                    ContactsContract.Groups._ID,
                    ContactsContract.Groups.TITLE,
                    ContactsContract.Groups.SUMMARY_COUNT,
                ),
                "${ContactsContract.Groups.DELETED}=0 AND ${ContactsContract.Groups.GROUP_VISIBLE}=1",
                null,
                "${ContactsContract.Groups.TITLE} ASC",
            )?.use { c ->
                val idCol = c.getColumnIndex(ContactsContract.Groups._ID)
                val titleCol = c.getColumnIndex(ContactsContract.Groups.TITLE)
                val countCol = c.getColumnIndex(ContactsContract.Groups.SUMMARY_COUNT)
                while (c.moveToNext()) {
                    out += ContactGroup(
                        c.getLong(idCol),
                        c.getString(titleCol).orEmpty(),
                        if (countCol >= 0) c.getInt(countCol) else 0,
                    )
                }
            }
            }
            out
        }
    }
    PreferenceScreen(title = { Text("Groups") }) {
        if (groups.value.isEmpty()) {
            item { Text("No contact groups on this device.", modifier = Modifier.padding(16.dp)) }
        }
        groups.value.forEach { group ->
            item {
                ListItem(
                    headlineContent = { Text(group.title.ifBlank { "Untitled" }) },
                    supportingContent = { Text("${group.count} contacts") },
                    modifier = Modifier.clickable {
                        backStack.add(CommsDashboardRoute(initialTab = "contacts"))
                    },
                )
            }
        }
    }
}
