package com.telos.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.ui.TelosColors

data class TelosNote(val id: Long, val title: String, val body: String, val time: Long = System.currentTimeMillis())

object TelosNotificationCenter {
    val notes = mutableStateListOf<TelosNote>()
    var open = mutableStateOf(false)
    var dnd = mutableStateOf(false)
    private var next = 1L

    fun post(title: String, body: String) {
        if (dnd.value) return
        notes.add(0, TelosNote(next++, title, body))
        while (notes.size > 80) notes.removeLast()
    }
}

@Composable
fun NotificationSidebar(modifier: Modifier = Modifier) {
    if (!TelosNotificationCenter.open.value) return
    Column(
        modifier.width(320.dp).fillMaxHeight().background(TelosColors.surfaceContainer).padding(12.dp),
    ) {
        Text("Notifications", color = TelosColors.secondary)
        Text(
            if (TelosNotificationCenter.dnd.value) "Do Not Disturb: On" else "Do Not Disturb: Off",
            color = TelosColors.primary,
            modifier = Modifier.clickable { TelosNotificationCenter.dnd.value = !TelosNotificationCenter.dnd.value }.padding(8.dp),
        )
        LazyColumn {
            items(TelosNotificationCenter.notes, key = { it.id }) { n ->
                Column(Modifier.fillMaxWidth().padding(8.dp).background(TelosColors.card).padding(8.dp)) {
                    Text(n.title, color = TelosColors.onSurface)
                    Text(n.body, color = TelosColors.onSurface.copy(0.7f), fontSize = 12.sp)
                }
            }
        }
    }
}
