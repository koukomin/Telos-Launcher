package de.mm20.launcher2.ui.comms

import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.search.TelosSearch
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object ContactGroupsRoute : NavKey

data class ContactGroup(val id: Long, val title: String, val count: Int)

private data class GroupMember(val contactId: Long, val name: String)

@Composable
fun ContactGroupsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val groups = produceState(emptyList<ContactGroup>()) {
        value = withContext(Dispatchers.IO) {
            val out = mutableListOf<ContactGroup>()
            runCatching {
                // SUMMARY_COUNT only exists in the summary view of the groups table
                context.contentResolver.query(
                    ContactsContract.Groups.CONTENT_SUMMARY_URI,
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
    var groupQuery by rememberSaveable { mutableStateOf("") }
    var expandedGroup by rememberSaveable { mutableStateOf(-1L) }
    val members = produceState(emptyList<GroupMember>(), expandedGroup) {
        value = if (expandedGroup < 0) emptyList() else withContext(Dispatchers.IO) {
            val out = linkedMapOf<Long, GroupMember>()
            runCatching {
                context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.DISPLAY_NAME,
                    ),
                    "${ContactsContract.Data.MIMETYPE}=? AND " +
                        "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID}=?",
                    arrayOf(
                        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                        expandedGroup.toString(),
                    ),
                    "${ContactsContract.Data.DISPLAY_NAME} COLLATE LOCALIZED ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        if (id !in out) out[id] = GroupMember(id, c.getString(1).orEmpty())
                    }
                }
            }
            out.values.toList()
        }
    }
    val shownGroups = TelosSearch.filter(groups.value, groupQuery) { listOf(it.title) }
    PreferenceScreen(title = { Text(stringResource(R.string.hc_groups)) }) {
        if (groups.value.isNotEmpty()) {
            item { TelosSearchBar(groupQuery, { groupQuery = it }, stringResource(R.string.tsp_search_groups)) }
            if (shownGroups.isEmpty() && groupQuery.isNotBlank()) item { SearchEmptyState(groupQuery) }
        }
        if (groups.value.isEmpty()) {
            item { Text(stringResource(R.string.hc_no_contact_groups_on_this_device), modifier = Modifier.padding(16.dp)) }
        }
        shownGroups.forEach { group ->
            item(key = "group-${group.id}") {
                ListItem(
                    headlineContent = {
                        Text(group.title.ifBlank { stringResource(R.string.au_phonea_untitled_group) })
                    },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.au_phonea_group_contacts, group.count, group.count))
                    },
                    modifier = Modifier.clickable {
                        expandedGroup = if (expandedGroup == group.id) -1L else group.id
                    },
                )
            }
            if (expandedGroup == group.id) {
                if (members.value.isEmpty()) {
                    item(key = "group-${group.id}-empty") {
                        Text(
                            stringResource(R.string.au_phonea_group_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        )
                    }
                }
                members.value.forEach { member ->
                    item(key = "group-${group.id}-member-${member.contactId}") {
                        ListItem(
                            headlineContent = { Text(member.name) },
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable { backStack.add(ContactDetailsRoute(contactId = member.contactId)) },
                        )
                    }
                }
            }
        }
    }
}
