package com.telos.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    Column(
        modifier
            .width(320.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(TelosColors.menu)
            .border(1.dp, TelosColors.outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Notifications", color = TelosColors.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (TelosNotificationCenter.dnd.value) "Do Not Disturb: On" else "Do Not Disturb: Off",
                color = TelosColors.primary,
                modifier = Modifier.clickable { TelosNotificationCenter.dnd.value = !TelosNotificationCenter.dnd.value }.padding(8.dp),
            )
            Text(
                "Clear",
                color = TelosColors.secondary,
                modifier = Modifier.clickable { TelosNotificationCenter.notes.clear() }.padding(8.dp),
            )
        }
        if (TelosNotificationCenter.notes.isEmpty()) {
            Text("No notifications", color = TelosColors.onSurface.copy(0.6f), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TelosNotificationCenter.notes, key = { it.id }) { n ->
                    val card = RoundedCornerShape(10.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(card)
                            .background(TelosColors.surfaceContainer)
                            .border(1.dp, TelosColors.outline, card)
                            .padding(10.dp),
                    ) {
                        Text(n.title, color = TelosColors.onSurface)
                        Text(n.body, color = TelosColors.onSurface.copy(0.7f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
