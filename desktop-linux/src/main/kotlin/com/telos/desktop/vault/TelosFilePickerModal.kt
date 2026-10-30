package com.telos.desktop.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.ui.TelosColors
import java.io.File

@Composable
fun TelosFilePickerModal(
    start: File = File(System.getProperty("user.home")),
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var dir by remember { mutableStateOf(start) }
    val entries = remember(dir) {
        listOfNotNull(dir.parentFile) + (dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList())
    }
    Column(Modifier.width(480.dp).heightIn(max = 420.dp).background(TelosColors.surfaceContainer).padding(12.dp)) {
        Text(dir.absolutePath, color = TelosColors.secondary, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
            items(entries, key = { it.absolutePath }) { f ->
                Text(
                    (if (f.isDirectory) "[dir] " else "") + f.name,
                    color = TelosColors.onSurface,
                    modifier = Modifier.clickable {
                        if (f.isDirectory) dir = f else onPick(f)
                    }.padding(6.dp),
                )
            }
        }
        Button(onClick = { onPick(dir) }) { Text("Use this folder (multi-carrier pool)") }
        Button(onClick = onDismiss) { Text("Cancel") }
    }
}
